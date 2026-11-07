package com.hstudy.app.ui.parent

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hstudy.app.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.Calendar

/**
 * 家长每周学习周报接收器：每周定时汇总孩子本周学习数据并推送通知，
 * 让家长无需打开 App 也能掌握孩子的学习情况。
 */
class WeeklyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = AppGraph
                // 本周一 0 点作为统计起点
                val weekStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val focusMin = app.focusRepository.getDailyFocusMinutesSince(weekStart).first()
                    .sumOf { it.totalMinutes }
                val reviewCount = app.mistakeRepository.getReviewsSince(weekStart).first().size
                val records = app.pointRepository.getAllRecords().first()
                val weekRecords = records.filter { it.date >= weekStart }
                val checkins = weekRecords.count { it.type == com.hstudy.app.data.local.entity.PointRecordType.TASK_COMPLETE }
                val earned = weekRecords.filter { it.points > 0 }.sumOf { it.points }

                val message = buildString {
                    append("📊 孩子本周学习周报：")
                    append("打卡${checkins}次 · 专注${focusMin}分钟 · 复习${reviewCount}道错题 · 获得${earned}积分")
                    if (focusMin == 0 && reviewCount == 0 && checkins == 0) append("。本周学习较少，多鼓励孩子加油！")
                    else append("。继续保持，稳步进步！")
                }
                showNotification(context, message)
            } catch (e: Exception) {
                showNotification(context, "📊 孩子本周学习周报已生成，打开 App 查看详细报告。")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "每周学习周报",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每周定时推送孩子本周学习情况汇总给家长"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("HStudy 每周学习周报")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "hstudy_weekly_report"
        const val NOTIFICATION_ID = 2002
        private const val REQUEST_CODE = 2002

        /** 安排每周周报推送（每周日 hour 点触发） */
        fun schedule(context: Context, hour: Int = 20) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WeeklyReportReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
            }
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7,
                pendingIntent
            )
        }

        /** 取消每周周报推送 */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WeeklyReportReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

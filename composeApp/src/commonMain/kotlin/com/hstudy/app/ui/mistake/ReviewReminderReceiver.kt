package com.hstudy.app.ui.mistake

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
 * 错题复习每日提醒接收器：每天定时提醒孩子复习错题，
 * 让艾宾浩斯间隔复习真正落实到日常，避免“忘了复习”导致遗忘。
 * 智能优化：查询实际到期错题数，有到期才推送并显示数量，无到期不打扰。
 */
class ReviewReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 智能提醒：查询实际到期错题数，有到期才推送（避免无谓打扰）
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mistakes = AppGraph.mistakeRepository.getAllMistakes().first()
                val dueCount = mistakes.count { SpacedRepetition.isDueForReview(it) }
                if (dueCount > 0) {
                    showNotification(context, "📚 今日有 $dueCount 道错题待复习，趁热打铁及时复习，记得更牢～")
                }
            } catch (e: Exception) {
                // 兜底：查询失败时仍推送通用提醒
                showNotification(context, "📚 该复习错题啦！及时复习，记得更牢～")
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
                "错题复习提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "每天定时提醒孩子复习错题，巩固薄弱知识点"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("HStudy 错题复习提醒")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "hstudy_review_reminder"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE = 2001

        /** 安排每日复习提醒（每天 hour 点触发） */
        fun schedule(context: Context, hour: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReviewReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // 如果今天的时间已过，则从明天开始
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        /** 取消每日复习提醒 */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReviewReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

package com.hstudy.app.ui.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hstudy.app.R
import java.util.Calendar

/**
 * 日程提醒闹钟接收器：到点弹出本地通知提醒孩子
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日程提醒"
        val emoji = intent.getStringExtra(EXTRA_EMOJI) ?: "⏰"
        val hasTask = intent.getBooleanExtra(EXTRA_HAS_TASK, false)

        val message = if (hasTask) {
            "$emoji $title 时间到啦！完成后记得打卡哦～"
        } else {
            "$emoji $title 时间到啦！"
        }

        showNotification(context, title, message)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ 需要创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "每日日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到点提醒孩子完成日程安排"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 点击通知打开 App
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("HStudy 日程提醒")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "hstudy_schedule_reminder"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EMOJI = "extra_emoji"
        const val EXTRA_HAS_TASK = "extra_has_task"
    }
}

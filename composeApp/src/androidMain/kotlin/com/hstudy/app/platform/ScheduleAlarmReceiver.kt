package com.hstudy.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 日程提醒闹钟接收器：到点弹出本地通知提醒孩子，并重注册明天的闹钟。
 * 渠道/标题/文案与原 ui.schedule.ScheduleAlarmReceiver 一致。
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE) ?: "日程提醒"
        val message = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY)
            ?.takeIf { it.isNotBlank() } ?: "$title 时间到啦！"

        AndroidNotificationScheduler.showNotification(
            context, ReminderKind.SCHEDULE, message,
            System.currentTimeMillis().toInt()
        )
        // SCHEDULE 类每日重复：触发后重注册下一天
        AndroidNotificationScheduler.rescheduleNextFromReceiver(context, intent)
    }
}
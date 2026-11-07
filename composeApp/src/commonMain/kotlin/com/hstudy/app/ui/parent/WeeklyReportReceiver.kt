package com.hstudy.app.ui.parent

import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 家长每周学习周报提醒（跨平台版）：
 * 原实现是 BroadcastReceiver + AlarmManager(setRepeating 7 天，通知 id/请求码 2002)，
 * 触发时统计本周一 0 点起的打卡/专注/复习/积分并推送「📊 孩子本周学习周报：…」。
 *
 * 跨平台层把「注册/取消」交给 [Platform.notifications]：
 * - Android：androidMain 的 platform.WeeklyReportReceiver 到点执行原统计与推送逻辑（文案不变）；
 * - iOS：IosNotificationScheduler 用 UNCalendarNotificationTrigger(每周日) 触发本地通知。
 *
 * 约定与 AppGraph.bootstrap()/AndroidReminderBootstrap 一致：
 * id=9002、kind=[ReminderKind.WEEKLY_REPORT]、dayOfWeek=7（周日），保证开关与开机重注册去重。
 */
object WeeklyReportReceiver {

    /** 与平台侧渠道保持一致（仅作文档/取消用） */
    const val CHANNEL_ID = "hstudy_weekly_report"
    const val NOTIFICATION_ID = 2002

    /** WEEKLY_REPORT 类提醒的稳定 id */
    const val REMINDER_ID = 9002

    const val TITLE = "每周学习周报"
    private const val BODY = "本周学习周报已生成，来看看孩子的学习情况吧！"

    /** 安排每周周报推送（每周日 hour 点触发） */
    fun schedule(hour: Int = 20) {
        Platform.notifications.schedule(
            ScheduledReminder(
                id = REMINDER_ID,
                kind = ReminderKind.WEEKLY_REPORT,
                hour = hour.coerceIn(0, 23),
                minute = 0,
                title = TITLE,
                body = BODY,
                repeatDaily = false,
                dayOfWeek = 7
            )
        )
    }

    /** 取消每周周报推送 */
    fun cancel() {
        Platform.notifications.cancel(REMINDER_ID)
    }
}

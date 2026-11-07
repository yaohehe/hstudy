package com.hstudy.app.ui.schedule

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 日程提醒调度辅助（跨平台版）：
 * 原实现用 AlarmManager + PendingIntent(ScheduleAlarmReceiver)，跨平台层改为
 * [Platform.notifications] 注册 [ScheduledReminder]（Android 侧由 AlarmManager 实现，iOS 侧由 UNCalendarNotificationTrigger 实现）。
 *
 * 语义保持：
 * - 提醒 id 直接用 schedule.id（与 androidMain 的 AndroidReminderBootstrap 一致，便于取消/重注册去重）；
 * - 触发时刻取 schedule.time（"HH:mm"），每天重复；
 * - 通知文案与原 ScheduleAlarmReceiver 完全一致：
 *   "{emoji} {title} 时间到啦！" + 关联任务时追加 "完成后记得打卡哦～"。
 */
object ScheduleAlarmHelper {

    /** 为单条日程注册每日提醒 */
    fun schedule(schedule: DailyScheduleEntity) {
        val (hour, minute) = parseTime(schedule.time)
        Platform.notifications.schedule(
            ScheduledReminder(
                id = schedule.id.toInt(),
                kind = ReminderKind.SCHEDULE,
                hour = hour,
                minute = minute,
                title = schedule.title,
                body = notificationBody(schedule),
                repeatDaily = true
            )
        )
    }

    /** 取消单条日程的提醒 */
    fun cancel(schedule: DailyScheduleEntity) {
        Platform.notifications.cancel(schedule.id.toInt())
    }

    /** 重新注册所有已启用日程的提醒（App 启动 / 设备重启后调用） */
    suspend fun rescheduleAll() {
        val schedules = AppGraph.scheduleRepository.getEnabledSchedulesSync()
        Platform.notifications.rescheduleAll(
            schedules.map { schedule ->
                val (hour, minute) = parseTime(schedule.time)
                ScheduledReminder(
                    id = schedule.id.toInt(),
                    kind = ReminderKind.SCHEDULE,
                    hour = hour,
                    minute = minute,
                    title = schedule.title,
                    body = notificationBody(schedule),
                    repeatDaily = true
                )
            }
        )
    }

    /** 通知正文：沿用原 ScheduleAlarmReceiver 的文案规则 */
    fun notificationBody(schedule: DailyScheduleEntity): String = buildString {
        append("${schedule.emoji} ${schedule.title} 时间到啦！")
        if (schedule.linkedTaskId != null) append("完成后记得打卡哦～")
    }

    /** "HH:mm" -> (hour, minute)，非法时回落到 0:0 */
    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        return hour to minute
    }
}

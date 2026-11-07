package com.hstudy.app.platform

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import kotlinx.coroutines.flow.first

/**
 * 从数据库/偏好恢复全部提醒闹钟（App 启动、设备重启后调用）。
 * 对应原 HStudyApp.onCreate + ScheduleAlarmHelper.rescheduleAll + BootCompletedReceiver 的恢复逻辑。
 */
object AndroidReminderBootstrap {

    /** 重注册所有已启用的日程闹钟（SCHEDULE 类） */
    suspend fun rescheduleScheduleAlarms() {
        AppGraph.init()
        try {
            AppGraph.scheduleRepository.getEnabledSchedulesSync().forEach { schedule ->
                Platform.notifications.schedule(toReminder(schedule))
            }
        } catch (_: Exception) {
            // 数据库尚未就绪等异常场景静默跳过，下次启动会重试
        }
    }

    /** 全量恢复：日程 + 错题复习 + 每周周报（开机广播使用） */
    suspend fun rescheduleAll() {
        AppGraph.init()
        rescheduleScheduleAlarms()
        try {
            if (AppGraph.userPreferences.reviewReminderEnabled.first()) {
                val hour = AppGraph.userPreferences.reviewReminderHour.first()
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9001, kind = ReminderKind.REVIEW, hour = hour, minute = 0,
                        title = "错题复习提醒", body = "今天还有错题等着复习，快去看看吧！"
                    )
                )
            }
            if (AppGraph.userPreferences.weeklyReportEnabled.first()) {
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9002, kind = ReminderKind.WEEKLY_REPORT, hour = 20, minute = 0,
                        title = "每周学习周报", body = "本周学习周报已生成，来看看孩子的学习情况吧！",
                        dayOfWeek = 7
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun toReminder(schedule: DailyScheduleEntity): ScheduledReminder {
        val parts = schedule.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        // 与原 ScheduleAlarmReceiver 的通知文案保持一致
        val body = buildString {
            append("${schedule.emoji} ${schedule.title} 时间到啦！")
            if (schedule.linkedTaskId != null) append("完成后记得打卡哦～")
        }
        return ScheduledReminder(
            id = schedule.id.toInt(),
            kind = ReminderKind.SCHEDULE,
            hour = hour,
            minute = minute,
            title = schedule.title,
            body = body
        )
    }
}
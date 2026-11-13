package com.hstudy.app.platform

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.ScheduleAlarmStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.stringsFor
import kotlinx.coroutines.flow.first

/**
 * 从数据库/偏好恢复全部提醒闹钟（App 启动、设备重启后调用）。
 * 对应原 HStudyApp.onCreate + ScheduleAlarmHelper.rescheduleAll + BootCompletedReceiver 的恢复逻辑。
 *
 * 文案语言（W9 Task 2）：本对象全部是 suspend（开机广播走 goAsync + 协程），所以重注册时现取
 * `currentDisplayLanguage()`——与发出点同一口径（spec §8）。中文态与迁移前逐字节一致：
 * 9001/9002 的原硬编码字面量等于 `ReviewReminderStringsZh`/`WeeklyReportStringsZh` 的值；
 * 日程正文组合与 [com.hstudy.app.ui.schedule.ScheduleAlarmHelper.notificationBody] 同一规则
 * （`timeUp` + 可选 `bodySeparator`/`rememberCheckIn`，ZH 分隔符为空串）。
 * 取词异常退化为 ZH（与各 Receiver 的兜底口径一致），不因语言解析失败而跳过重注册。
 */
object AndroidReminderBootstrap {

    /** 重注册所有已启用的日程闹钟（SCHEDULE 类） */
    suspend fun rescheduleScheduleAlarms() {
        AppGraph.init()
        try {
            val lang = runCatching { currentDisplayLanguage() }.getOrDefault(DisplayLanguage.ZH)
            val strings = stringsFor(lang).scheduleAlarm
            AppGraph.scheduleRepository.getEnabledSchedulesSync().forEach { schedule ->
                Platform.notifications.schedule(toReminder(schedule, lang, strings))
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
            val lang = runCatching { currentDisplayLanguage() }.getOrDefault(DisplayLanguage.ZH)
            val review = stringsFor(lang).reviewReminder
            val weekly = stringsFor(lang).weeklyReport
            if (AppGraph.userPreferences.reviewReminderEnabled.first()) {
                val hour = AppGraph.userPreferences.reviewReminderHour.first()
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9001, kind = ReminderKind.REVIEW, hour = hour, minute = 0,
                        title = review.title, body = review.defaultBody
                    )
                )
            }
            if (AppGraph.userPreferences.weeklyReportEnabled.first()) {
                Platform.notifications.schedule(
                    ScheduledReminder(
                        id = 9002, kind = ReminderKind.WEEKLY_REPORT, hour = 20, minute = 0,
                        title = weekly.title, body = weekly.body,
                        dayOfWeek = 7
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 日程 → 排程提醒（标题/正文按 [lang] 取词）。
     *
     * 正文组合与 `ScheduleAlarmHelper.notificationBody` 保持一致：
     * `timeUp(emoji, localizedPresetText(title, lang))` + 关联任务时 `bodySeparator + rememberCheckIn`。
     * 原实现是硬编码中文（`"${schedule.emoji} ${schedule.title} 时间到啦！"` / `"完成后记得打卡哦～"`），
     * W9 起改走 [ScheduleAlarmStrings]；ZH 侧 `bodySeparator` 为空串，因此组合结果与迁移前逐字节相同。
     */
    private fun toReminder(
        schedule: DailyScheduleEntity,
        lang: DisplayLanguage,
        strings: ScheduleAlarmStrings,
    ): ScheduledReminder {
        val parts = schedule.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        // 日程标题：预设示例译出、家长自建原样（注册表只做显示映射，存储值不动）
        val title = localizedPresetText(schedule.title, lang)
        val body = buildString {
            append(strings.timeUp(schedule.emoji, title))
            if (schedule.linkedTaskId != null) {
                append(strings.bodySeparator)
                append(strings.rememberCheckIn)
            }
        }
        return ScheduledReminder(
            id = schedule.id.toInt(),
            kind = ReminderKind.SCHEDULE,
            hour = hour,
            minute = minute,
            title = title,
            body = body
        )
    }
}

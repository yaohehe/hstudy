package com.hstudy.app.ui.schedule

import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.ScheduleAlarmStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.i18n.stringsFor
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
 * - 通知文案在**发出时**按生效语言取词（spec §8）：`currentDisplayLanguage()` → [stringsFor] 的
 *   [ScheduleAlarmStrings]，日程标题走 [localizedPresetText]（预设示例译出、家长自建原样）；
 *   关联任务时正文追加 [ScheduleAlarmStrings.bodySeparator] + [ScheduleAlarmStrings.rememberCheckIn]
 *   （ZH 分隔符是空串、字节与迁移前一致；EN 是空格，避免两句粘连）。
 *
 * 本地化文案的**实际存活期**（W5 终审复核的诚实口径，别读成"切语言自动生效"）：
 * - 写入时机：只有管理页**保存/启停日程**才经本对象重注册；[rescheduleAll] 目前**没有生产调用点**。
 * - Android 的通知**标题**恒为 per-kind 渠道默认中文「HStudy 日程提醒」：`platform/ScheduleAlarmReceiver.kt`
 *   调 showNotification 不带 `titleOverride`，`AndroidNotificationScheduler` 回落到固定中文标题；
 *   本对象传入的 [ScheduledReminder.title] 只作额外信息/正文回退。
 * - 每次 App 启动 / 开机，`HStudyApplication` → `AndroidReminderBootstrap.rescheduleScheduleAlarms()`
 *   会用硬编码 canonical 中文正文（`"${schedule.emoji} ${schedule.title} 时间到啦！"` /
 *   `"完成后记得打卡哦～"`）重注册，EN 注册的正文**下次启动/开机就回退成中文**。
 * - 净效果：本地化副本只存活到下次启动/开机前；切换语言后要在管理页重新保存（或启停）该日程才刷新
 *   ——iOS 同样没有启动重注册来"治好"它（切语言只在重新保存/启停时刷新文案）。
 * - W9 遗留项（都需平台文件授权）：`ScheduleAlarmReceiver` 把 `EXTRA_TITLE` 当 `titleOverride`
 *   传给 showNotification；bootstrap 改走本地化辅助而非硬编码中文。
 *
 * 取词依赖 suspend 的 `currentDisplayLanguage()`，因此 [schedule] 也是 suspend：
 * 4 个调用点（ParentScheduleManageScreen 载入示例/启停/新建/编辑）均已在 `scope.launch {}` 内，
 * [rescheduleAll] 本就 suspend，调用方无需改动；纯取消的 [cancel] 不取词，保持同步。
 */
object ScheduleAlarmHelper {

    /** 为单条日程注册每日提醒（标题/正文按当前生效语言取词） */
    suspend fun schedule(schedule: DailyScheduleEntity) {
        val lang = currentDisplayLanguage()
        val strings = stringsFor(lang).scheduleAlarm
        val (hour, minute) = parseTime(schedule.time)
        Platform.notifications.schedule(
            ScheduledReminder(
                id = schedule.id.toInt(),
                kind = ReminderKind.SCHEDULE,
                hour = hour,
                minute = minute,
                title = localizedPresetText(schedule.title, lang),
                body = notificationBody(schedule, strings, lang),
                repeatDaily = true
            )
        )
    }

    /** 取消单条日程的提醒 */
    fun cancel(schedule: DailyScheduleEntity) {
        Platform.notifications.cancel(schedule.id.toInt())
    }

    /**
     * 重新注册所有已启用日程的提醒（当前**无生产调用点**：启动/开机由 androidMain 的
     * AndroidReminderBootstrap 自行重注册 canonical 中文；标题/正文按当前生效语言取词）
     */
    suspend fun rescheduleAll() {
        val lang = currentDisplayLanguage()
        val strings = stringsFor(lang).scheduleAlarm
        val schedules = AppGraph.scheduleRepository.getEnabledSchedulesSync()
        Platform.notifications.rescheduleAll(
            schedules.map { schedule ->
                val (hour, minute) = parseTime(schedule.time)
                ScheduledReminder(
                    id = schedule.id.toInt(),
                    kind = ReminderKind.SCHEDULE,
                    hour = hour,
                    minute = minute,
                    title = localizedPresetText(schedule.title, lang),
                    body = notificationBody(schedule, strings, lang),
                    repeatDaily = true
                )
            }
        )
    }

    /** 通知正文：标题部分按注册表本地化；正文按生效语言取词（语言分隔符见 [ScheduleAlarmStrings.bodySeparator]） */
    fun notificationBody(schedule: DailyScheduleEntity, strings: ScheduleAlarmStrings, lang: DisplayLanguage): String = buildString {
        append(strings.timeUp(schedule.emoji, localizedPresetText(schedule.title, lang)))
        if (schedule.linkedTaskId != null) {
            append(strings.bodySeparator)
            append(strings.rememberCheckIn)
        }
    }

    /** "HH:mm" -> (hour, minute)，非法时回落到 0:0 */
    private fun parseTime(time: String): Pair<Int, Int> {
        val parts = time.split(":")
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: 0).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59)
        return hour to minute
    }
}

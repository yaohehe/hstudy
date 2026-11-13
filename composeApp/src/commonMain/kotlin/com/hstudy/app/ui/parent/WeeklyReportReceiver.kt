package com.hstudy.app.ui.parent

import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 家长每周学习周报提醒（跨平台版）：
 * 原实现是 BroadcastReceiver + AlarmManager(setRepeating 7 天，通知 id/请求码 2002)，
 * 触发时统计本周一 0 点起的打卡/专注/复习/积分并推送「📊 孩子本周学习周报：…」。
 *
 * 跨平台层把「注册/取消」交给 [Platform.notifications]：
 * - Android：androidMain 的 platform.WeeklyReportReceiver 到点执行原统计逻辑，并在**触发时**
 *   按当前生效语言取词拼正文（[com.hstudy.app.i18n.WeeklyReportStrings] 的 summary +
 *   encourageLow/Good，查询异常时用 fallback）；
 * - iOS：IosNotificationScheduler 用 UNCalendarNotificationTrigger(每周日) 触发本地通知，
 *   [ScheduledReminder] 的 title/body 在**排程时**冻结一份快照，之后不追更。
 *
 * 本地化文案的**实际存活期**（诚实口径，别读成"切语言自动生效"）：
 * - iOS 的快照只存活到下次冷启动之前：`AppGraph.bootstrap()` 每次冷启动都会重注册 9002
 *   （W9 起该重注册按生效语言取词，不再是硬编码中文），切语言须重新启停周报开关才刷新。
 * - Android 的通知**正文**在触发时现拼、跟着生效语言走；**标题** EN 态用
 *   [com.hstudy.app.i18n.WeeklyReportStrings.title] 覆盖，ZH 态沿用渠道默认标题
 *   「HStudy 每周学习周报」（与 v3.1.0 逐字节一致，W1 终审裁决；W9 接线，见 androidMain 接收器 KDoc）。
 *   本对象传入的 [ScheduledReminder.title] 在 Android 侧只是排程元数据。
 *
 * 取词依赖 suspend 的 `currentDisplayLanguage()`，因此 [schedule] 也是 suspend：
 * 唯一调用点（ParentSettingsScreen 的周报开关）已在 `scope.launch {}` 内；纯取消的 [cancel]
 * 不取词，保持同步。
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

    /** 安排每周周报推送（每周日 hour 点触发；标题/正文按当前生效语言取词） */
    suspend fun schedule(hour: Int = 20) {
        val lang = currentDisplayLanguage()
        val strings = stringsFor(lang).weeklyReport
        Platform.notifications.schedule(
            ScheduledReminder(
                id = REMINDER_ID,
                kind = ReminderKind.WEEKLY_REPORT,
                hour = hour.coerceIn(0, 23),
                minute = 0,
                title = strings.title,
                body = strings.body,
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

package com.hstudy.app.ui.mistake

import com.hstudy.app.i18n.ReviewReminderStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 错题复习每日提醒（跨平台版）：注册/取消交给 [Platform.notifications]；
 * 文案按生效语言取词（spec §8），但两个平台的取词时机不同：
 * - iOS 在**排程时**把 title/body 冻结进本地通知请求，之后不再追更；
 * - Android 在**触发时**由 androidMain ReviewReminderReceiver 重新按当前语言取词
 *   （ZH 态标题沿用渠道默认，见该文件 KDoc）。
 *
 * 冷启动重注册（W9 起按生效语言取词）：`AppGraph.bootstrap()` 与开机
 * `AndroidReminderBootstrap.rescheduleAll()` 都用 `ReviewReminderStrings.title/defaultBody`
 * 按**当前**语言重注册 9001，不再用硬编码中文覆盖 iOS 排程时冻结的本地化快照。
 * 仍然成立的上限：切语言后要**关闭再开启**「每日复习提醒」（或重启）才刷新已排程副本。
 */
object ReviewReminderReceiver {

    const val CHANNEL_ID = "hstudy_review_reminder"
    const val NOTIFICATION_ID = 2001
    const val REMINDER_ID = 9001

    /** 当前生效语言下的通知文案（发出时调用）。 */
    suspend fun localizedCopy(): ReviewReminderStrings =
        stringsFor(currentDisplayLanguage()).reviewReminder

    /** 安排每日复习提醒（每天 hour 点触发；标题/正文按当前语言取词）。 */
    suspend fun schedule(hour: Int) {
        val copy = localizedCopy()
        Platform.notifications.schedule(
            ScheduledReminder(
                id = REMINDER_ID,
                kind = ReminderKind.REVIEW,
                hour = hour.coerceIn(0, 23),
                minute = 0,
                title = copy.title,
                body = copy.defaultBody,
                repeatDaily = true
            )
        )
    }

    /** 取消每日复习提醒 */
    fun cancel() {
        Platform.notifications.cancel(REMINDER_ID)
    }
}

package com.hstudy.app.ui.mistake

import com.hstudy.app.i18n.ReviewReminderStrings
import com.hstudy.app.i18n.currentDisplayLanguage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduledReminder

/**
 * 错题复习每日提醒（跨平台版）：注册/取消交给 [Platform.notifications]；
 * 文案在**发出时**按生效语言取（spec §8）——已排程通知不追更，下个每日重建周期生效。
 * 平台侧（androidMain ReviewReminderReceiver / iOS UNUserNotificationCenter）消费同一份本地化文案。
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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hstudy.app.platform

import com.hstudy.app.iosbridge.HStudyFillNotificationContent
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS 本地提醒：UNUserNotificationCenter + UNCalendarNotificationTrigger。
 * identifier 编码 kind（hstudy.reminder.<KIND>.<id>），cancelKind 按前缀匹配 pending/delivered。
 * dayOfWeek 约定 1=周一…7=周日；NSDateComponents.weekday 1=周日…7=周六。
 */
class IosNotificationScheduler : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    override fun schedule(reminder: ScheduledReminder) {
        val content = UNMutableNotificationContent()
        // title/body/sound 在 Kotlin 侧只暴露为只读属性，赋值由 ObjC inline 桥接完成（同 IosDeviceFeedback）。
        HStudyFillNotificationContent(
            content,
            reminder.title.ifBlank { defaultTitle(reminder.kind) },
            reminder.body,
            UNNotificationSound.defaultSound
        )

        val components = NSDateComponents().apply {
            hour = reminder.hour.coerceIn(0, 23).toLong()
            minute = reminder.minute.coerceIn(0, 59).toLong()
        }
        val repeats: Boolean
        if (reminder.kind == ReminderKind.WEEKLY_REPORT) {
            val iso = if (reminder.dayOfWeek in 1..7) reminder.dayOfWeek else 7
            components.weekday = (if (iso == 7) 1 else iso + 1).toLong()
            repeats = true
        } else {
            repeats = reminder.repeatDaily
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier(reminder.kind, reminder.id), content, trigger
        )
        center.addNotificationRequest(request) { _ -> }
    }

    override fun cancel(id: Int) {
        val ids = ReminderKind.entries.map { identifier(it, id) }
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    override fun cancelKind(kind: ReminderKind) {
        val prefix = kindPrefix(kind)
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.orEmpty().mapNotNull {
                (it as? UNNotificationRequest)?.identifier?.takeIf { id -> id.startsWith(prefix) }
            }
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
        }
        center.getDeliveredNotificationsWithCompletionHandler { notifications ->
            val ids = notifications.orEmpty().mapNotNull {
                (it as? UNNotification)?.request?.identifier?.takeIf { id -> id.startsWith(prefix) }
            }
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    override fun rescheduleAll(reminders: List<ScheduledReminder>) {
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.orEmpty().mapNotNull {
                (it as? UNNotificationRequest)?.identifier?.takeIf { id -> id.startsWith(ID_PREFIX) }
            }
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
        }
        reminders.forEach { schedule(it) }
    }

    private fun defaultTitle(kind: ReminderKind): String = when (kind) {
        ReminderKind.SCHEDULE -> "HStudy 日程提醒"
        ReminderKind.REVIEW -> "HStudy 错题复习提醒"
        ReminderKind.WEEKLY_REPORT -> "HStudy 每周学习周报"
    }

    companion object {
        private const val ID_PREFIX = "hstudy.reminder."
        private fun kindPrefix(kind: ReminderKind) = "$ID_PREFIX${kind.name}."
        private fun identifier(kind: ReminderKind, id: Int) = "${kindPrefix(kind)}$id"
    }
}
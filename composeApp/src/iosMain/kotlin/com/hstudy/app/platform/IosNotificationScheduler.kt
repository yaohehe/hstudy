@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hstudy.app.platform

import com.hstudy.app.iosbridge.HStudyFillNotificationContent
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLock
import platform.Foundation.NSObject
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionAlert
import platform.UserNotifications.UNNotificationPresentationOptionBadge
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol

/**
 * iOS 本地提醒：UNUserNotificationCenter + UNCalendarNotificationTrigger。
 * identifier 编码 kind、id 与持久化 generation（hstudy.reminder.<KIND>.<id>.g<N>[.<weekday>]），
 * 异步清理只删除旧 generation 和 legacy identifier，避免误删刚添加的新请求。
 * dayOfWeek 约定 1=周一…7=周日；NSDateComponents.weekday 1=周日…7=周六。
 */
class IosNotificationScheduler : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        installDelegate()
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    override fun schedule(reminder: ScheduledReminder) = withGenerationLock {
        val previousGeneration = generation(reminder.kind, reminder.id)
        val nextGeneration = previousGeneration + 1L
        defaults.setInteger(nextGeneration, generationKey(reminder.kind, reminder.id))

        // Freeze the cleanup set before adding replacement requests. Even if removal completes
        // later, it cannot contain any identifier from the new generation.
        val previousIds = ReminderNotificationIds.legacyIdentifiers(reminder.kind, reminder.id) +
            if (previousGeneration > 0L) {
                ReminderNotificationIds.identifiers(reminder.kind, reminder.id, previousGeneration)
            } else {
                emptyList()
            }
        center.removePendingNotificationRequestsWithIdentifiers(previousIds)

        notificationWeekdays(reminder).forEach { isoWeekday ->
            val content = UNMutableNotificationContent()
            // title/body/sound 在 Kotlin 侧只暴露为只读属性，赋值由 ObjC inline 桥接完成（同 IosDeviceFeedback）。
            HStudyFillNotificationContent(
                content,
                reminder.title.ifBlank { defaultTitle(reminder.kind) },
                reminder.body,
                UNNotificationSound.defaultSound
            )
            content.setUserInfo(mapOf(NotificationRoutes.EXTRA_ROUTE to NotificationRoutes.forKind(reminder.kind)))

            val components = NSDateComponents().apply {
                hour = reminder.hour.coerceIn(0, 23).toLong()
                minute = reminder.minute.coerceIn(0, 59).toLong()
                if (isoWeekday != null) {
                    // ISO: Monday=1..Sunday=7; NSDateComponents: Sunday=1..Saturday=7.
                    weekday = (if (isoWeekday == 7) 1 else isoWeekday + 1).toLong()
                }
            }
            val repeats = reminder.kind == ReminderKind.WEEKLY_REPORT || reminder.repeatDaily
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats)
            val request = UNNotificationRequest.requestWithIdentifier(
                ReminderNotificationIds.identifier(reminder.kind, reminder.id, nextGeneration, isoWeekday),
                content,
                trigger,
            )
            center.addNotificationRequest(request) { _ -> }
        }
    }

    override fun cancel(id: Int) {
        ReminderKind.entries.forEach { kind -> cancel(id, kind) }
    }

    override fun cancel(id: Int, kind: ReminderKind) = withGenerationLock {
        val currentGeneration = generation(kind, id)
        val ids = ReminderNotificationIds.legacyIdentifiers(kind, id) +
            if (currentGeneration > 0L) {
                ReminderNotificationIds.identifiers(kind, id, currentGeneration)
            } else {
                emptyList()
            }
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
        // Invalidate this generation before a later schedule can reserve its replacement.
        defaults.setInteger(currentGeneration + 1L, generationKey(kind, id))
    }

    override fun cancelKind(kind: ReminderKind) {
        val prefix = ReminderNotificationIds.PREFIX + kind.name + "."
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
            // Snapshot first; replacements are registered only after this exact removal set is issued.
            val ids = requests.orEmpty().mapNotNull {
                (it as? UNNotificationRequest)?.identifier?.takeIf { id -> id.startsWith(ID_PREFIX) }
            }
            center.removePendingNotificationRequestsWithIdentifiers(ids)
            reminders.forEach { schedule(it) }
        }
    }

    private fun defaultTitle(kind: ReminderKind): String = when (kind) {
        ReminderKind.SCHEDULE -> "HStudy 日程提醒"
        ReminderKind.REVIEW -> "HStudy 错题复习提醒"
        ReminderKind.WEEKLY_REPORT -> "HStudy 每周学习周报"
    }

    companion object {
        private const val ID_PREFIX = ReminderNotificationIds.PREFIX
        private val defaults = NSUserDefaults.standardUserDefaults
        private val generationLock = NSLock()
        private val sharedDelegate = IosNotificationDelegate()

        internal fun installDelegate() {
            UNUserNotificationCenter.currentNotificationCenter().delegate = sharedDelegate
        }

        private fun generationKey(kind: ReminderKind, id: Int): String =
            ReminderNotificationIds.base(kind, id.toLong()) + ".generation"

        private fun generation(kind: ReminderKind, id: Int): Long =
            defaults.integerForKey(generationKey(kind, id))

        private inline fun <T> withGenerationLock(block: () -> T): T {
            generationLock.lock()
            return try {
                block()
            } finally {
                generationLock.unlock()
            }
        }

        private fun notificationWeekdays(reminder: ScheduledReminder): List<Int?> = when {
            reminder.kind == ReminderKind.WEEKLY_REPORT -> listOf(
                reminder.dayOfWeek.takeIf { it in 1..7 } ?: 7
            )
            !reminder.repeatDaily -> listOf(null)
            else -> {
                val days = reminder.repeatDays.filter { it in 1..7 }.toSet()
                when {
                    days.isEmpty() || days.size == 7 -> listOf(null)
                    else -> days.sorted()
                }
            }
        }
    }
}

/** Keeps a strong reference through the scheduler companion; UNUserNotificationCenter.delegate is weak. */
internal class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionAlert or
                UNNotificationPresentationOptionBadge or
                UNNotificationPresentationOptionSound
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val route = didReceiveNotificationResponse.notification.request.content.userInfo[NotificationRoutes.EXTRA_ROUTE] as? String
        if (route != null && NotificationRoutes.isAllowed(route)) {
            Platform.pendingRoute = route
        }
        withCompletionHandler()
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        openSettingsForNotification: UNNotification?,
    ) = Unit
}

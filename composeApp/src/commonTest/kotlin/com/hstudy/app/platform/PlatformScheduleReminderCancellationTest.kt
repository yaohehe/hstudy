package com.hstudy.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformScheduleReminderCancellationTest {

    @Test
    fun cancelScheduleRemindersUsesOnlyTheScheduleKindForEveryId() {
        val previous = Platform.notifications
        val scheduler = RecordingNotificationScheduler()
        Platform.notifications = scheduler
        try {
            Platform.cancelScheduleReminders(listOf(41L, 42L))

            assertEquals(
                listOf(
                    41 to ReminderKind.SCHEDULE,
                    42 to ReminderKind.SCHEDULE,
                ),
                scheduler.cancellations,
            )
        } finally {
            Platform.notifications = previous
        }
    }

    @Test
    fun cancelScheduleRemindersRejectsIdsThatCannotBeScheduled() {
        val previous = Platform.notifications
        val scheduler = RecordingNotificationScheduler()
        Platform.notifications = scheduler
        try {
            assertFailsWith<IllegalArgumentException> {
                Platform.cancelScheduleReminders(listOf(Int.MAX_VALUE.toLong() + 1L))
            }

            assertTrue(scheduler.cancellations.isEmpty())
        } finally {
            Platform.notifications = previous
        }
    }

    private class RecordingNotificationScheduler : NotificationScheduler {
        val cancellations = mutableListOf<Pair<Int, ReminderKind>>()

        override fun schedule(reminder: ScheduledReminder) = Unit
        override fun cancel(id: Int) = Unit
        override fun cancel(id: Int, kind: ReminderKind) {
            cancellations += id to kind
        }
        override fun cancelKind(kind: ReminderKind) = Unit
        override fun rescheduleAll(reminders: List<ScheduledReminder>) = Unit
    }
}

package com.hstudy.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReminderNotificationIdsTest {

    @Test
    fun successiveGenerationsNeverShareRequestIdentifiers() {
        val first = ReminderNotificationIds.identifiers(ReminderKind.SCHEDULE, 9L, generation = 1L)
        val second = ReminderNotificationIds.identifiers(ReminderKind.SCHEDULE, 9L, generation = 2L)

        assertTrue(first.intersect(second.toSet()).isEmpty())
        assertNotEquals(first, second)
    }

    @Test
    fun replacementCleanupContainsOnlyLegacyAndPreviousGenerationIdentifiers() {
        val cleanup = ReminderNotificationIds.identifiers(ReminderKind.SCHEDULE, 9L, generation = 4L) +
            ReminderNotificationIds.legacyIdentifiers(ReminderKind.SCHEDULE, 9L)
        val replacement = ReminderNotificationIds.identifiers(ReminderKind.SCHEDULE, 9L, generation = 5L)

        assertTrue(replacement.intersect(cleanup.toSet()).isEmpty())
        assertTrue(cleanup.contains("hstudy.reminder.SCHEDULE.9"))
        assertTrue(cleanup.contains("hstudy.reminder.SCHEDULE.9.6"))
        assertTrue(cleanup.contains("hstudy.reminder.SCHEDULE.9.g4.6"))
        assertEquals(16, cleanup.size)
    }
}

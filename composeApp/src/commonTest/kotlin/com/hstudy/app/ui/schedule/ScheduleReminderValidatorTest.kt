package com.hstudy.app.ui.schedule

import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleReminderValidatorTest {

    @Test
    fun missingOrDisabledScheduleIsNotAnActiveReminder() {
        assertFalse(ScheduleReminderValidator.isActive(null, null))
        assertFalse(
            ScheduleReminderValidator.isActive(
                schedule(enabled = false),
                null,
            )
        )
    }

    @Test
    fun enabledUnlinkedScheduleIsActive() {
        assertTrue(ScheduleReminderValidator.isActive(schedule(linkedTaskId = null), null))
    }

    @Test
    fun enabledScheduleRequiresAnActiveLinkedTask() {
        assertFalse(ScheduleReminderValidator.isActive(schedule(linkedTaskId = 7L), null))
        assertFalse(
            ScheduleReminderValidator.isActive(
                schedule(linkedTaskId = 7L),
                task(active = false),
            )
        )
        assertTrue(
            ScheduleReminderValidator.isActive(
                schedule(linkedTaskId = 7L),
                task(active = true),
            )
        )
    }

    private fun schedule(enabled: Boolean = true, linkedTaskId: Long? = null) = DailyScheduleEntity(
        id = 5L,
        time = "16:00",
        title = "作业",
        linkedTaskId = linkedTaskId,
        isEnabled = enabled,
    )

    private fun task(active: Boolean) = TaskEntity(
        id = 7L,
        title = "作业",
        category = TaskCategory.BASIC,
        points = 1,
        isActive = active,
    )
}

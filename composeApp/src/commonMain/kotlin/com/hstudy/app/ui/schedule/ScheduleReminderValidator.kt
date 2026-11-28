package com.hstudy.app.ui.schedule

import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskEntity

/** Fail-closed validation for an alarm that may have outlived its schedule or linked task. */
internal object ScheduleReminderValidator {
    fun isActive(schedule: DailyScheduleEntity?, linkedTask: TaskEntity?): Boolean {
        if (schedule?.isEnabled != true) return false
        val taskId = schedule.linkedTaskId ?: return true
        return linkedTask?.id == taskId && linkedTask.isActive
    }
}

package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.TaskFrequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TaskFrequencyPolicyTest {

    @Test
    fun dailyTaskUsesCalendarDate() {
        val date = LocalDate(2026, 11, 25)
        assertEquals("2026-11-25", TaskFrequency.DAILY.periodKey(date))
        assertNotEquals(
            TaskFrequency.DAILY.periodKey(date),
            TaskFrequency.DAILY.periodKey(LocalDate(2026, 11, 26))
        )
    }

    @Test
    fun weeklyTaskUsesIsoWeekAcrossMonthBoundary() {
        assertEquals(
            TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 3, 30)),
            TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 5))
        )
        assertNotEquals(
            TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 5)),
            TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 4, 6))
        )
    }

    @Test
    fun weeklyTaskUsesIsoWeekAcrossYearBoundary() {
        assertEquals(
            TaskFrequency.WEEKLY.periodKey(LocalDate(2026, 12, 28)),
            TaskFrequency.WEEKLY.periodKey(LocalDate(2027, 1, 3))
        )
        assertNotEquals(
            TaskFrequency.WEEKLY.periodKey(LocalDate(2027, 1, 3)),
            TaskFrequency.WEEKLY.periodKey(LocalDate(2027, 1, 4))
        )
    }

    @Test
    fun monthlyTaskUsesCalendarMonthAcrossYearBoundary() {
        assertEquals(
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 1, 1)),
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 1, 31))
        )
        assertNotEquals(
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 1, 31)),
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 2, 1))
        )
        assertEquals(
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 12, 1)),
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 12, 31))
        )
        assertNotEquals(
            TaskFrequency.MONTHLY.periodKey(LocalDate(2026, 12, 31)),
            TaskFrequency.MONTHLY.periodKey(LocalDate(2027, 1, 1))
        )
    }

    @Test
    fun onceTaskUsesLifetimeKey() {
        assertEquals("ONCE", TaskFrequency.ONCE.periodKey(LocalDate(2026, 11, 25)))
        assertEquals(
            TaskFrequency.ONCE.periodKey(LocalDate(2020, 1, 1)),
            TaskFrequency.ONCE.periodKey(LocalDate(2030, 12, 31))
        )
    }
}

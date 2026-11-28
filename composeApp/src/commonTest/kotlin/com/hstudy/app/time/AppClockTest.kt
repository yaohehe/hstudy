package com.hstudy.app.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class AppClockTest {
    @Test fun fixedClockCrossesMidnightDeterministically() {
        val localMidnight = LocalDate(2026, 1, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val clock = FixedAppClock(localMidnight - 1)
        val before = clock.today()
        clock.millis += 1
        assertEquals("2025-12-31", before)
        assertEquals("2026-01-01", clock.today())
    }
}

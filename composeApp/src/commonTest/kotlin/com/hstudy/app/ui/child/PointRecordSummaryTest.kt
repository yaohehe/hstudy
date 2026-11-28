package com.hstudy.app.ui.child

import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class PointRecordSummaryTest {
    @Test
    fun summaryKeepsMidnightBucketsOrderAndPositiveTotal() {
        val today = LocalDate(2026, 11, 28)
        val zone = TimeZone.currentSystemDefault()
        val todayStart = today.atStartOfDayIn(zone).toEpochMilliseconds()
        val yesterdayStart = today.plus(-1, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val weekStart = today.plus(-6, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val now = today.atTime(12, 0).toInstant(zone).toEpochMilliseconds()
        val records = listOf(
            record(1, 5, todayStart),
            record(2, -2, yesterdayStart),
            record(3, 3, weekStart),
            record(4, 4, weekStart - 1),
            record(5, -1, now + 24L * 60L * 60L * 1000L),
        )

        val summary = summarizePointRecords(records, now)

        assertEquals(12, summary.totalEarned)
        assertEquals(listOf(0, 1, 2, 3), summary.grouped.keys.toList())
        assertEquals(listOf(1L, 5L), summary.grouped[0]?.map { it.id })
        assertEquals(listOf(2L), summary.grouped[1]?.map { it.id })
        assertEquals(listOf(3L), summary.grouped[2]?.map { it.id })
        assertEquals(listOf(4L), summary.grouped[3]?.map { it.id })
    }

    @Test
    fun summaryForEmptyRecordsIsEmpty() {
        val summary = summarizePointRecords(emptyList(), 0L)

        assertEquals(0, summary.totalEarned)
        assertEquals(emptyMap(), summary.grouped)
    }

    private fun record(id: Long, points: Int, date: Long) = PointRecordEntity(
        id = id,
        points = points,
        type = if (points >= 0) PointRecordType.BONUS else PointRecordType.PENALTY,
        date = date,
    )
}

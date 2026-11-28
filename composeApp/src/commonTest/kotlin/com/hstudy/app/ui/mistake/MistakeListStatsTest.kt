package com.hstudy.app.ui.mistake

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MistakeListStatsTest {
    @Test
    fun derivedStatsMatchDueWeakestSubjectAndSevenDayForecast() {
        val now = localMillis(28, 12)
        val day = 24L * 60L * 60L * 1000L
        val records = listOf(
            mistake(1, MistakeSubject.MATH, createdAt = now - 2 * day),
            mistake(2, MistakeSubject.MATH, createdAt = now - day),
            mistake(3, MistakeSubject.MATH, mastered = true, createdAt = now),
            mistake(4, MistakeSubject.CHINESE, createdAt = now, reviewCount = 1),
            mistake(5, MistakeSubject.CHINESE, createdAt = now - 2 * day, lastReviewAt = now - day, reviewCount = 1),
            mistake(6, MistakeSubject.CHINESE, mastered = true, createdAt = now),
            mistake(7, MistakeSubject.ENGLISH, createdAt = now + 10 * day),
        )

        val stats = deriveMistakeListStats(records, now)

        assertEquals(listOf(1L, 2L), stats.dueForReview.map { it.id })
        assertEquals(MistakeSubject.MATH, stats.weakestSubject)
        assertEquals(listOf(2, 1, 1, 0, 0, 0, 0), stats.reviewForecast)
    }

    @Test
    fun derivedStatsForEmptyRecordsAreEmpty() {
        val stats = deriveMistakeListStats(emptyList(), localMillis(28, 12))

        assertEquals(emptyList(), stats.dueForReview)
        assertNull(stats.weakestSubject)
        assertEquals(List(7) { 0 }, stats.reviewForecast)
    }

    private fun mistake(
        id: Long,
        subject: MistakeSubject,
        mastered: Boolean = false,
        createdAt: Long,
        lastReviewAt: Long = 0L,
        reviewCount: Int = 0,
    ) = MistakeEntity(
        id = id,
        subject = subject,
        title = "mistake-$id",
        isMastered = mastered,
        createdAt = createdAt,
        lastReviewAt = lastReviewAt,
        reviewCount = reviewCount,
    )

    private fun localMillis(day: Int, hour: Int): Long =
        LocalDate(2026, 11, day).atTime(hour, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

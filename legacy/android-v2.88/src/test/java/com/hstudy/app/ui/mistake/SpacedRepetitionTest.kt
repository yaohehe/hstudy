package com.hstudy.app.ui.mistake

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import org.junit.Assert.*
import org.junit.Test

class SpacedRepetitionTest {

    private fun makeMistake(
        reviewCount: Int = 0,
        isMastered: Boolean = false,
        createdAt: Long = 1000L,
        lastReviewAt: Long = 0L
    ) = MistakeEntity(
        id = 1,
        subject = MistakeSubject.MATH,
        title = "test",
        reviewCount = reviewCount,
        isMastered = isMastered,
        createdAt = createdAt,
        lastReviewAt = lastReviewAt
    )

    // ---- intervalDays ----

    @Test
    fun `intervalDays returns correct intervals for each review count`() {
        assertEquals(1, SpacedRepetition.intervalDays(0))
        assertEquals(2, SpacedRepetition.intervalDays(1))
        assertEquals(4, SpacedRepetition.intervalDays(2))
        assertEquals(7, SpacedRepetition.intervalDays(3))
        assertEquals(15, SpacedRepetition.intervalDays(4))
        assertEquals(30, SpacedRepetition.intervalDays(5))
    }

    @Test
    fun `intervalDays caps at 30 for large review counts`() {
        assertEquals(30, SpacedRepetition.intervalDays(6))
        assertEquals(30, SpacedRepetition.intervalDays(100))
    }

    // ---- nextReviewAt ----

    @Test
    fun `nextReviewAt uses createdAt when never reviewed`() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 1000L, lastReviewAt = 0L)
        // base = createdAt = 1000, interval = 1 day
        assertEquals(1000L + 1 * 86400000L, SpacedRepetition.nextReviewAt(mistake))
    }

    @Test
    fun `nextReviewAt uses lastReviewAt when reviewed`() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 1000L, lastReviewAt = 5000L)
        // base = lastReviewAt = 5000, interval = 4 days
        assertEquals(5000L + 4 * 86400000L, SpacedRepetition.nextReviewAt(mistake))
    }

    // ---- isDueForReview ----

    @Test
    fun `isDueForReview returns false when mastered`() {
        val mistake = makeMistake(reviewCount = 0, isMastered = true, createdAt = 0L)
        assertFalse(SpacedRepetition.isDueForReview(mistake, now = Long.MAX_VALUE))
    }

    @Test
    fun `isDueForReview returns true when past due`() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 1 day = 86400000
        assertTrue(SpacedRepetition.isDueForReview(mistake, now = 86400000L + 1))
    }

    @Test
    fun `isDueForReview returns false when not yet due`() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 86400000, now is before that
        assertFalse(SpacedRepetition.isDueForReview(mistake, now = 1000L))
    }

    // ---- proficiencyLabel ----

    @Test
    fun `proficiencyLabel returns correct labels`() {
        assertEquals("未复习", SpacedRepetition.proficiencyLabel(0))
        assertEquals("复习1次", SpacedRepetition.proficiencyLabel(1))
        assertEquals("复习2次", SpacedRepetition.proficiencyLabel(2))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(3))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(4))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(5))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(100))
    }

    // ---- nextReviewLabel ----

    @Test
    fun `nextReviewLabel returns mastered when mastered`() {
        val mistake = makeMistake(isMastered = true)
        assertEquals("已掌握", SpacedRepetition.nextReviewLabel(mistake))
    }

    @Test
    fun `nextReviewLabel returns today when due`() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 86400000, now >= that
        assertEquals("今天复习", SpacedRepetition.nextReviewLabel(mistake, now = 86400000L + 1))
    }

    @Test
    fun `nextReviewLabel returns tomorrow for 1 day away`() {
        val mistake = makeMistake(reviewCount = 1, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 2 days = 172800000
        // now = 172800000 - 1 => ceil(1/86400000.0) = 1 => "明天复习"
        val now = 172800000L - 1L
        assertEquals("明天复习", SpacedRepetition.nextReviewLabel(mistake, now = now))
    }

    @Test
    fun `nextReviewLabel returns days away for multi-day gap`() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 4 days = 345600000
        // now = 0 => daysUntil = ceil(345600000/86400000) = 4
        assertEquals("4天后复习", SpacedRepetition.nextReviewLabel(mistake, now = 0L))
    }
}

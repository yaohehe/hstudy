package com.hstudy.app.ui.mistake

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.i18n.DisplayLanguage
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ===== 以下测试逐条移植自 app/src/test/java/com/hstudy/app/ui/mistake/SpacedRepetitionTest.kt =====

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
    fun intervalDays_returns_correct_intervals_for_each_review_count() {
        assertEquals(1, SpacedRepetition.intervalDays(0))
        assertEquals(2, SpacedRepetition.intervalDays(1))
        assertEquals(4, SpacedRepetition.intervalDays(2))
        assertEquals(7, SpacedRepetition.intervalDays(3))
        assertEquals(15, SpacedRepetition.intervalDays(4))
        assertEquals(30, SpacedRepetition.intervalDays(5))
    }

    @Test
    fun intervalDays_caps_at_30_for_large_review_counts() {
        assertEquals(30, SpacedRepetition.intervalDays(6))
        assertEquals(30, SpacedRepetition.intervalDays(100))
    }

    // ---- nextReviewAt ----

    @Test
    fun nextReviewAt_uses_createdAt_when_never_reviewed() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 1000L, lastReviewAt = 0L)
        // base = createdAt = 1000, interval = 1 day
        assertEquals(1000L + 1 * 86400000L, SpacedRepetition.nextReviewAt(mistake))
    }

    @Test
    fun nextReviewAt_uses_lastReviewAt_when_reviewed() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 1000L, lastReviewAt = 5000L)
        // base = lastReviewAt = 5000, interval = 4 days
        assertEquals(5000L + 4 * 86400000L, SpacedRepetition.nextReviewAt(mistake))
    }

    // ---- isDueForReview ----

    @Test
    fun isDueForReview_returns_false_when_mastered() {
        val mistake = makeMistake(reviewCount = 0, isMastered = true, createdAt = 0L)
        assertFalse(SpacedRepetition.isDueForReview(mistake, now = Long.MAX_VALUE))
    }

    @Test
    fun isDueForReview_returns_true_when_past_due() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 1 day = 86400000
        assertTrue(SpacedRepetition.isDueForReview(mistake, now = 86400000L + 1))
    }

    @Test
    fun isDueForReview_returns_false_when_not_yet_due() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 86400000, now is before that
        assertFalse(SpacedRepetition.isDueForReview(mistake, now = 1000L))
    }

    // ---- proficiencyLabel ----

    @Test
    fun proficiencyLabel_returns_correct_labels() {
        assertEquals("未复习", SpacedRepetition.proficiencyLabel(0, DisplayLanguage.ZH))
        assertEquals("复习1次", SpacedRepetition.proficiencyLabel(1, DisplayLanguage.ZH))
        assertEquals("复习2次", SpacedRepetition.proficiencyLabel(2, DisplayLanguage.ZH))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.ZH))
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(4, DisplayLanguage.ZH))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(5, DisplayLanguage.ZH))
        assertEquals("已巩固", SpacedRepetition.proficiencyLabel(100, DisplayLanguage.ZH))
    }

    // ---- nextReviewLabel ----

    @Test
    fun nextReviewLabel_returns_mastered_when_mastered() {
        val mistake = makeMistake(isMastered = true)
        assertEquals("已掌握", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH))
    }

    @Test
    fun nextReviewLabel_returns_today_when_due() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 86400000, now >= that
        assertEquals("今天复习", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = 86400000L + 1))
    }

    @Test
    fun nextReviewLabel_returns_tomorrow_for_1_day_away() {
        val mistake = makeMistake(reviewCount = 1, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 2 days = 172800000
        // now = 172800000 - 1 => ceil(1/86400000.0) = 1 => "明天复习"
        val now = 172800000L - 1L
        assertEquals("明天复习", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = now))
    }

    @Test
    fun nextReviewLabel_returns_days_away_for_multi_day_gap() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 4 days = 345600000
        // now = 0 => daysUntil = ceil(345600000/86400000) = 4
        assertEquals("4天后复习", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = 0L))
    }

    // ---- 阶段 2 W1：标签随语言 ----

    @Test
    fun labelsSwitchWithLanguage() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 0L, lastReviewAt = 0L)
        assertEquals("较熟练", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.ZH))
        assertEquals("Familiar", SpacedRepetition.proficiencyLabel(3, DisplayLanguage.EN))
        // nextReviewAt = 4 天；now = 0 → daysUntil = 4
        assertEquals("4天后复习", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.ZH, now = 0L))
        assertEquals("Review in 4 days", SpacedRepetition.nextReviewLabel(mistake, DisplayLanguage.EN, now = 0L))
    }
}

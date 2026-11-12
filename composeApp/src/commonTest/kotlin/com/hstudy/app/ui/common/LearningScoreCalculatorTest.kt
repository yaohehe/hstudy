package com.hstudy.app.ui.common

import com.hstudy.app.i18n.DisplayLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ===== 以下测试逐条移植自 app/src/test/java/com/hstudy/app/ui/common/LearningScoreCalculatorTest.kt =====

class LearningScoreCalculatorTest {

    @Test
    fun zero_inputs_yields_zero_total_and_pending_grade() {
        val score = LearningScoreCalculator.calculate(0f, 0, 0, DisplayLanguage.ZH)
        assertEquals(0, score.total)
        assertEquals(0, score.checkinScore)
        assertEquals(0, score.focusScore)
        assertEquals(0, score.reviewScore)
        assertEquals("待开始", score.grade)
        assertEquals("💤", score.gradeEmoji)
    }

    @Test
    fun full_checkin_gives_40_points() {
        val score = LearningScoreCalculator.calculate(1f, 0, 0, DisplayLanguage.ZH)
        assertEquals(40, score.checkinScore)
    }

    @Test
    fun sixty_minutes_focus_gives_30_points() {
        val score = LearningScoreCalculator.calculate(0f, 60, 0, DisplayLanguage.ZH)
        assertEquals(30, score.focusScore)
    }

    @Test
    fun three_reviews_give_30_points() {
        val score = LearningScoreCalculator.calculate(0f, 0, 3, DisplayLanguage.ZH)
        assertEquals(30, score.reviewScore)
    }

    @Test
    fun perfect_score_yields_excellent_grade() {
        val score = LearningScoreCalculator.calculate(1f, 60, 3, DisplayLanguage.ZH)
        assertEquals(100, score.total)
        assertEquals("优秀", score.grade)
        assertEquals("🌟", score.gradeEmoji)
    }

    @Test
    fun score_60_to_84_yields_good_grade() {
        // checkin=0.8 => 32, focus=30 => 30, review=0 => 0 => total=62
        val score = LearningScoreCalculator.calculate(0.8f, 60, 0, DisplayLanguage.ZH)
        assertEquals(62, score.total)
        assertEquals("良好", score.grade)
        assertEquals("😊", score.gradeEmoji)
    }

    @Test
    fun score_40_to_59_yields_ok_grade() {
        // checkin=0.5 => 20, focus=20min => 10, review=1 => 10 => total=40
        val score = LearningScoreCalculator.calculate(0.5f, 20, 1, DisplayLanguage.ZH)
        assertEquals(40, score.total)
        assertEquals("不错", score.grade)
        assertEquals("👍", score.gradeEmoji)
    }

    @Test
    fun score_1_to_39_yields_starter_grade() {
        val score = LearningScoreCalculator.calculate(0.2f, 5, 0, DisplayLanguage.ZH)
        // checkin=8, focus=2, review=0 => total=10
        assertEquals(10, score.total)
        assertEquals("起步", score.grade)
        assertEquals("🌱", score.gradeEmoji)
    }

    @Test
    fun checkin_rate_is_clamped_to_0_1() {
        val overScore = LearningScoreCalculator.calculate(1.5f, 0, 0, DisplayLanguage.ZH)
        assertEquals(40, overScore.checkinScore)

        val underScore = LearningScoreCalculator.calculate(-0.5f, 0, 0, DisplayLanguage.ZH)
        assertEquals(0, underScore.checkinScore)
    }

    @Test
    fun focus_minutes_is_clamped_to_0_60() {
        val overScore = LearningScoreCalculator.calculate(0f, 120, 0, DisplayLanguage.ZH)
        assertEquals(30, overScore.focusScore)

        val underScore = LearningScoreCalculator.calculate(0f, -10, 0, DisplayLanguage.ZH)
        assertEquals(0, underScore.focusScore)
    }

    @Test
    fun review_count_is_clamped_to_0_3() {
        val overScore = LearningScoreCalculator.calculate(0f, 0, 10, DisplayLanguage.ZH)
        assertEquals(30, overScore.reviewScore)

        val underScore = LearningScoreCalculator.calculate(0f, 0, -5, DisplayLanguage.ZH)
        assertEquals(0, underScore.reviewScore)
    }

    @Test
    fun encouragement_matches_grade() {
        val excellent = LearningScoreCalculator.calculate(1f, 60, 3, DisplayLanguage.ZH)
        assertTrue(excellent.encouragement.contains("超棒"))

        val pending = LearningScoreCalculator.calculate(0f, 0, 0, DisplayLanguage.ZH)
        assertTrue(pending.encouragement.contains("新的一天"))
    }
}

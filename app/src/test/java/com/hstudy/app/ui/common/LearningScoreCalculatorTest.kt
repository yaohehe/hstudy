package com.hstudy.app.ui.common

import org.junit.Assert.*
import org.junit.Test

class LearningScoreCalculatorTest {

    @Test
    fun `zero inputs yields zero total and pending grade`() {
        val score = LearningScoreCalculator.calculate(0f, 0, 0)
        assertEquals(0, score.total)
        assertEquals(0, score.checkinScore)
        assertEquals(0, score.focusScore)
        assertEquals(0, score.reviewScore)
        assertEquals("待开始", score.grade)
        assertEquals("💤", score.gradeEmoji)
    }

    @Test
    fun `full checkin gives 40 points`() {
        val score = LearningScoreCalculator.calculate(1f, 0, 0)
        assertEquals(40, score.checkinScore)
    }

    @Test
    fun `60 minutes focus gives 30 points`() {
        val score = LearningScoreCalculator.calculate(0f, 60, 0)
        assertEquals(30, score.focusScore)
    }

    @Test
    fun `3 reviews give 30 points`() {
        val score = LearningScoreCalculator.calculate(0f, 0, 3)
        assertEquals(30, score.reviewScore)
    }

    @Test
    fun `perfect score yields excellent grade`() {
        val score = LearningScoreCalculator.calculate(1f, 60, 3)
        assertEquals(100, score.total)
        assertEquals("优秀", score.grade)
        assertEquals("🌟", score.gradeEmoji)
    }

    @Test
    fun `score 60-84 yields good grade`() {
        // checkin=0.8 => 32, focus=30 => 30, review=0 => 0 => total=62
        val score = LearningScoreCalculator.calculate(0.8f, 60, 0)
        assertEquals(62, score.total)
        assertEquals("良好", score.grade)
        assertEquals("😊", score.gradeEmoji)
    }

    @Test
    fun `score 40-59 yields ok grade`() {
        // checkin=0.5 => 20, focus=20min => 10, review=1 => 10 => total=40
        val score = LearningScoreCalculator.calculate(0.5f, 20, 1)
        assertEquals(40, score.total)
        assertEquals("不错", score.grade)
        assertEquals("👍", score.gradeEmoji)
    }

    @Test
    fun `score 1-39 yields starter grade`() {
        val score = LearningScoreCalculator.calculate(0.2f, 5, 0)
        // checkin=8, focus=2, review=0 => total=10
        assertEquals(10, score.total)
        assertEquals("起步", score.grade)
        assertEquals("🌱", score.gradeEmoji)
    }

    @Test
    fun `checkinRate is clamped to 0-1`() {
        val overScore = LearningScoreCalculator.calculate(1.5f, 0, 0)
        assertEquals(40, overScore.checkinScore)

        val underScore = LearningScoreCalculator.calculate(-0.5f, 0, 0)
        assertEquals(0, underScore.checkinScore)
    }

    @Test
    fun `focusMinutes is clamped to 0-60`() {
        val overScore = LearningScoreCalculator.calculate(0f, 120, 0)
        assertEquals(30, overScore.focusScore)

        val underScore = LearningScoreCalculator.calculate(0f, -10, 0)
        assertEquals(0, underScore.focusScore)
    }

    @Test
    fun `reviewCount is clamped to 0-3`() {
        val overScore = LearningScoreCalculator.calculate(0f, 0, 10)
        assertEquals(30, overScore.reviewScore)

        val underScore = LearningScoreCalculator.calculate(0f, 0, -5)
        assertEquals(0, underScore.reviewScore)
    }

    @Test
    fun `encouragement matches grade`() {
        val excellent = LearningScoreCalculator.calculate(1f, 60, 3)
        assertTrue(excellent.encouragement.contains("超棒"))

        val pending = LearningScoreCalculator.calculate(0f, 0, 0)
        assertTrue(pending.encouragement.contains("新的一天"))
    }
}

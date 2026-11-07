package com.hstudy.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =========================================================================================
// 临时测试夹具（fixture）：LearningScoreCalculator 的源码目前仍只在 app/src/main（ui/ 尚未迁移）。
// 按迁移约束本任务不得改动 composeApp ui/ 目录，因此把纯逻辑评分器原样复制到 commonTest，
// 保证回归测试立即可运行。待 ui/ 迁移完成后：删除本 fixture 段与 DailyLearningScore，
// 并删除本包内与之重复的类，测试即可直接针对 commonMain 生产代码运行。
// 原文件：app/src/main/java/com/hstudy/app/ui/common/LearningScore.kt（纯 Kotlin，无平台依赖）。
// =========================================================================================

/**
 * 每日学习表现评分结果
 */
data class DailyLearningScore(
    val total: Int,            // 综合得分 0-100
    val checkinScore: Int,     // 打卡维度得分（满分40）
    val focusScore: Int,       // 专注维度得分（满分30）
    val reviewScore: Int,      // 复习维度得分（满分30）
    val checkinRate: Float,    // 打卡完成率
    val focusMinutes: Int,     // 今日专注分钟
    val reviewCount: Int,      // 今日复习次数
    val grade: String,         // 等级评语
    val gradeEmoji: String,    // 等级图标
    val encouragement: String  // 鼓励语
)

/**
 * 每日学习表现评分计算器（与 app 原版逻辑逐行一致）：
 * - 打卡完成度 40 分；专注投入 30 分（60 分钟满分）；错题复习 30 分（3 道满分）
 */
object LearningScoreCalculator {

    fun calculate(checkinRate: Float, focusMinutes: Int, reviewCount: Int): DailyLearningScore {
        val checkinScore = (checkinRate.coerceIn(0f, 1f) * 40).toInt()
        val focusScore = ((focusMinutes / 60f).coerceIn(0f, 1f) * 30).toInt()
        val reviewScore = ((reviewCount / 3f).coerceIn(0f, 1f) * 30).toInt()
        val total = checkinScore + focusScore + reviewScore

        val (grade, gradeEmoji) = when {
            total >= 85 -> "优秀" to "🌟"
            total >= 60 -> "良好" to "😊"
            total >= 40 -> "不错" to "👍"
            total > 0 -> "起步" to "🌱"
            else -> "待开始" to "💤"
        }

        val encouragement = when {
            total >= 85 -> "今天学习超棒！全面发展，继续保持！"
            total >= 60 -> "今天表现很好！再坚持一下就完美了！"
            total >= 40 -> "不错的开始！专注和复习还能再加把劲！"
            total > 0 -> "已经迈出一大步！继续完成更多任务吧！"
            else -> "新的一天，从完成第一个任务开始吧！"
        }

        return DailyLearningScore(
            total = total,
            checkinScore = checkinScore,
            focusScore = focusScore,
            reviewScore = reviewScore,
            checkinRate = checkinRate,
            focusMinutes = focusMinutes,
            reviewCount = reviewCount,
            grade = grade,
            gradeEmoji = gradeEmoji,
            encouragement = encouragement
        )
    }
}

// ===== 以下测试逐条移植自 app/src/test/java/com/hstudy/app/ui/common/LearningScoreCalculatorTest.kt =====

class LearningScoreCalculatorTest {

    @Test
    fun zero_inputs_yields_zero_total_and_pending_grade() {
        val score = LearningScoreCalculator.calculate(0f, 0, 0)
        assertEquals(0, score.total)
        assertEquals(0, score.checkinScore)
        assertEquals(0, score.focusScore)
        assertEquals(0, score.reviewScore)
        assertEquals("待开始", score.grade)
        assertEquals("💤", score.gradeEmoji)
    }

    @Test
    fun full_checkin_gives_40_points() {
        val score = LearningScoreCalculator.calculate(1f, 0, 0)
        assertEquals(40, score.checkinScore)
    }

    @Test
    fun sixty_minutes_focus_gives_30_points() {
        val score = LearningScoreCalculator.calculate(0f, 60, 0)
        assertEquals(30, score.focusScore)
    }

    @Test
    fun three_reviews_give_30_points() {
        val score = LearningScoreCalculator.calculate(0f, 0, 3)
        assertEquals(30, score.reviewScore)
    }

    @Test
    fun perfect_score_yields_excellent_grade() {
        val score = LearningScoreCalculator.calculate(1f, 60, 3)
        assertEquals(100, score.total)
        assertEquals("优秀", score.grade)
        assertEquals("🌟", score.gradeEmoji)
    }

    @Test
    fun score_60_to_84_yields_good_grade() {
        // checkin=0.8 => 32, focus=30 => 30, review=0 => 0 => total=62
        val score = LearningScoreCalculator.calculate(0.8f, 60, 0)
        assertEquals(62, score.total)
        assertEquals("良好", score.grade)
        assertEquals("😊", score.gradeEmoji)
    }

    @Test
    fun score_40_to_59_yields_ok_grade() {
        // checkin=0.5 => 20, focus=20min => 10, review=1 => 10 => total=40
        val score = LearningScoreCalculator.calculate(0.5f, 20, 1)
        assertEquals(40, score.total)
        assertEquals("不错", score.grade)
        assertEquals("👍", score.gradeEmoji)
    }

    @Test
    fun score_1_to_39_yields_starter_grade() {
        val score = LearningScoreCalculator.calculate(0.2f, 5, 0)
        // checkin=8, focus=2, review=0 => total=10
        assertEquals(10, score.total)
        assertEquals("起步", score.grade)
        assertEquals("🌱", score.gradeEmoji)
    }

    @Test
    fun checkin_rate_is_clamped_to_0_1() {
        val overScore = LearningScoreCalculator.calculate(1.5f, 0, 0)
        assertEquals(40, overScore.checkinScore)

        val underScore = LearningScoreCalculator.calculate(-0.5f, 0, 0)
        assertEquals(0, underScore.checkinScore)
    }

    @Test
    fun focus_minutes_is_clamped_to_0_60() {
        val overScore = LearningScoreCalculator.calculate(0f, 120, 0)
        assertEquals(30, overScore.focusScore)

        val underScore = LearningScoreCalculator.calculate(0f, -10, 0)
        assertEquals(0, underScore.focusScore)
    }

    @Test
    fun review_count_is_clamped_to_0_3() {
        val overScore = LearningScoreCalculator.calculate(0f, 0, 10)
        assertEquals(30, overScore.reviewScore)

        val underScore = LearningScoreCalculator.calculate(0f, 0, -5)
        assertEquals(0, underScore.reviewScore)
    }

    @Test
    fun encouragement_matches_grade() {
        val excellent = LearningScoreCalculator.calculate(1f, 60, 3)
        assertTrue(excellent.encouragement.contains("超棒"))

        val pending = LearningScoreCalculator.calculate(0f, 0, 0)
        assertTrue(pending.encouragement.contains("新的一天"))
    }
}

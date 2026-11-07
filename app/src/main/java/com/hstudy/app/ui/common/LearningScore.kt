package com.hstudy.app.ui.common

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
 * 每日学习表现评分计算器：把孩子每天的打卡、专注、错题复习整合为综合评分，
 * 让孩子对"今日学习质量"有清晰反馈，促进自我调节与学习表现提升。
 *
 * 评分维度（满分100）：
 * - 打卡完成度 40 分：今日完成任务占比
 * - 专注投入 30 分：今日专注时长（60分钟为满分基准）
 * - 错题复习 30 分：今日复习错题数（3道为满分基准）
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

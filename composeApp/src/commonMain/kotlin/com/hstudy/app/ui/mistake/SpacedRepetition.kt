package com.hstudy.app.ui.mistake

import com.hstudy.app.data.local.entity.MistakeEntity

/**
 * 艾宾浩斯间隔复习调度器
 *
 * 依据遗忘曲线，错题在复习后的不同时间点重复复习，记忆保持率最高：
 * 第1次复习后隔1天、第2次后隔2天、第3次后隔4天、第4次后隔7天、第5次后隔15天、之后隔30天。
 */
object SpacedRepetition {

    /** 各次复习后的间隔天数（索引=已复习次数） */
    private val intervals = listOf(1, 2, 4, 7, 15, 30)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 根据已复习次数获取下次复习间隔天数 */
    fun intervalDays(reviewCount: Int): Int {
        return if (reviewCount < intervals.size) intervals[reviewCount] else intervals.last()
    }

    /** 计算下次应复习的时间戳 */
    fun nextReviewAt(mistake: MistakeEntity): Long {
        // 从未复习过：以创建时间为基准，隔1天开始第一次复习
        val base = if (mistake.lastReviewAt > 0) mistake.lastReviewAt else mistake.createdAt
        return base + intervalDays(mistake.reviewCount) * DAY_MS
    }

    /** 判断错题今天是否到了该复习的时间（未掌握且已到复习时间点） */
    fun isDueForReview(mistake: MistakeEntity, now: Long = com.hstudy.app.ui.dt.nowMillis()): Boolean {
        if (mistake.isMastered) return false
        return now >= nextReviewAt(mistake)
    }

    /** 复习熟练度描述（复习次数越多越熟练） */
    fun proficiencyLabel(reviewCount: Int): String = when {
        reviewCount == 0 -> "未复习"
        reviewCount < 3 -> "复习${reviewCount}次"
        reviewCount < 5 -> "较熟练"
        else -> "已巩固"
    }

    /** 下次复习的友好描述（如“今天复习”/“明天复习”/“3天后复习”，让间隔复习计划透明可见） */
    fun nextReviewLabel(mistake: MistakeEntity, now: Long = com.hstudy.app.ui.dt.nowMillis()): String {
        if (mistake.isMastered) return "已掌握"
        val nextAt = nextReviewAt(mistake)
        if (now >= nextAt) return "今天复习"
        val daysUntil = kotlin.math.ceil((nextAt - now).toDouble() / DAY_MS).toInt()
        return if (daysUntil <= 1) "明天复习" else "${daysUntil}天后复习"
    }
}

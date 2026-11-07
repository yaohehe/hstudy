package com.hstudy.app.ui.mistake

import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =========================================================================================
// 临时测试夹具（fixture）：SpacedRepetition 的源码目前仍只在 app/src/main（ui/ 尚未迁移，
// 本任务不得改动 composeApp ui/ 目录），因此把纯逻辑调度器复制到 commonTest，逻辑与原版
// 逐行一致；唯一差异：默认参数 System.currentTimeMillis() 改为跨平台等价的
// Clock.System.now().toEpochMilliseconds()（行为完全相同）。
// 待 ui/ 迁移完成后删除本 fixture 段，测试直接跑 commonMain 生产代码。
// 原文件：app/src/main/java/com/hstudy/app/ui/mistake/SpacedRepetition.kt
// =========================================================================================

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
    fun isDueForReview(mistake: MistakeEntity, now: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
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
    fun nextReviewLabel(mistake: MistakeEntity, now: Long = Clock.System.now().toEpochMilliseconds()): String {
        if (mistake.isMastered) return "已掌握"
        val nextAt = nextReviewAt(mistake)
        if (now >= nextAt) return "今天复习"
        val daysUntil = kotlin.math.ceil((nextAt - now).toDouble() / DAY_MS).toInt()
        return if (daysUntil <= 1) "明天复习" else "${daysUntil}天后复习"
    }
}

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
    fun nextReviewLabel_returns_mastered_when_mastered() {
        val mistake = makeMistake(isMastered = true)
        assertEquals("已掌握", SpacedRepetition.nextReviewLabel(mistake))
    }

    @Test
    fun nextReviewLabel_returns_today_when_due() {
        val mistake = makeMistake(reviewCount = 0, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 86400000, now >= that
        assertEquals("今天复习", SpacedRepetition.nextReviewLabel(mistake, now = 86400000L + 1))
    }

    @Test
    fun nextReviewLabel_returns_tomorrow_for_1_day_away() {
        val mistake = makeMistake(reviewCount = 1, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 2 days = 172800000
        // now = 172800000 - 1 => ceil(1/86400000.0) = 1 => "明天复习"
        val now = 172800000L - 1L
        assertEquals("明天复习", SpacedRepetition.nextReviewLabel(mistake, now = now))
    }

    @Test
    fun nextReviewLabel_returns_days_away_for_multi_day_gap() {
        val mistake = makeMistake(reviewCount = 2, createdAt = 0L, lastReviewAt = 0L)
        // nextReviewAt = 0 + 4 days = 345600000
        // now = 0 => daysUntil = ceil(345600000/86400000) = 4
        assertEquals("4天后复习", SpacedRepetition.nextReviewLabel(mistake, now = 0L))
    }
}

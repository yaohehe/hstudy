package com.hstudy.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

// =========================================================================================
// 临时测试夹具（fixture）：LevelSystem 的源码目前仍只在 app/src/main（ui/ 尚未迁移，本任务
// 不得改动 composeApp ui/ 目录）。此处仅复制被测试的纯逻辑部分（不含 LevelBadge Composable），
// 逻辑与原版逐行一致。待 ui/ 迁移完成后删除本 fixture 段与 LevelInfo，测试直接跑生产代码。
// 原文件：app/src/main/java/com/hstudy/app/ui/common/LevelSystem.kt
// =========================================================================================

data class LevelInfo(
    val level: Int,
    val title: String,
    val emoji: String,
    val requiredPoints: Int,
    val nextLevelPoints: Int,
    val progress: Float // 0.0 ~ 1.0
)

object LevelSystem {
    private val levels = listOf(
        Triple(1, "学习新手", "🌱") to 0,
        Triple(2, "勤奋小学徒", "🌿") to 100,
        Triple(3, "知识探索者", "🌳") to 300,
        Triple(4, "学习达人", "🌲") to 600,
        Triple(5, "学霸少年", "🎋") to 1000,
        Triple(6, "知识王者", "🌸") to 1500,
        Triple(7, "学习之星", "🍎") to 2500,
        Triple(8, "超级学霸", "🌴") to 4000,
        Triple(9, "传奇学者", "🏔️") to 6000,
        Triple(10, "学神", "🌟") to 10000
    )

    fun getLevelInfo(totalEarnedPoints: Int): LevelInfo {
        var currentIdx = 0
        for (i in levels.indices) {
            if (totalEarnedPoints >= levels[i].second) {
                currentIdx = i
            } else break
        }

        val current = levels[currentIdx]
        val next = if (currentIdx < levels.lastIndex) levels[currentIdx + 1] else null

        val progress = if (next != null) {
            val range = next.second - current.second
            val earned = totalEarnedPoints - current.second
            earned.toFloat() / range
        } else 1f

        return LevelInfo(
            level = current.first.first,
            title = current.first.second,
            emoji = current.first.third,
            requiredPoints = current.second,
            nextLevelPoints = next?.second ?: current.second,
            progress = progress.coerceIn(0f, 1f)
        )
    }
}

// ===== 以下测试逐条移植自 app/src/test/java/com/hstudy/app/ui/common/LevelSystemTest.kt =====

class LevelSystemTest {

    @Test
    fun zero_points_yields_level_1() {
        val info = LevelSystem.getLevelInfo(0)
        assertEquals(1, info.level)
        assertEquals("学习新手", info.title)
        assertEquals("🌱", info.emoji)
        assertEquals(0, info.requiredPoints)
        assertEquals(100, info.nextLevelPoints)
        assertEquals(0f, info.progress, 0.01f)
    }

    @Test
    fun mid_level_progress_is_calculated_correctly() {
        // Level 2 starts at 100, Level 3 at 300 => range=200
        val info = LevelSystem.getLevelInfo(200)
        assertEquals(2, info.level)
        assertEquals("勤奋小学徒", info.title)
        assertEquals(0.5f, info.progress, 0.01f)
    }

    @Test
    fun exactly_at_level_threshold() {
        val info = LevelSystem.getLevelInfo(300)
        assertEquals(3, info.level)
        assertEquals("知识探索者", info.title)
        assertEquals(0f, info.progress, 0.01f)
    }

    @Test
    fun just_below_threshold_stays_at_previous_level() {
        val info = LevelSystem.getLevelInfo(299)
        assertEquals(2, info.level)
        // progress = (299-100)/(300-100) = 199/200 = 0.995
        assertEquals(0.995f, info.progress, 0.01f)
    }

    @Test
    fun max_level_at_10000_points() {
        val info = LevelSystem.getLevelInfo(10000)
        assertEquals(10, info.level)
        assertEquals("学神", info.title)
        assertEquals("🌟", info.emoji)
        assertEquals(1f, info.progress, 0.01f)
    }

    @Test
    fun points_above_max_still_level_10() {
        val info = LevelSystem.getLevelInfo(99999)
        assertEquals(10, info.level)
        assertEquals(1f, info.progress, 0.01f)
    }

    @Test
    fun all_levels_are_reachable() {
        val thresholds = listOf(0, 100, 300, 600, 1000, 1500, 2500, 4000, 6000, 10000)
        for (i in thresholds.indices) {
            val info = LevelSystem.getLevelInfo(thresholds[i])
            assertEquals(i + 1, info.level)
        }
    }

    @Test
    fun nextLevelPoints_equals_requiredPoints_at_max_level() {
        val info = LevelSystem.getLevelInfo(10000)
        assertEquals(info.requiredPoints, info.nextLevelPoints)
    }
}

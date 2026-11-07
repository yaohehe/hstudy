package com.hstudy.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

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

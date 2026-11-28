package com.hstudy.app.ui.parent

import com.hstudy.app.data.local.entity.RewardEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParentRewardEditScreenTest {

    @Test
    fun editingKeyedRewardPreservesPresetIdentityAndHistoricalMetadata() {
        val existing = RewardEntity(
            id = 42,
            name = "看一集动画片",
            description = "看一集喜欢的动画片",
            pointsCost = 80,
            iconEmoji = "📺",
            category = "娱乐",
            isActive = false,
            sortOrder = 7,
            createdAt = 123,
            presetKey = "看一集动画片"
        )

        val saved = buildRewardForSave(
            existing = existing,
            rewardId = existing.id,
            name = "家长修改后的名称",
            description = "家长修改后的说明",
            pointsCost = "90",
            iconEmoji = "🎁",
            category = "特权"
        )

        assertEquals(
            existing.copy(
                name = "家长修改后的名称",
                description = "家长修改后的说明",
                pointsCost = 90,
                iconEmoji = "🎁",
                category = "特权"
            ),
            saved
        )
        assertEquals("看一集动画片", saved.presetKey)
    }

    @Test
    fun creatingCustomRewardDoesNotInventPresetIdentity() {
        val saved = buildRewardForSave(
            existing = null,
            rewardId = 0,
            name = "自定义奖励",
            description = "自定义说明",
            pointsCost = "120",
            iconEmoji = "🎲",
            category = "娱乐"
        )

        assertEquals(0, saved.id)
        assertEquals("自定义奖励", saved.name)
        assertEquals(120, saved.pointsCost)
        assertNull(saved.presetKey)
    }
}

package com.hstudy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val pointsCost: Int,
    val iconEmoji: String = "🎁",
    @ColumnInfo(defaultValue = "其他")
    val category: String = "其他",        // 奖励分类：娱乐/美食/出行/特权/实物/社交
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reward_exchanges")
data class RewardExchangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,
    val pointsSpent: Int,
    val exchangedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

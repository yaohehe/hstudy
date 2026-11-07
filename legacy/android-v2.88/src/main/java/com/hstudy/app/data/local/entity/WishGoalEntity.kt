package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 心愿存钱罐目标：孩子设定大目标，逐步存入积分，攒够后兑换
 */
@Entity(tableName = "wish_goals")
data class WishGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                      // 心愿名称（如"一台新平板"）
    val description: String = "",          // 心愿描述
    val targetPoints: Int,                 // 目标积分
    val savedPoints: Int = 0,              // 已存入积分
    val iconEmoji: String = "🎯",          // 图标
    val isActive: Boolean = true,          // 是否进行中
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null          // 达成时间
)

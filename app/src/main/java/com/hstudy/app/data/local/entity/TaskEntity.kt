package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 任务类别
 */
enum class TaskCategory {
    BASIC,       // 基础学习任务
    ENHANCEMENT, // 提升学习任务
    HABIT        // 习惯与态度奖励
}

/**
 * 任务频率
 */
enum class TaskFrequency {
    DAILY,    // 每日
    WEEKLY,   // 每周
    MONTHLY,  // 每月
    ONCE      // 一次性
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: TaskCategory,
    val points: Int,                    // 基础积分
    val bonusPoints: Int = 0,           // 奖励积分（如全对额外加分）
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val difficulty: Int = 1,            // 难度等级：1=普通⭐ 2=进阶⭐⭐ 3=挑战⭐⭐⭐
    val isMandatory: Boolean = false,   // 每日必须：未完成时自动扣减相应积分（仅对每日任务生效）
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 难度星级文本（1=⭐ 2=⭐⭐ 3=⭐⭐⭐） */
fun TaskEntity.difficultyStars(): String = when (difficulty) {
    2 -> "⭐⭐"
    3 -> "⭐⭐⭐"
    else -> "⭐"
}

/** 难度名称 */
fun TaskEntity.difficultyName(): String = when (difficulty) {
    2 -> "进阶"
    3 -> "挑战"
    else -> "普通"
}

/** 根据难度计算实际可得积分（进阶×1.2，挑战×1.5） */
fun TaskEntity.effectivePoints(): Int = when (difficulty) {
    2 -> (points * 1.2).toInt()
    3 -> (points * 1.5).toInt()
    else -> points
}

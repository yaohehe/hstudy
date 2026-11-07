package com.hstudy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_challenges")
data class HabitChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startDate: String,                // yyyy-MM-dd
    val totalDays: Int = 21,              // 挑战天数（默认21天）
    val completedDays: Int = 0,           // 已完成天数
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,     // 挑战是否完成
    val emoji: String = "🎯",
    val dailyPoints: Int = 3,             // 每天打卡积分
    @ColumnInfo(defaultValue = "")
    val lastCheckInDate: String = "",     // 最后打卡日期(yyyy-MM-dd)，防止当天重复打卡
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                     // 成就类型标识
    val title: String,
    val description: String,
    val emoji: String,
    val unlockedAt: Long = System.currentTimeMillis()
)

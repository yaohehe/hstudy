package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["sessionKey"], unique = true)],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val durationMinutes: Int,             // 专注时长（分钟）
    val taskTitle: String = "",           // 关联任务名
    val completedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val earnedPoints: Int = 0,
    val date: String = "",                // yyyy-MM-dd
    /** Pomodoro 起锚点幂等键；历史/手动专注记录保持 null。 */
    val sessionKey: String? = null,
)

@Entity(tableName = "daily_plans")
data class DailyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                     // yyyy-MM-dd
    val startHour: Int,                   // 开始小时
    val startMinute: Int,                 // 开始分钟
    val endHour: Int,                     // 结束小时
    val endMinute: Int,                   // 结束分钟
    val taskTitle: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds()
)

package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日日程项：家长为孩子设定的时间日程（如7:00起床、16:00写作业）
 */
@Entity(tableName = "daily_schedules")
data class DailyScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: String,                      // HH:mm 格式
    val title: String,                     // 日程标题
    val emoji: String = "⏰",              // 图标
    val linkedTaskId: Long? = null,        // 关联任务ID（可选）
    val isEnabled: Boolean = true,         // 是否启用
    val repeatDays: String = "1,2,3,4,5,6,7", // 重复日（1=周一...7=周日）
    val sortOrder: Int = 0
)

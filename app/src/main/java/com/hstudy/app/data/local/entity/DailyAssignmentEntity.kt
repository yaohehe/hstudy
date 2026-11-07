package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 家长布置的每日任务（区别于固定打卡任务，针对当天推送）
 */
@Entity(tableName = "daily_assignments")
data class DailyAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                     // 任务标题
    val description: String = "",          // 任务描述
    val points: Int = 5,                   // 积分值
    val assignedDate: String = "",         // 布置日期 yyyy-MM-dd
    val isCompleted: Boolean = false,      // 孩子是否已完成
    val completedAt: Long? = null,         // 完成时间戳
    val parentNote: String = "",           // 家长鼓励语
    val createdAt: Long = System.currentTimeMillis()
)

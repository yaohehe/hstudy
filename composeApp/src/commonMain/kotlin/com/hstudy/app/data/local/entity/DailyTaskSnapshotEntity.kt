package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_task_snapshots")
data class DailyTaskSnapshotEntity(
    @PrimaryKey val date: String,
    val taskIdsJson: String,
    val totalCount: Int,
    val createdAt: Long
)

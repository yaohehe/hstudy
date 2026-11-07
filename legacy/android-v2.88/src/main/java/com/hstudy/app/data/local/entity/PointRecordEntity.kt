package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 积分记录类型
 *
 * 重要约定（积分生命周期一致性）：
 * - 打卡类行为（TASK_COMPLETE / 习惯 / 布置任务 / 每日目标）在打卡瞬间**不写积分记录**，
 *   只生成待审申请；只有家长审批通过后才写入一条积分记录。
 * - 撤销已审批的打卡，不物理删除原记录，而是追加一条 REVERSAL 反向记录冲抵，
 *   保证账本append-only、可追溯。
 */
enum class PointRecordType {
    TASK_COMPLETE,    // 完成任务获得（审批通过后写入）
    BONUS,            // 额外奖励（审批通过后写入）
    ADJUSTMENT,       // 父母手动调整
    REWARD_EXCHANGE,  // 兑换奖励消耗
    REVERSAL          // 撤销冲抵（取消打卡/撤销已发积分时写入的反向记录）
}

@Entity(tableName = "point_records")
data class PointRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,           // 关联任务（可空，手动调整时无taskId）
    val points: Int,                     // 积分变动（正数=获得，负数=消耗）
    val type: PointRecordType,
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val weekStart: Long = 0,             // 所属周的起始时间戳（用于周报统计）
    val approvalId: Long? = null         // 关联的审批申请（审批通过的积分可溯源；撤销时用于定位）
)

@Entity(tableName = "task_completions")
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completedAt: Long = System.currentTimeMillis(),
    val pointsEarned: Int,
    val note: String = "",
    val date: String = ""                // yyyy-MM-dd 格式
)

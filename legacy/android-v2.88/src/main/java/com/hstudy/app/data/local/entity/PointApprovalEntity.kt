package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 积分审核申请实体：孩子完成学习行为后生成待审核记录，家长审核通过后才实际加分。
 * 实现"申请→家长审核→确认加分"机制，防止孩子自行给自己加分。
 *
 * 生命周期一致性核心字段：
 * - [bizKey] 业务唯一键（如 TASK:12:2026-09-06）。同一 bizKey 同时最多只有一条申请处于
 *   PENDING，且最多只有一条处于"已计分"状态（APPROVED）。取消打卡后重新打卡会复用/重建，
 *   但绝不会让两条申请同时生效，从根本上杜绝重复累计。
 * - [awardedRecordId] 审批通过时写入的积分记录 id，撤销时可精确定位并冲抵，
 *   避免"撤销后积分残留"。
 */
@Entity(tableName = "point_approvals")
data class PointApprovalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,          // 行为类型：TASK_COMPLETE / MISTAKE_REVIEW / FOCUS_COMPLETE / VARIATION / HABIT_CHECKIN / ASSIGNMENT / DAILY_TARGET / OTHER
    val description: String,         // 描述（如"完成数学作业"、"复习错题：xxx"）
    val requestedPoints: Int,        // 申请积分
    val status: ApprovalStatus = ApprovalStatus.PENDING,  // 审核状态
    val requestedAt: Long = System.currentTimeMillis(),   // 申请时间
    val reviewedAt: Long = 0,        // 审核时间（0=未审核）
    val taskId: Long? = null,        // 关联任务 id（任务打卡时有值）
    val bizKey: String = "",         // 业务唯一键：同一业务（任务+日期等）只允许一条生效申请
    val awardedRecordId: Long? = null, // 审批通过时写入的积分记录 id（撤销扣回时定位用）
    val date: String = ""            // 归属日期 yyyy-MM-dd（打卡类行为必填，便于按日去重）
)

/**
 * 审核状态
 *
 * 终态说明：
 * - APPROVED  已通过并已发放积分（计入余额）
 * - REJECTED  家长拒绝（不计分）
 * - CANCELLED 未审批前被孩子取消打卡（不计分）
 * - REVOKED   已发放积分后被撤销，已通过 REVERSAL 记录冲抵（净额为 0）
 */
enum class ApprovalStatus {
    PENDING,    // 待审核
    APPROVED,   // 已通过（已计分）
    REJECTED,   // 已拒绝
    CANCELLED,  // 已取消（未审批前撤销打卡）
    REVOKED     // 已作废（审批通过后撤销打卡，积分已扣回）
}

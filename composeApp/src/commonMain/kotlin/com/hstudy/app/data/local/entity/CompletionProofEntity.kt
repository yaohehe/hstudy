package com.hstudy.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

/**
 * 完成证明状态
 */
enum class ProofStatus {
    PENDING,    // 待审核
    APPROVED,   // 已通过
    REJECTED    // 已拒绝
}

@Entity(tableName = "completion_proofs")
data class CompletionProofEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val photoPath: String,                // 照片路径
    val note: String = "",                // 备注说明
    val status: ProofStatus = ProofStatus.PENDING,
    val submittedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val reviewedAt: Long? = null,
    val pointsAwarded: Int = 0,           // 审核通过后发放的积分
    val date: String = ""                 // yyyy-MM-dd
)

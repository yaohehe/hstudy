package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointApprovalDao {

    @Query("SELECT * FROM point_approvals ORDER BY requestedAt DESC")
    fun getAllApprovals(): Flow<List<PointApprovalEntity>>

    @Query("SELECT * FROM point_approvals WHERE status = :status ORDER BY requestedAt DESC")
    fun getApprovalsByStatus(status: ApprovalStatus): Flow<List<PointApprovalEntity>>

    @Query("SELECT COUNT(*) FROM point_approvals WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insert(approval: PointApprovalEntity): Long

    @Update
    suspend fun update(approval: PointApprovalEntity)

    @Query("UPDATE point_approvals SET status = :status, reviewedAt = :reviewedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ApprovalStatus, reviewedAt: Long = System.currentTimeMillis())

    @Query("UPDATE point_approvals SET status = 'APPROVED', reviewedAt = :reviewedAt WHERE status = 'PENDING'")
    suspend fun approveAll(reviewedAt: Long = System.currentTimeMillis())

    // ===== 生命周期一致性：按业务唯一键(bizKey)定位申请 =====

    /** 同一业务最近一条申请（不论状态） */
    @Query("SELECT * FROM point_approvals WHERE bizKey = :bizKey ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByBizKey(bizKey: String): PointApprovalEntity?

    /** 同一业务处于待审核的申请（用于重复打卡时复用，避免产生第二条申请） */
    @Query("SELECT * FROM point_approvals WHERE bizKey = :bizKey AND status = 'PENDING' ORDER BY id DESC LIMIT 1")
    suspend fun getPendingByBizKey(bizKey: String): PointApprovalEntity?

    /**
     * 同一业务仍处于"已计分"状态的申请（APPROVED）。
     * 用于审批时的重复防护：一条业务最多只允许一条申请真正发放积分。
     */
    @Query("SELECT * FROM point_approvals WHERE bizKey = :bizKey AND status = 'APPROVED' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveAwardedByBizKey(bizKey: String): PointApprovalEntity?

    @Query("SELECT * FROM point_approvals WHERE id = :id")
    suspend fun getById(id: Long): PointApprovalEntity?

    /** 审核结果落库：同时记录本条申请实际发放的积分记录 id，便于撤销时精确定位冲抵 */
    @Query("UPDATE point_approvals SET status = :status, reviewedAt = :reviewedAt, awardedRecordId = :awardedRecordId WHERE id = :id")
    suspend fun updateReview(
        id: Long,
        status: ApprovalStatus,
        awardedRecordId: Long?,
        reviewedAt: Long = System.currentTimeMillis()
    )

    /** 当前待审核申请列表（用于批量审批，在仓库层逐条幂等发放） */
    @Query("SELECT * FROM point_approvals WHERE status = 'PENDING' ORDER BY requestedAt ASC")
    suspend fun getPendingList(): List<PointApprovalEntity>

    /** 待审核积分总额——展示用，绝不计入可用余额 */
    @Query("SELECT COALESCE(SUM(requestedPoints), 0) FROM point_approvals WHERE status = 'PENDING'")
    fun getPendingPoints(): Flow<Int>
}

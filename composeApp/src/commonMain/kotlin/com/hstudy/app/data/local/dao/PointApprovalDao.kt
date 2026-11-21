package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

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
    suspend fun updateStatus(id: Long, status: ApprovalStatus, reviewedAt: Long = Clock.System.now().toEpochMilliseconds())

    @Query("UPDATE point_approvals SET status = 'APPROVED', reviewedAt = :reviewedAt WHERE status = 'PENDING'")
    suspend fun approveAll(reviewedAt: Long = Clock.System.now().toEpochMilliseconds())

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
        reviewedAt: Long = Clock.System.now().toEpochMilliseconds()
    )

    /**
     * D-03（指令 §4.1 P1-01）：某一天的全部任务类审批申请——**单查询**，任务卡逐卡取态由此一次
     * 流式读取，禁止按卡 N+1。
     *
     * 口径与 `PointRepository.checkInForTask` 的 bizKey 生成逻辑逐字对齐（以代码为准）：
     * - 前缀：`bizKey = "TASK:" + taskId + ":" + date`，所以任务类判据就是 `bizKey LIKE 'TASK:%'`
     *   （ASSIGN/HABIT/MISTAKE_REV/VARIATION 等其它前缀天然不混入）；
     * - 日期编码：bizKey 里的日期是本地时区 `yyyy-MM-dd`（todayString()），与 [PointApprovalEntity.date]
     *   列**同源同值**（ensureApproval 用同一个 today 同时写 bizKey 尾巴与 date 列），因此按
     *   `date = :date` 过滤即精确等价于"当日 bizKey 尾巴"，不需要（也刻意不复刻）时区运算推
     *   requestedAt 的 dayStart 时间戳。
     * - 同一 bizKey 的多条（拒绝后重打卡产生新行）由仓库层取 id 最大一条，这里只 ORDER BY id，
     *   不改动任何 bizKey 比较/写库语义。
     */
    @Query("SELECT * FROM point_approvals WHERE bizKey LIKE 'TASK:%' AND date = :date ORDER BY id ASC")
    fun getTaskApprovalsForDate(date: String): Flow<List<PointApprovalEntity>>

    /** 当前待审核申请列表（用于批量审批，在仓库层逐条幂等发放） */
    @Query("SELECT * FROM point_approvals WHERE status = 'PENDING' ORDER BY requestedAt ASC")
    suspend fun getPendingList(): List<PointApprovalEntity>

    /** 待审核积分总额——展示用，绝不计入可用余额 */
    @Query("SELECT COALESCE(SUM(requestedPoints), 0) FROM point_approvals WHERE status = 'PENDING'")
    fun getPendingPoints(): Flow<Int>
}

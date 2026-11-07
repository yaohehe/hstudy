package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import kotlinx.coroutines.flow.Flow

/**
 * 积分审核仓库：管理孩子的积分申请和家长的审核操作。
 */
class PointApprovalRepository(private val dao: PointApprovalDao) {

    fun getAllApprovals(): Flow<List<PointApprovalEntity>> = dao.getAllApprovals()

    fun getPendingApprovals(): Flow<List<PointApprovalEntity>> = dao.getApprovalsByStatus(ApprovalStatus.PENDING)

    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    /** 待审批积分总额（仅展示，绝不计入可用余额） */
    fun getPendingPoints(): Flow<Int> = dao.getPendingPoints()

    /**
     * 孩子提交积分申请（仅登记申请，**不写积分**）。
     *
     * @param bizKey 业务唯一键（如 TASK:12:2026-09-06）。传入后由仓库保证同一业务
     *               不会重复计分；不传则按一次性申请处理（历史行为）。
     */
    suspend fun submitApproval(
        actionType: String,
        description: String,
        points: Int,
        bizKey: String = "",
        taskId: Long? = null,
        date: String = ""
    ): Long {
        return dao.insert(PointApprovalEntity(
            actionType = actionType,
            description = description,
            requestedPoints = points,
            taskId = taskId,
            bizKey = bizKey,
            date = date
        ))
    }

    /** 家长通过单条申请 */
    suspend fun approve(id: Long) {
        dao.updateStatus(id, ApprovalStatus.APPROVED)
    }

    /** 家长拒绝单条申请 */
    suspend fun reject(id: Long) {
        dao.updateStatus(id, ApprovalStatus.REJECTED)
    }

    /** 家长批量通过所有待审核 */
    suspend fun approveAll() {
        dao.approveAll()
    }
}

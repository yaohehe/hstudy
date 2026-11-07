package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.HabitDao
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import kotlinx.coroutines.flow.Flow

/**
 * 完成证明 Repository - 管理孩子提交的照片证明
 */
class ProofRepository(private val habitDao: HabitDao) {

    suspend fun addProof(proof: CompletionProofEntity): Long = habitDao.insertProof(proof)

    suspend fun updateProof(proof: CompletionProofEntity) = habitDao.updateProof(proof)

    fun getPendingProofs(): Flow<List<CompletionProofEntity>> = habitDao.getPendingProofs()

    fun getAllProofs(): Flow<List<CompletionProofEntity>> = habitDao.getAllProofs()

    fun getPendingProofCount(): Flow<Int> = habitDao.getPendingProofCount()

    suspend fun getProofForTaskOnDate(taskId: Long, date: String): CompletionProofEntity? =
        habitDao.getProofForTaskOnDate(taskId, date)

    suspend fun approveProof(id: Long, points: Int) =
        habitDao.reviewProof(id, ProofStatus.APPROVED, System.currentTimeMillis(), points)

    suspend fun rejectProof(id: Long) =
        habitDao.reviewProof(id, ProofStatus.REJECTED, System.currentTimeMillis())
}

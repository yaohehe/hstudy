package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.DirectTransactionRunner
import com.hstudy.app.data.local.RewardPresetCatalog
import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.RewardExchangeEntity
import kotlinx.coroutines.flow.Flow

class RewardRepository(
    private val rewardDao: RewardDao,
    private val pointRepository: PointRepository,
    private val transactionRunner: DbTransactionRunner = DirectTransactionRunner
) {

    fun getAllActiveRewards(): Flow<List<RewardEntity>> = rewardDao.getAllActiveRewards()

    suspend fun getActiveRewardCount(): Int = rewardDao.getActiveRewardCount()

    /** Ensure every canonical preset exists without replacing custom rewards or edited preset rows. */
    suspend fun ensureDefaultRewards() {
        initDefaultRewards()
    }

    suspend fun getRewardById(id: Long): RewardEntity? = rewardDao.getRewardById(id)

    suspend fun addReward(reward: RewardEntity): Long = rewardDao.insertReward(reward)

    suspend fun updateReward(reward: RewardEntity) = rewardDao.updateReward(reward)

    suspend fun deleteReward(id: Long) = rewardDao.softDeleteReward(id)

    /**
     * Exchange history and its guarded point debit are one repository transaction.
     *
     * @return the exchange row id, or -1 when the amount is invalid or the balance is insufficient.
     */
    suspend fun exchangeReward(rewardId: Long, pointsSpent: Int, note: String = ""): Long =
        transactionRunner.runInTransaction {
            if (pointsSpent <= 0) return@runInTransaction -1L
            val debited = pointRepository.spendPoints(
                points = pointsSpent,
                note = "兑换：$note",
                requireSufficientBalance = true
            )
            if (!debited) return@runInTransaction -1L
            rewardDao.insertExchange(
                RewardExchangeEntity(
                    rewardId = rewardId,
                    pointsSpent = pointsSpent,
                    note = note
                )
            )
        }

    fun getAllExchanges(): Flow<List<RewardExchangeEntity>> = rewardDao.getAllExchanges()

    /** Initialize the canonical preset set atomically; existing keyed rows and custom rewards survive. */
    suspend fun initDefaultRewards() {
        transactionRunner.runInTransaction {
            RewardPresetCatalog.all.forEach { rewardDao.insertReward(it.toEntity()) }
        }
    }
}

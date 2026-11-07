package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.RewardExchangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    // ===== 奖励 =====
    @Query("SELECT * FROM rewards WHERE isActive = 1 ORDER BY pointsCost ASC")
    fun getAllActiveRewards(): Flow<List<RewardEntity>>

    @Query("SELECT COUNT(*) FROM rewards WHERE isActive = 1")
    suspend fun getActiveRewardCount(): Int

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getRewardById(id: Long): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: RewardEntity): Long

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Query("UPDATE rewards SET isActive = 0 WHERE id = :id")
    suspend fun softDeleteReward(id: Long)

    // ===== 兑换记录 =====
    @Insert
    suspend fun insertExchange(exchange: RewardExchangeEntity): Long

    @Query("SELECT * FROM reward_exchanges ORDER BY exchangedAt DESC")
    fun getAllExchanges(): Flow<List<RewardExchangeEntity>>

    @Query("SELECT * FROM reward_exchanges WHERE rewardId = :rewardId ORDER BY exchangedAt DESC")
    fun getExchangesByReward(rewardId: Long): Flow<List<RewardExchangeEntity>>
}

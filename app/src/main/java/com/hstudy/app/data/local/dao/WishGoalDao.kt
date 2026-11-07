package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.WishGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WishGoalDao {

    @Insert
    suspend fun insert(goal: WishGoalEntity): Long

    @Update
    suspend fun update(goal: WishGoalEntity)

    @Query("DELETE FROM wish_goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM wish_goals ORDER BY isActive DESC, createdAt ASC")
    fun getAllGoals(): Flow<List<WishGoalEntity>>

    @Query("SELECT * FROM wish_goals WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveGoals(): Flow<List<WishGoalEntity>>

    @Query("SELECT * FROM wish_goals WHERE id = :id")
    suspend fun getGoalById(id: Long): WishGoalEntity?

    @Query("UPDATE wish_goals SET savedPoints = savedPoints + :amount WHERE id = :id")
    suspend fun addSavedPoints(id: Long, amount: Int)

    @Query("UPDATE wish_goals SET isActive = 0, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)
}

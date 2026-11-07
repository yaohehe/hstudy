package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.DailyAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAssignmentDao {

    @Insert
    suspend fun insert(assignment: DailyAssignmentEntity): Long

    @Update
    suspend fun update(assignment: DailyAssignmentEntity)

    @Query("DELETE FROM daily_assignments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM daily_assignments WHERE assignedDate = :date ORDER BY createdAt DESC")
    fun getAssignmentsByDate(date: String): Flow<List<DailyAssignmentEntity>>

    @Query("SELECT * FROM daily_assignments WHERE assignedDate = :date AND isCompleted = 0 ORDER BY createdAt DESC")
    fun getPendingAssignmentsByDate(date: String): Flow<List<DailyAssignmentEntity>>

    @Query("SELECT * FROM daily_assignments WHERE id = :id")
    suspend fun getAssignmentById(id: Long): DailyAssignmentEntity?

    @Query("UPDATE daily_assignments SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)
}

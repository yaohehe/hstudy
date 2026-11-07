package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isActive = 1 ORDER BY category, sortOrder")
    fun getAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isActive = 1")
    suspend fun getActiveTaskCount(): Int

    @Query("SELECT * FROM tasks WHERE isActive = 1 ORDER BY category, sortOrder")
    suspend fun getAllActiveTasksSnapshot(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE category = :category AND isActive = 1 ORDER BY sortOrder")
    fun getTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Delete
    suspend fun delete(task: TaskEntity)
}

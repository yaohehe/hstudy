package com.hstudy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hstudy.app.data.local.entity.MandatoryPenaltyRunEntity
import com.hstudy.app.data.local.entity.TaskEntity

@Dao
interface MandatoryPenaltyRunDao {
    /** Active DAILY mandatory tasks as observed inside the caller's write transaction. */
    @Query(
        "SELECT * FROM tasks WHERE isActive = 1 AND isMandatory = 1 AND frequency = 'DAILY' ORDER BY id"
    )
    suspend fun getMandatoryDailyTasks(): List<TaskEntity>

    @Query("SELECT taskId FROM task_completions WHERE date = :date")
    suspend fun getCompletedTaskIds(date: String): List<Long>

    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records")
    suspend fun getBalance(): Int

    /** First-write-wins claim. Returns -1 when another runner already committed this date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(run: MandatoryPenaltyRunEntity): Long

    @Query("UPDATE mandatory_penalty_runs SET deductedPoints = :points WHERE periodKey = :periodKey")
    suspend fun updateDeductedPoints(periodKey: String, points: Int)

    @Query("SELECT * FROM mandatory_penalty_runs WHERE periodKey = :periodKey LIMIT 1")
    suspend fun getByPeriodKey(periodKey: String): MandatoryPenaltyRunEntity?

    @Query("SELECT COUNT(*) FROM mandatory_penalty_runs")
    suspend fun getRunCount(): Int

    /** Durable cursor replaces the DataStore date for deciding where a catch-up starts. */
    @Query(
        "SELECT periodKey FROM mandatory_penalty_runs WHERE periodKey <= :endDate " +
            "ORDER BY periodKey DESC LIMIT 1"
    )
    suspend fun getLatestPeriodKey(endDate: String): String?
}

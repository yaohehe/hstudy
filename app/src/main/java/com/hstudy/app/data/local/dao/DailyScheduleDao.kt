package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyScheduleDao {

    @Insert
    suspend fun insert(schedule: DailyScheduleEntity): Long

    @Update
    suspend fun update(schedule: DailyScheduleEntity)

    @Query("DELETE FROM daily_schedules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM daily_schedules ORDER BY time ASC, sortOrder ASC")
    fun getAllSchedules(): Flow<List<DailyScheduleEntity>>

    @Query("SELECT * FROM daily_schedules WHERE isEnabled = 1 ORDER BY time ASC, sortOrder ASC")
    fun getEnabledSchedules(): Flow<List<DailyScheduleEntity>>

    @Query("SELECT * FROM daily_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): DailyScheduleEntity?

    @Query("UPDATE daily_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM daily_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedulesSync(): List<DailyScheduleEntity>
}

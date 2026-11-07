package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.FocusSessionEntity
import com.hstudy.app.data.local.entity.DailyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {

    // ===== 专注记录 =====
    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY completedAt DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE date = :date ORDER BY completedAt DESC")
    fun getSessionsByDate(date: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE date = :date")
    fun getTotalFocusMinutesForDate(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions")
    fun getTotalFocusMinutes(): Flow<Int>

    @Query("SELECT * FROM focus_sessions WHERE completedAt >= :since ORDER BY completedAt")
    fun getSessionsSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT date, COALESCE(SUM(durationMinutes), 0) AS totalMinutes FROM focus_sessions WHERE completedAt >= :since GROUP BY date ORDER BY date")
    fun getDailyFocusMinutesSince(since: Long): Flow<List<DailyFocusMinutes>>

    // ===== 每日计划 =====
    @Insert
    suspend fun insertPlan(plan: DailyPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: DailyPlanEntity)

    @Query("DELETE FROM daily_plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("SELECT * FROM daily_plans WHERE date = :date ORDER BY startHour, startMinute")
    fun getPlansByDate(date: String): Flow<List<DailyPlanEntity>>

    @Query("UPDATE daily_plans SET isCompleted = :completed WHERE id = :id")
    suspend fun setPlanCompleted(id: Long, completed: Boolean)
}

/**
 * 每日专注分钟数汇总
 */
data class DailyFocusMinutes(
    val date: String,
    val totalMinutes: Int
)

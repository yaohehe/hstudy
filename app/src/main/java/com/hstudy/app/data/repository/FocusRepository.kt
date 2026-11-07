package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.FocusDao
import com.hstudy.app.data.local.dao.DailyFocusMinutes
import com.hstudy.app.data.local.entity.DailyPlanEntity
import com.hstudy.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class FocusRepository(private val focusDao: FocusDao) {

    // ===== 专注记录 =====
    suspend fun recordFocusSession(durationMinutes: Int, taskTitle: String = "", points: Int = 0): Long {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return focusDao.insertSession(
            FocusSessionEntity(
                durationMinutes = durationMinutes,
                taskTitle = taskTitle,
                earnedPoints = points,
                date = today
            )
        )
    }

    fun getAllSessions(): Flow<List<FocusSessionEntity>> = focusDao.getAllSessions()

    fun getSessionsByDate(date: String): Flow<List<FocusSessionEntity>> = focusDao.getSessionsByDate(date)

    fun getTotalFocusMinutesForDate(date: String): Flow<Int> = focusDao.getTotalFocusMinutesForDate(date)

    fun getTotalFocusMinutes(): Flow<Int> = focusDao.getTotalFocusMinutes()

    fun getSessionsSince(since: Long): Flow<List<FocusSessionEntity>> = focusDao.getSessionsSince(since)

    fun getDailyFocusMinutesSince(since: Long): Flow<List<DailyFocusMinutes>> =
        focusDao.getDailyFocusMinutesSince(since)

    // ===== 每日计划 =====
    suspend fun addPlan(plan: DailyPlanEntity): Long = focusDao.insertPlan(plan)

    suspend fun updatePlan(plan: DailyPlanEntity) = focusDao.updatePlan(plan)

    suspend fun deletePlan(id: Long) = focusDao.deletePlan(id)

    fun getPlansByDate(date: String): Flow<List<DailyPlanEntity>> = focusDao.getPlansByDate(date)

    suspend fun setPlanCompleted(id: Long, completed: Boolean) = focusDao.setPlanCompleted(id, completed)

    companion object {
        fun getTodayString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}

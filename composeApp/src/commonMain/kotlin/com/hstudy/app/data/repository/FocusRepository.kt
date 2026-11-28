package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.FocusDao
import com.hstudy.app.data.local.dao.DailyFocusMinutes
import com.hstudy.app.data.local.entity.DailyPlanEntity
import com.hstudy.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

class FocusRepository(private val focusDao: FocusDao) {

    // ===== 专注记录 =====
    suspend fun recordFocusSession(durationMinutes: Int, taskTitle: String = "", points: Int = 0): Long {
        val today = todayString()
        return focusDao.insertSession(
            FocusSessionEntity(
                durationMinutes = durationMinutes,
                taskTitle = taskTitle,
                earnedPoints = points,
                date = today
            )
        )
    }

    /**
     * 按番茄钟起锚点幂等落专注记录。INSERT IGNORE 的并发败者读取同一唯一键，
     * 保证 RESUME_DEPOSIT 重放始终返回同一行 id，从而复用同一 FOCUS bizKey。
     */
    suspend fun recordPomodoroSession(
        durationMinutes: Int,
        taskTitle: String = "",
        points: Int = 0,
        sessionStartEpochMs: Long,
    ): Long {
        val sessionKey = pomodoroSessionKey(sessionStartEpochMs)
        val insertedId = focusDao.insertSessionIfAbsent(
            FocusSessionEntity(
                durationMinutes = durationMinutes,
                taskTitle = taskTitle,
                earnedPoints = points,
                date = todayString(),
                sessionKey = sessionKey,
            )
        )
        return if (insertedId != -1L) {
            insertedId
        } else {
            checkNotNull(focusDao.getSessionByKey(sessionKey)) {
                "Ignored Pomodoro focus insert has no row for $sessionKey"
            }.id
        }
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
        fun getTodayString(): String = todayString()

        /** Durable across process restarts; only the Pomodoro start anchor identifies a round. */
        fun pomodoroSessionKey(sessionStartEpochMs: Long): String = "POMODORO:$sessionStartEpochMs"
    }
}
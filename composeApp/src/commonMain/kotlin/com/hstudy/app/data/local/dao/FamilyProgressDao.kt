package com.hstudy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/** Read-only aggregate boundary dedicated to family progress export. */
@Dao
interface FamilyProgressDao {
    /**
     * Every scalar subquery is evaluated by SQLite for the same statement snapshot, so concurrent
     * point/completion/focus/mistake writes cannot be mixed across independent Flow reads.
     */
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(points), 0) FROM point_records) AS totalPoints,
            (SELECT COUNT(*) FROM task_completions) AS totalCompletions,
            (SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions) AS totalFocusMinutes,
            (SELECT COUNT(*) FROM task_completions WHERE date = :today) AS todayCompletedTasks,
            (SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_sessions WHERE date = :today) AS todayFocusMinutes,
            (SELECT COUNT(*) FROM mistakes) AS totalMistakes,
            (SELECT COUNT(*) FROM mistakes WHERE isMastered = 1) AS masteredMistakes,
            (SELECT GROUP_CONCAT(date, ',') FROM (
                SELECT DISTINCT date FROM task_completions WHERE completedAt >= :since ORDER BY date DESC
            )) AS completionDates
        """
    )
    suspend fun getProgressAggregate(today: String, since: Long): ProgressAggregate
}

/** One transactionally consistent Room snapshot for family progress export. */
data class ProgressAggregate(
    val totalPoints: Int,
    val totalCompletions: Int,
    val totalFocusMinutes: Int,
    val todayCompletedTasks: Int,
    val todayFocusMinutes: Int,
    val totalMistakes: Int,
    val masteredMistakes: Int,
    val completionDates: String?,
)

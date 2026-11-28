package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {

    // ===== 积分记录 =====
    @Insert
    suspend fun insertPointRecord(record: PointRecordEntity): Long

    @Query("SELECT * FROM point_records ORDER BY date DESC")
    fun getAllPointRecords(): Flow<List<PointRecordEntity>>

    @Query("SELECT * FROM point_records WHERE date >= :start AND date < :end ORDER BY date DESC")
    fun getPointRecordsBetween(start: Long, end: Long): Flow<List<PointRecordEntity>>

    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records")
    fun getTotalPoints(): Flow<Int>

    /**
     * 累计获得收入：任务/奖励/家长奖惩正分；心愿退款与其它非收入正调整不得刷高。
     * 无 approvalId 的旧账仅在没有审批单通过 awardedRecordId 回链时生效；有回链必须 APPROVED。
     * ADJUSTMENT 是家长直接发放的收入，不受审批状态限制。
     */
    @Query(
        "SELECT COALESCE(SUM(pr.points), 0) FROM point_records pr " +
            "WHERE pr.points > 0 AND pr.type IN ('TASK_COMPLETE', 'BONUS', 'ADJUSTMENT') AND (" +
            "  pr.type = 'ADJUSTMENT' OR " +
            "  (pr.approvalId IS NOT NULL AND EXISTS(SELECT 1 FROM point_approvals pa " +
            "    WHERE pa.id = pr.approvalId AND pa.status = 'APPROVED')) OR " +
            "  EXISTS(SELECT 1 FROM point_approvals pa WHERE pa.awardedRecordId = pr.id " +
            "    AND pa.status = 'APPROVED') OR " +
            "  (pr.approvalId IS NULL AND NOT EXISTS(SELECT 1 FROM point_approvals pa " +
            "    WHERE pa.awardedRecordId = pr.id))" +
            ")"
    )
    fun getLifetimeEarnedPoints(): Flow<Int>

    /** 指定时间段内获得的积分（只算正数）——用于计算日均收入、预估心愿达成天数 */
    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records WHERE points > 0 AND date >= :start AND date < :end")
    suspend fun getPositivePointsBetween(start: Long, end: Long): Int

    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records WHERE date >= :start AND date < :end")
    fun getPointsBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records WHERE weekStart = :weekStart")
    fun getWeeklyPoints(weekStart: Long): Flow<Int>

    // ===== 任务打卡 =====
    @Insert
    suspend fun insertCompletion(completion: TaskCompletionEntity): Long

    @Query("SELECT * FROM task_completions WHERE date = :date ORDER BY completedAt DESC")
    fun getCompletionsByDate(date: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE taskId = :taskId AND date = :date LIMIT 1")
    suspend fun getCompletionForTaskOnDate(taskId: Long, date: String): TaskCompletionEntity?

    /**
     * Frequency-aware completion lookup. Null bounds represent the unbounded
     * ONCE lifetime; finite windows are half-open [startDate, endDate).
     */
    @Query(
        "SELECT * FROM task_completions WHERE taskId = :taskId " +
            "AND (:startDate IS NULL OR date >= :startDate) " +
            "AND (:endDate IS NULL OR date < :endDate) " +
            "ORDER BY completedAt DESC, id DESC LIMIT 1"
    )
    suspend fun getCompletionForTaskInPeriod(
        taskId: Long,
        startDate: String?,
        endDate: String?
    ): TaskCompletionEntity?

    /** One task-card query for all active task ids; period filtering happens in Kotlin. */
    @Query("SELECT * FROM task_completions WHERE taskId IN (:taskIds) ORDER BY completedAt DESC, id DESC")
    fun getCompletionsForTasks(taskIds: List<Long>): Flow<List<TaskCompletionEntity>>

    /** Exact canonical period snapshot, optionally preferring the selected approval's date. */
    @Query(
        "SELECT * FROM task_completions WHERE taskId = :taskId AND frequencyPeriod = :frequencyPeriod " +
            "ORDER BY CASE WHEN date = :preferredDate THEN 0 ELSE 1 END, completedAt DESC, id DESC LIMIT 1"
    )
    suspend fun getCompletionForTaskInFrequencyPeriod(
        taskId: Long,
        frequencyPeriod: String,
        preferredDate: String?
    ): TaskCompletionEntity?

    /** Date fallback is restricted to rows that predate the frequencyPeriod snapshot. */
    @Query(
        "SELECT * FROM task_completions WHERE taskId = :taskId AND frequencyPeriod IS NULL " +
            "AND (:startDate IS NULL OR date >= :startDate) " +
            "AND (:endDate IS NULL OR date < :endDate) " +
            "ORDER BY CASE WHEN date = :preferredDate THEN 0 ELSE 1 END, completedAt DESC, id DESC LIMIT 1"
    )
    suspend fun getLegacyCompletionForTaskInPeriod(
        taskId: Long,
        startDate: String?,
        endDate: String?,
        preferredDate: String?
    ): TaskCompletionEntity?

    @Query("SELECT taskId FROM task_completions WHERE date = :date")
    fun getCompletedTaskIdsForDate(date: String): Flow<List<Long>>

    /** 完美一天：至少有一个活跃任务，且每个活跃任务当天都有完成记录。 */
    @Query(
        "SELECT CASE WHEN EXISTS(SELECT 1 FROM tasks WHERE isActive = 1) " +
            "AND NOT EXISTS(" +
            "SELECT 1 FROM tasks t WHERE t.isActive = 1 AND NOT EXISTS(" +
            "SELECT 1 FROM task_completions tc WHERE tc.taskId = t.id AND tc.date = :date" +
            ")) THEN 1 ELSE 0 END"
    )
    fun isPerfectDay(date: String): Flow<Boolean>

    @Query("SELECT * FROM task_completions WHERE taskId = :taskId ORDER BY completedAt DESC, id DESC")
    suspend fun getCompletionsForTask(taskId: Long): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions WHERE completedAt >= :start AND completedAt < :end ORDER BY date")
    fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>>

    @Query("SELECT COALESCE(SUM(pointsEarned), 0) FROM task_completions WHERE completedAt >= :start AND completedAt < :end")
    fun getEarnedPointsBetween(start: Long, end: Long): Flow<Int>

    // ===== 撤销打卡 =====
    // 注意：撤销打卡不再物理删除积分记录。未审批的撤销走取消申请；
    // 已审批的撤销通过追加 REVERSAL 反向记录冲抵，保证账本可追溯、净额正确。
    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND date = :date")
    suspend fun deleteCompletionForTaskOnDate(taskId: Long, date: String)

    /** Delete only the completion row selected by exact period/legacy-date lookup. */
    @Query("DELETE FROM task_completions WHERE id = :id")
    suspend fun deleteCompletionById(id: Long)

    // ===== 统计 =====
    @Query("SELECT DISTINCT date FROM task_completions WHERE completedAt >= :since ORDER BY date DESC")
    suspend fun getDistinctCompletionDates(since: Long): List<String>

    /** 累计打卡总次数——用于成就评估 */
    @Query("SELECT COUNT(*) FROM task_completions")
    suspend fun getTotalCompletionCount(): Int

    @Query("SELECT COUNT(*) FROM task_completions")
    fun observeTotalCompletionCount(): Flow<Int>

    @Query("SELECT DISTINCT date FROM task_completions WHERE completedAt >= :since ORDER BY date DESC")
    fun observeDistinctCompletionDates(since: Long): Flow<List<String>>
}

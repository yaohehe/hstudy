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

    /** 累计获得的总积分（只算正数，不含消耗）——用于学习宠物成长等长期激励 */
    @Query("SELECT COALESCE(SUM(points), 0) FROM point_records WHERE points > 0")
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

    @Query("SELECT taskId FROM task_completions WHERE date = :date")
    fun getCompletedTaskIdsForDate(date: String): Flow<List<Long>>

    @Query("SELECT * FROM task_completions WHERE completedAt >= :start AND completedAt < :end ORDER BY date")
    fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>>

    @Query("SELECT COALESCE(SUM(pointsEarned), 0) FROM task_completions WHERE completedAt >= :start AND completedAt < :end")
    fun getEarnedPointsBetween(start: Long, end: Long): Flow<Int>

    // ===== 撤销打卡 =====
    // 注意：撤销打卡不再物理删除积分记录。未审批的撤销走取消申请；
    // 已审批的撤销通过追加 REVERSAL 反向记录冲抵，保证账本可追溯、净额正确。
    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND date = :date")
    suspend fun deleteCompletionForTaskOnDate(taskId: Long, date: String)

    // ===== 统计 =====
    @Query("SELECT DISTINCT date FROM task_completions WHERE completedAt >= :since ORDER BY date DESC")
    suspend fun getDistinctCompletionDates(since: Long): List<String>

    /** 累计打卡总次数——用于成就评估 */
    @Query("SELECT COUNT(*) FROM task_completions")
    suspend fun getTotalCompletionCount(): Int
}

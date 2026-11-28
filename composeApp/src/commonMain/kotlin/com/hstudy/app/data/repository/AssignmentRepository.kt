package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.DailyAssignmentDao
import com.hstudy.app.data.local.entity.DailyAssignmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class AssignmentRepository(
    private val dao: DailyAssignmentDao,
    private val transactionRunner: DbTransactionRunner,
    private val pointRepository: PointRepository? = null,
) {

    fun getTodayString(): String = todayString()

    suspend fun assignTask(
        title: String,
        description: String = "",
        points: Int = 5,
        parentNote: String = ""
    ): Long {
        return dao.insert(
            DailyAssignmentEntity(
                title = title,
                description = description,
                points = points,
                parentNote = parentNote,
                assignedDate = getTodayString()
            )
        )
    }

    fun getTodayAssignments(): Flow<List<DailyAssignmentEntity>> =
        dao.getAssignmentsByDate(getTodayString())

    fun getTodayPendingAssignments(): Flow<List<DailyAssignmentEntity>> =
        dao.getPendingAssignmentsByDate(getTodayString())

    suspend fun getAssignmentById(id: Long): DailyAssignmentEntity? = dao.getAssignmentById(id)

    suspend fun deleteAssignment(id: Long) = dao.delete(id)

    /**
     * 孩子完成布置的任务 - 标记完成并发放积分
     */
    suspend fun completeAssignment(id: Long, onPoints: suspend (Int, String) -> Unit) {
        transactionRunner.runInTransaction {
            val assignment = dao.getAssignmentById(id) ?: return@runInTransaction
            if (assignment.isCompleted) return@runInTransaction
            dao.markCompleted(id, Clock.System.now().toEpochMilliseconds())
            // Keep the points request in the same transaction: a callback failure
            // rolls back both isCompleted and any nested approval writes.
            onPoints(assignment.points, assignment.title)
        }
    }

    /**
     * 孩子撤销已完成的布置任务（Antigravity 指令 P1-01，撤销闭环）：
     * 完成标记回置与积分取消/REVERSAL 在同一事务内完成，返回实际扣回积分。
     */
    suspend fun uncompleteAssignment(id: Long): Int = transactionRunner.runInTransaction {
        val assignment = dao.getAssignmentById(id) ?: return@runInTransaction 0
        if (!assignment.isCompleted) return@runInTransaction 0
        dao.markUncompleted(id)
        val date = assignment.completedAt?.let { PointRepository.completionDateStringOf(it) }
            ?: getTodayString()
        pointRepository?.undoCheckIn(
            bizKey = "ASSIGN:$id:$date",
            date = date,
        ) ?: 0
    }
}
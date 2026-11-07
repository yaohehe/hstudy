package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyAssignmentDao
import com.hstudy.app.data.local.entity.DailyAssignmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class AssignmentRepository(private val dao: DailyAssignmentDao) {

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
        val assignment = dao.getAssignmentById(id) ?: return
        if (assignment.isCompleted) return
        dao.markCompleted(id, Clock.System.now().toEpochMilliseconds())
        onPoints(assignment.points, assignment.title)
    }
}
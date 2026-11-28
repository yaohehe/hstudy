package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryReminderCancellationTest {

    @Test
    fun deleteTaskCancelsExactlyTheSchedulesUnlinkedByTheCommittedOperation(): Unit = runBlocking {
        val taskDao = FakeTaskDao()
        val scheduleDao = FakeDailyScheduleDao()
        val taskId = taskDao.insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 1))
        val otherTaskId = taskDao.insert(TaskEntity(title = "阅读", category = TaskCategory.BASIC, points = 1))
        val linkedIds = setOf(11L, 12L)
        linkedIds.forEach { id ->
            scheduleDao.insert(DailyScheduleEntity(id = id, time = "16:00", title = "作业", linkedTaskId = taskId))
        }
        val unrelatedId = 13L
        scheduleDao.insert(
            DailyScheduleEntity(id = unrelatedId, time = "17:00", title = "阅读", linkedTaskId = otherTaskId)
        )
        var cancelledIds = emptyList<Long>()
        var callbackObservedCommittedState = false
        val repository = TaskRepository(
            taskDao,
            scheduleDao,
            reminderCanceller = { ids ->
                callbackObservedCommittedState = taskDao.allRows().find { it.id == taskId }?.isActive == false &&
                    linkedIds.all { id -> scheduleDao.currentSchedules().find { it.id == id }?.linkedTaskId == null }
                cancelledIds += ids
            },
        )

        val result = repository.deleteTask(taskId)

        assertTrue(callbackObservedCommittedState)
        assertEquals(linkedIds, cancelledIds.toSet())
        assertEquals(linkedIds, result.cancelled.toSet())
        assertTrue(result.failed.isEmpty())
        assertEquals(otherTaskId, scheduleDao.getScheduleById(unrelatedId)?.linkedTaskId)
    }

    @Test
    fun failedUnlinkDoesNotCancelReminders(): Unit = runBlocking {
        val taskDao = FakeTaskDao()
        val scheduleDao = FakeDailyScheduleDao()
        val taskId = taskDao.insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 1))
        scheduleDao.insert(DailyScheduleEntity(id = 21L, time = "16:00", title = "作业", linkedTaskId = taskId))
        scheduleDao.failUnlink = true
        var cancellationCalled = false
        val repository = TaskRepository(
            taskDao,
            scheduleDao,
            reminderCanceller = { cancellationCalled = true },
        )

        val failure = runCatching { repository.deleteTask(taskId) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(cancellationCalled)
        assertEquals(taskId, scheduleDao.getScheduleById(21L)?.linkedTaskId)
    }

    @Test
    fun applyTemplateCancelsOnlySchedulesForSoftDeletedTasks(): Unit = runBlocking {
        val taskDao = FakeTaskDao()
        val scheduleDao = FakeDailyScheduleDao()
        val firstTaskId = taskDao.insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 1))
        val secondTaskId = taskDao.insert(TaskEntity(title = "阅读", category = TaskCategory.BASIC, points = 1))
        val firstScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "16:00", title = "作业", linkedTaskId = firstTaskId)
        )
        val secondScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = secondTaskId)
        )
        val unrelatedId = scheduleDao.insert(
            DailyScheduleEntity(time = "18:00", title = "运动")
        )
        var cancelledIds = emptyList<Long>()
        val repository = TaskRepository(
            taskDao,
            scheduleDao,
            reminderCanceller = { cancelledIds += it },
        )

        val result = repository.applyTemplate(
            TaskTemplate(
                id = "empty", name = "empty", description = "", emoji = "", ageRange = "",
                tasks = emptyList(),
            )
        )

        assertEquals(setOf(firstScheduleId, secondScheduleId), cancelledIds.toSet())
        assertEquals(setOf(firstScheduleId, secondScheduleId), result.cancelled.toSet())
        assertTrue(result.failed.isEmpty())
        assertNull(scheduleDao.getScheduleById(firstScheduleId)?.linkedTaskId)
        assertNull(scheduleDao.getScheduleById(secondScheduleId)?.linkedTaskId)
        assertNull(scheduleDao.getScheduleById(unrelatedId)?.linkedTaskId)
    }

    @Test
    fun deleteTaskReportsCancellationFailureAndContinuesWithLaterIds(): Unit = runBlocking {
        val taskDao = FakeTaskDao()
        val scheduleDao = FakeDailyScheduleDao()
        val taskId = taskDao.insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 1))
        val firstScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "16:00", title = "作业", linkedTaskId = taskId)
        )
        val secondScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = taskId)
        )
        val attempted = mutableListOf<Long>()
        val repository = TaskRepository(
            taskDao,
            scheduleDao,
            reminderCanceller = { ids ->
                attempted += ids
                if (ids.single() == firstScheduleId) error("injected cancellation failure")
            },
        )

        val result = repository.deleteTask(taskId)

        assertEquals(listOf(firstScheduleId, secondScheduleId), attempted)
        assertEquals(listOf(secondScheduleId), result.cancelled)
        assertEquals(listOf(firstScheduleId), result.failed)
        assertFalse(taskDao.allRows().single { it.id == taskId }.isActive)
    }

    @Test
    fun applyTemplateReportsCancellationFailuresWithoutFailingTheCommittedTemplate(): Unit = runBlocking {
        val taskDao = FakeTaskDao()
        val scheduleDao = FakeDailyScheduleDao()
        val taskId = taskDao.insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 1))
        val failedScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "16:00", title = "作业", linkedTaskId = taskId)
        )
        val cancelledScheduleId = scheduleDao.insert(
            DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = taskId)
        )
        val attempted = mutableListOf<Long>()
        val repository = TaskRepository(
            taskDao,
            scheduleDao,
            reminderCanceller = { ids ->
                attempted += ids
                if (ids.single() == failedScheduleId) error("injected cancellation failure")
            },
        )
        val replacement = TaskEntity(title = "新模板任务", category = TaskCategory.BASIC, points = 2)

        val result = repository.applyTemplate(
            TaskTemplate(
                id = "replacement", name = "replacement", description = "", emoji = "", ageRange = "",
                tasks = listOf(replacement),
            )
        )

        assertEquals(listOf(failedScheduleId, cancelledScheduleId), attempted)
        assertEquals(listOf(cancelledScheduleId), result.cancelled)
        assertEquals(listOf(failedScheduleId), result.failed)
        assertTrue(taskDao.getAllActiveTasksSnapshot().any { it.title == replacement.title })
    }
}

private class FakeDailyScheduleDao : DailyScheduleDao {
    private val schedules = MutableStateFlow<List<DailyScheduleEntity>>(emptyList())
    var failUnlink = false

    fun currentSchedules(): List<DailyScheduleEntity> = schedules.value

    override suspend fun insert(schedule: DailyScheduleEntity): Long {
        val id = if (schedule.id != 0L) schedule.id else ((schedules.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        schedules.value = schedules.value.filterNot { it.id == id } + schedule.copy(id = id)
        return id
    }

    override suspend fun update(schedule: DailyScheduleEntity) {
        schedules.value = schedules.value.map { if (it.id == schedule.id) schedule else it }
    }

    override suspend fun delete(id: Long) {
        schedules.value = schedules.value.filterNot { it.id == id }
    }

    override fun getAllSchedules(): Flow<List<DailyScheduleEntity>> = schedules
    override fun getEnabledSchedules(): Flow<List<DailyScheduleEntity>> = schedules.map { list -> list.filter { it.isEnabled } }
    override suspend fun getScheduleById(id: Long): DailyScheduleEntity? = schedules.value.find { it.id == id }
    override suspend fun setEnabled(id: Long, enabled: Boolean) = update(getScheduleById(id)!!.copy(isEnabled = enabled))
    override suspend fun getScheduleIdsForTask(taskId: Long): List<Long> =
        schedules.value.filter { it.linkedTaskId == taskId }.map { it.id }

    override suspend fun unlinkTask(taskId: Long): Int {
        if (failUnlink) error("injected schedule unlink failure")
        val linked = schedules.value.filter { it.linkedTaskId == taskId }
        schedules.value = schedules.value.map { if (it.linkedTaskId == taskId) it.copy(linkedTaskId = null) else it }
        return linked.size
    }

    override suspend fun getEnabledSchedulesSync(): List<DailyScheduleEntity> = schedules.value.filter { it.isEnabled }
}

package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDeleteScheduleTest {

    @Test
    fun softDeleteUnlinksOnlySchedulesForThatTask() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2))
            val otherTaskId = db.taskDao().insert(TaskEntity(title = "阅读", category = TaskCategory.BASIC, points = 2))
            val linkedId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:00", title = "写作业", linkedTaskId = taskId)
            )
            val secondLinkedId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:30", title = "继续写作业", linkedTaskId = taskId)
            )
            val otherId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = otherTaskId)
            )
            val repository = TaskRepository(db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db))

            repository.deleteTask(taskId)

            assertEquals(false, db.taskDao().getTaskById(taskId)?.isActive)
            assertNotNull(db.taskDao().getTaskById(otherTaskId))
            val schedules = db.dailyScheduleDao().getAllSchedules().first().associateBy { it.id }
            assertNull(schedules.getValue(linkedId).linkedTaskId)
            assertNull(schedules.getValue(secondLinkedId).linkedTaskId)
            assertEquals(otherTaskId, schedules.getValue(otherId).linkedTaskId)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedScheduleUnlinkRollsBackTaskSoftDelete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2))
            val scheduleId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:00", title = "写作业", linkedTaskId = taskId)
            )
            val failingDao = object : DailyScheduleDao by db.dailyScheduleDao() {
                override suspend fun unlinkTask(taskId: Long): Int = error("injected schedule unlink failure")
            }
            val repository = TaskRepository(db.taskDao(), failingDao, roomTransactionRunner(db))

            val failure = runCatching { repository.deleteTask(taskId) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertNotNull(db.taskDao().getTaskById(taskId))
            assertEquals(taskId, db.dailyScheduleDao().getScheduleById(scheduleId)?.linkedTaskId)
        } finally {
            db.close()
        }
    }

    @Test
    fun applyTemplateUnlinksSchedulesForEverySoftDeletedTask() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val firstTaskId = db.taskDao().insert(
                TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2)
            )
            val secondTaskId = db.taskDao().insert(
                TaskEntity(title = "阅读", category = TaskCategory.HABIT, points = 2)
            )
            val firstScheduleId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:00", title = "写作业", linkedTaskId = firstTaskId)
            )
            val secondScheduleId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = secondTaskId)
            )
            val repository = TaskRepository(db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db))
            val template = TaskTemplate(
                id = "test", name = "测试", description = "", emoji = "", ageRange = "",
                tasks = listOf(TaskEntity(title = "模板任务", category = TaskCategory.BASIC, points = 3))
            )

            repository.applyTemplate(template)

            assertEquals(false, db.taskDao().getTaskById(firstTaskId)?.isActive)
            assertEquals(false, db.taskDao().getTaskById(secondTaskId)?.isActive)
            assertNull(db.dailyScheduleDao().getScheduleById(firstScheduleId)?.linkedTaskId)
            assertNull(db.dailyScheduleDao().getScheduleById(secondScheduleId)?.linkedTaskId)
            assertEquals(listOf("模板任务"), db.taskDao().getAllActiveTasksSnapshot().map { it.title })
        } finally {
            db.close()
        }
    }

    @Test
    fun legacyDeleteRejectsUnsupportedScheduleDaoBeforeMutation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val taskId = db.taskDao().insert(
                TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2)
            )
            val repository = TaskRepository(db.taskDao())

            val failure = runCatching { repository.deleteTask(taskId) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals("DailyScheduleDao is required before soft-deleting tasks", failure?.message)
            assertNotNull(db.taskDao().getTaskById(taskId))
        } finally {
            db.close()
        }
    }

    @Test
    fun unlinkTaskReturnsAffectedRowsAndLeavesUnrelatedSchedulesUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2))
            val otherTaskId = db.taskDao().insert(TaskEntity(title = "阅读", category = TaskCategory.BASIC, points = 2))
            val firstId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:00", title = "写作业", linkedTaskId = taskId)
            )
            val secondId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:30", title = "阅读", linkedTaskId = otherTaskId)
            )

            assertEquals(1, db.dailyScheduleDao().unlinkTask(taskId))
            assertEquals(0, db.dailyScheduleDao().unlinkTask(taskId))
            assertNull(db.dailyScheduleDao().getScheduleById(firstId)?.linkedTaskId)
            assertEquals(otherTaskId, db.dailyScheduleDao().getScheduleById(secondId)?.linkedTaskId)
        } finally {
            db.close()
        }
    }
}

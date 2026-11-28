package com.hstudy.app.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.DailyScheduleDao
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.platform.AndroidNotificationScheduler
import com.hstudy.app.platform.ReminderKind
import com.hstudy.app.platform.ScheduleAlarmReceiver
import com.hstudy.app.platform.ScheduledReminder
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDeleteReminderTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deletingLinkedTaskCancelsOnlyItsScheduleRemindersAfterUnlink() = runBlocking {
        val db = testDatabase(context)
        val scheduler = AndroidNotificationScheduler(context)
        var linkedId = 0L
        var unrelatedId = 0L
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2))
            val otherTaskId = db.taskDao().insert(TaskEntity(title = "阅读", category = TaskCategory.BASIC, points = 2))
            linkedId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "16:00", title = "写作业", linkedTaskId = taskId)
            )
            unrelatedId = db.dailyScheduleDao().insert(
                DailyScheduleEntity(time = "17:00", title = "阅读", linkedTaskId = otherTaskId)
            )
            (setOf(linkedId) + unrelatedId).forEach { id ->
                scheduler.cancel(id.toInt())
                scheduler.schedule(reminder(id))
            }
            (setOf(linkedId) + unrelatedId).forEach { id ->
                assertNotNull("schedule PendingIntent must exist before deletion", schedulePendingIntent(id))
            }
            var cancellationRanAfterUnlink = false
            var cancelledIds = emptyList<Long>()
            val repository = TaskRepository(
                db.taskDao(),
                db.dailyScheduleDao(),
                roomTransactionRunner(db),
                reminderCanceller = { ids ->
                    cancellationRanAfterUnlink = runBlocking {
                        db.taskDao().getTaskById(taskId)?.isActive == false &&
                            db.dailyScheduleDao().getScheduleById(linkedId)?.linkedTaskId == null
                    }
                    cancelledIds = ids
                    ids.forEach { scheduler.cancel(it.toInt(), ReminderKind.SCHEDULE) }
                },
            )

            repository.deleteTask(taskId)

            assertTrue(cancellationRanAfterUnlink)
            assertEquals(setOf(linkedId), cancelledIds.toSet())
            assertNull("linked schedule PendingIntent must be cancelled", schedulePendingIntent(linkedId))
            assertNotNull("unrelated schedule PendingIntent must remain", schedulePendingIntent(unrelatedId))
            assertEquals(otherTaskId, db.dailyScheduleDao().getScheduleById(unrelatedId)?.linkedTaskId)
        } finally {
            (setOf(linkedId) + unrelatedId).forEach { scheduler.cancel(it.toInt()) }
            db.close()
        }
    }

    @Test
    fun failedUnlinkDoesNotCancelTheStillLinkedReminder() = runBlocking {
        val db = testDatabase(context)
        val scheduler = AndroidNotificationScheduler(context)
        val scheduleId = 91104L
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "作业", category = TaskCategory.BASIC, points = 2))
            db.dailyScheduleDao().insert(
                DailyScheduleEntity(id = scheduleId, time = "16:00", title = "写作业", linkedTaskId = taskId)
            )
            scheduler.cancel(scheduleId.toInt())
            scheduler.schedule(reminder(scheduleId))
            var cancellationCalled = false
            val failingDao = object : DailyScheduleDao by db.dailyScheduleDao() {
                override suspend fun unlinkTask(taskId: Long): Int = error("injected schedule unlink failure")
            }
            val repository = TaskRepository(
                db.taskDao(),
                failingDao,
                roomTransactionRunner(db),
                reminderCanceller = { cancellationCalled = true },
            )

            val failure = runCatching { repository.deleteTask(taskId) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertFalse(cancellationCalled)
            assertEquals(taskId, db.dailyScheduleDao().getScheduleById(scheduleId)?.linkedTaskId)
            assertNotNull(schedulePendingIntent(scheduleId))
        } finally {
            scheduler.cancel(scheduleId.toInt())
            db.close()
        }
    }

    private fun reminder(id: Long) = ScheduledReminder(
        id = id.toInt(),
        kind = ReminderKind.SCHEDULE,
        hour = 23,
        minute = 59,
        title = "Schedule",
        body = "Reminder",
    )

    private fun schedulePendingIntent(id: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}

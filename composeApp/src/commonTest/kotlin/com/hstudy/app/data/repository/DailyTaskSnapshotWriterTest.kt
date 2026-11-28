package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyTaskSnapshotDao
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.time.FixedAppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyTaskSnapshotWriterTest {

    @Test
    fun ordinaryEmptyObservationDoesNotCreateSnapshot(): Unit = runBlocking {
        val snapshotDao = FakeDailyTaskSnapshotDao()
        val writer = DailyTaskSnapshotWriter(FakeTaskDao(), snapshotDao, CLOCK)

        writer.captureObserved(emptyList())
        writer.captureActive()

        assertTrue(snapshotDao.rows.isEmpty())
        assertEquals(0, snapshotDao.inserts)
    }

    @Test
    fun preexistingZeroSnapshotIsImmutableWhenTasksAppear(): Unit = runBlocking {
        val snapshotDao = FakeDailyTaskSnapshotDao()
        val existing = DailyTaskSnapshotEntity(
            date = DATE,
            taskIdsJson = "[]",
            totalCount = 0,
            createdAt = 123L,
        )
        snapshotDao.rows[DATE] = existing
        val task = TaskEntity(id = 7L, title = "任务", category = TaskCategory.BASIC, points = 2)
        val writer = DailyTaskSnapshotWriter(FakeTaskDao(), snapshotDao, CLOCK)

        writer.captureObserved(listOf(task))
        writer.captureObserved(emptyList())

        assertEquals(existing, snapshotDao.rows[DATE])
        assertEquals(0, snapshotDao.inserts)
    }

    @Test
    fun firstNonEmptyObservationCapturesSortedUniqueActiveIds(): Unit = runBlocking {
        val snapshotDao = FakeDailyTaskSnapshotDao()
        val writer = DailyTaskSnapshotWriter(FakeTaskDao(), snapshotDao, CLOCK)
        val tasks = listOf(
            TaskEntity(id = 9L, title = "九", category = TaskCategory.BASIC, points = 2),
            TaskEntity(id = 2L, title = "二", category = TaskCategory.HABIT, points = 2),
            TaskEntity(id = 9L, title = "重复", category = TaskCategory.HABIT, points = 2),
        )

        writer.captureObserved(tasks)

        val snapshot = snapshotDao.rows.getValue(DATE)
        assertEquals("[2,9]", snapshot.taskIdsJson)
        assertEquals(2, snapshot.totalCount)
        assertEquals(CLOCK.nowMillis(), snapshot.createdAt)
        assertEquals(1, snapshotDao.inserts)
    }

    private class FakeDailyTaskSnapshotDao : DailyTaskSnapshotDao {
        val rows = mutableMapOf<String, DailyTaskSnapshotEntity>()
        var inserts = 0

        override fun getByDate(date: String): Flow<DailyTaskSnapshotEntity?> =
            MutableStateFlow(rows[date])

        override suspend fun getByDateSync(date: String): DailyTaskSnapshotEntity? = rows[date]

        override suspend fun upsert(snapshot: DailyTaskSnapshotEntity) {
            rows[snapshot.date] = snapshot
        }

        override suspend fun insertIfAbsent(snapshot: DailyTaskSnapshotEntity) {
            inserts++
            rows.putIfAbsent(snapshot.date, snapshot)
        }
    }

    private companion object {
        const val DATE = "2026-11-25"
        val CLOCK = FixedAppClock(
            LocalDate(2026, 11, 25).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
    }
}

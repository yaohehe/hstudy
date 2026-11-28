package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.DailyTaskSnapshotDao
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.time.AppClock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writes the first active-task denominator for each local date.
 *
 * Existing snapshots are immutable report history, including legacy zero-row snapshots.
 * Ordinary empty observations are not persisted because they do not establish a denominator.
 */
class DailyTaskSnapshotWriter(
    private val taskDao: TaskDao,
    private val snapshotDao: DailyTaskSnapshotDao,
    private val clock: AppClock
) {

    /** Called after a task-list Flow emits, including Compose observation/initialization. */
    suspend fun captureObserved(tasks: List<TaskEntity>) {
        capture(clock.today(), tasks)
    }

    /** Reads and captures the current active task set for today. */
    suspend fun captureActive() {
        capture(clock.today(), taskDao.getAllActiveTasksSnapshot())
    }

    private suspend fun capture(date: String, tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) return
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }.sorted()
        if (snapshotDao.getByDateSync(date) != null) return

        // No process lock is held across Room calls: mutations capture inside a Room transaction,
        // while Flow observations capture outside one. INSERT OR IGNORE plus the date primary key
        // provides first-write-wins without a transaction/lock inversion with concurrent observers.
        snapshotDao.insertIfAbsent(
            DailyTaskSnapshotEntity(
                date = date,
                taskIdsJson = Json.encodeToString(taskIds),
                totalCount = taskIds.size,
                createdAt = clock.nowMillis()
            )
        )
    }
}

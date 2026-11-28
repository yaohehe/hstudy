package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.DailyAssignmentEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrequencyBonusAssignmentTest {

    @Test
    fun dailyTaskAllowsOnlyOneCompletionPerCalendarDate() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.DAILY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-11-25", clockFor("2026-11-25")))
            assertEquals(CheckInResult.ALREADY_PENDING, repo.checkInForTask(task, "2026-11-25", clockFor("2026-11-25")))
            assertEquals(1, db.pointDao().getTotalCompletionCount())
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-11-26", clockFor("2026-11-26")))
            assertEquals(2, db.pointDao().getTotalCompletionCount())
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun weeklyTaskCompletedMondayAndTuesdayCreatesOneApproval() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-03-30", clockFor("2026-03-30")))
            assertEquals(CheckInResult.ALREADY_PENDING, repo.checkInForTask(task, "2026-03-31", clockFor("2026-03-31")))
            assertEquals(1, db.pointDao().getTotalCompletionCount())
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            assertEquals(
                "WEEK:2026-03-30",
                db.pointDao().getCompletionsByDate("2026-03-30").first().single().frequencyPeriod
            )
            assertEquals(
                "WEEK:2026-03-30",
                db.pointApprovalDao().getAllApprovals().first().single().frequencyPeriod
            )

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-04-06", clockFor("2026-04-06")))
            assertEquals(2, db.pointDao().getTotalCompletionCount())
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun monthlyTaskAllowsOneCompletionPerCalendarMonth() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.MONTHLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-01-31", clockFor("2026-01-31")))
            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-02-01", clockFor("2026-02-01")))
            assertEquals(CheckInResult.ALREADY_PENDING, repo.checkInForTask(task, "2026-02-28", clockFor("2026-02-28")))
            assertEquals(2, db.pointDao().getTotalCompletionCount())
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-03-01", clockFor("2026-03-01")))
            assertEquals(3, db.pointDao().getTotalCompletionCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun onceTaskRejectsEveryLaterLifetimeAttempt() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.ONCE, points = 5, bonusPoints = 2)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2025-11-25", clockFor("2025-11-25")))
            val approval = db.pointApprovalDao().getAllApprovals().first().single()
            assertEquals(7, repo.approveAndAward(approval.id))
            assertEquals(CheckInResult.ALREADY_CREDITED, repo.checkInForTask(task, "2026-11-25", clockFor("2026-11-25")))
            assertEquals(1, db.pointDao().getTotalCompletionCount())
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            assertEquals(7, repo.getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun taskBonusIsRequestedAndAwardedExactlyOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.DAILY, points = 5, bonusPoints = 2)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-11-25", clockFor("2026-11-25")))
            val approval = db.pointApprovalDao().getAllApprovals().first().single()
            assertEquals(7, approval.requestedPoints)
            assertEquals(7, db.pointDao().getCompletionsByDate("2026-11-25").first().single().pointsEarned)

            assertEquals(7, repo.approveAndAward(approval.id))
            assertEquals(0, repo.approveAndAward(approval.id))
            assertEquals(CheckInResult.ALREADY_CREDITED, repo.checkInForTask(task, "2026-11-25", clockFor("2026-11-25")))
            assertEquals(7, repo.getTotalPoints().first())
            assertEquals(listOf(7), db.pointDao().getAllPointRecords().first().map { it.points })
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentWeeklyCheckInsCreateOnlyOneCompletionAndApproval() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            val results = withTimeout(30_000) {
                coroutineScope {
                    listOf(
                        async { repo.checkInForTask(task, "2026-03-30", clockFor("2026-03-30")) },
                        async { repo.checkInForTask(task, "2026-03-31", clockFor("2026-03-31")) }
                    ).awaitAll()
                }
            }

            assertEquals(listOf(CheckInResult.CREATED, CheckInResult.ALREADY_PENDING), results.sorted())
            assertEquals(1, db.pointDao().getTotalCompletionCount())
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun periodLookupDrivesCompletedAndApprovalStateAcrossBoundaries() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val runner = roomTransactionRunner(db)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val daily = insertTask(db, TaskFrequency.DAILY)
            val weekly = insertTask(db, TaskFrequency.WEEKLY)
            val monthly = insertTask(db, TaskFrequency.MONTHLY)
            val once = insertTask(db, TaskFrequency.ONCE)
            val tasks = listOf(daily, weekly, monthly, once)

            repo.checkInForTask(daily, "2026-04-01", clockFor("2026-04-01"))
            repo.checkInForTask(weekly, "2026-03-30", clockFor("2026-03-30"))
            repo.checkInForTask(monthly, "2026-04-01", clockFor("2026-04-01"))
            repo.checkInForTask(once, "2025-11-25", clockFor("2025-11-25"))

            val aprilSecond = LocalDate(2026, 4, 2)
            assertEquals(
                setOf(weekly.id, monthly.id, once.id),
                repo.getCompletedTaskIdsForPeriod(tasks, aprilSecond).first()
            )
            assertEquals(
                mapOf(
                    weekly.id to ApprovalStatus.PENDING,
                    monthly.id to ApprovalStatus.PENDING,
                    once.id to ApprovalStatus.PENDING
                ),
                repo.getTaskApprovalStates(tasks, aprilSecond).first()
            )

            val nextWeek = LocalDate(2026, 4, 6)
            assertEquals(
                setOf(monthly.id, once.id),
                repo.getCompletedTaskIdsForPeriod(tasks, nextWeek).first()
            )
            assertEquals(
                mapOf(monthly.id to ApprovalStatus.PENDING, once.id to ApprovalStatus.PENDING),
                repo.getTaskApprovalStates(tasks, nextWeek).first()
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun legacyDailyApprovalIsReusedForWeeklyPeriodAndUndo() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            db.pointDao().insertCompletion(
                com.hstudy.app.data.local.entity.TaskCompletionEntity(
                    taskId = task.id,
                    pointsEarned = 5,
                    date = "2026-03-30",
                    frequencyPeriod = "WEEK:2026-03-30"
                )
            )
            db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = "TASK_COMPLETE",
                    description = "完成任务：${task.title}",
                    requestedPoints = 5,
                    taskId = task.id,
                    bizKey = "TASK:${task.id}:2026-03-30",
                    activeBizKey = "TASK:${task.id}:2026-03-30",
                    date = "2026-03-30",
                    frequencyPeriod = "WEEK:2026-03-30"
                )
            )

            assertEquals(
                CheckInResult.ALREADY_PENDING,
                repo.checkInForTask(task, "2026-03-31", clockFor("2026-03-31"))
            )
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            assertEquals(
                mapOf(task.id to ApprovalStatus.PENDING),
                repo.getTaskApprovalStates(listOf(task), LocalDate(2026, 3, 31)).first()
            )
            assertEquals(0, repo.undoTaskCheckIn(task, "2026-03-31"))
            assertEquals(0, db.pointDao().getTotalCompletionCount())
            assertEquals(
                ApprovalStatus.CANCELLED,
                db.pointApprovalDao().getAllApprovals().first().single().status
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun legacyPeriodDuplicatesCannotAwardTwiceAndBlockAnotherCheckIn() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val period = "WEEK:2026-03-30"
            db.pointDao().insertCompletion(
                com.hstudy.app.data.local.entity.TaskCompletionEntity(
                    taskId = task.id,
                    pointsEarned = 5,
                    date = "2026-03-30",
                    frequencyPeriod = period
                )
            )
            db.pointDao().insertCompletion(
                com.hstudy.app.data.local.entity.TaskCompletionEntity(
                    taskId = task.id,
                    pointsEarned = 5,
                    date = "2026-03-31",
                    frequencyPeriod = period
                )
            )
            val mondayId = db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = "TASK_COMPLETE",
                    description = "Monday",
                    requestedPoints = 5,
                    taskId = task.id,
                    bizKey = "TASK:${task.id}:2026-03-30",
                    activeBizKey = "TASK:${task.id}:2026-03-30",
                    date = "2026-03-30",
                    frequencyPeriod = period
                )
            )
            assertEquals(5, repo.approveAndAward(mondayId))
            db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = "TASK_COMPLETE",
                    description = "Tuesday",
                    requestedPoints = 5,
                    status = ApprovalStatus.REJECTED,
                    taskId = task.id,
                    bizKey = "TASK:${task.id}:2026-03-31",
                    date = "2026-03-31",
                    frequencyPeriod = period
                )
            )

            assertEquals(
                CheckInResult.ALREADY_CREDITED,
                repo.checkInForTask(task, "2026-04-01", clockFor("2026-04-01"))
            )
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)

            val wednesdayId = db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = "TASK_COMPLETE",
                    description = "Wednesday",
                    requestedPoints = 5,
                    taskId = task.id,
                    bizKey = "TASK:${task.id}:WEEK:2026-03-30",
                    activeBizKey = "TASK:${task.id}:WEEK:2026-03-30",
                    date = "2026-04-01",
                    frequencyPeriod = period
                )
            )
            assertEquals(0, repo.approveAndAward(wednesdayId))
            assertEquals(ApprovalStatus.CANCELLED, db.pointApprovalDao().getById(wednesdayId)?.status)
            assertEquals(5, repo.getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun weeklyUndoFromLaterDateUsesPeriodKeyAndRemovesOriginalCompletion() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            repo.checkInForTask(task, "2026-03-30", clockFor("2026-03-30"))
            val approval = db.pointApprovalDao().getAllApprovals().first().single()
            assertEquals(5, repo.approveAndAward(approval.id))

            assertEquals(5, repo.undoTaskCheckIn(task, "2026-03-31"))
            assertEquals(0, db.pointDao().getTotalCompletionCount())
            assertEquals(ApprovalStatus.REVOKED, db.pointApprovalDao().getById(approval.id)?.status)
            assertEquals(0, repo.getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun weeklyRejectedRecheckUndoRemovesOriginalPeriodCompletion() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.WEEKLY)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            repo.checkInForTask(task, "2026-03-30", clockFor("2026-03-30"))
            val first = db.pointApprovalDao().getAllApprovals().first().single()
            repo.rejectApproval(first.id)
            assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-03-31", clockFor("2026-03-31")))
            assertEquals(1, db.pointDao().getTotalCompletionCount())

            assertEquals(0, repo.undoTaskCheckIn(task, "2026-03-31"))
            assertEquals(0, db.pointDao().getTotalCompletionCount())
            val latest = db.pointApprovalDao().getAllApprovals().first().maxBy { it.id }
            assertEquals(ApprovalStatus.CANCELLED, latest.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun parentRevokeOfOnceApprovalRemovesLifetimeCompletion() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, TaskFrequency.ONCE)
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            repo.checkInForTask(task, "2025-11-25", clockFor("2025-11-25"))
            val approval = db.pointApprovalDao().getAllApprovals().first().single()
            assertEquals(5, repo.approveAndAward(approval.id))

            assertEquals(5, repo.revokeApproval(approval.id))
            assertEquals(0, db.pointDao().getTotalCompletionCount())
            assertEquals(ApprovalStatus.REVOKED, db.pointApprovalDao().getById(approval.id)?.status)
            assertEquals(0, repo.getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun thrownAssignmentPointsCallbackRollsBackAndRetrySucceedsOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val assignmentRepo = AssignmentRepository(db.dailyAssignmentDao(), runner)
            val assignmentId = db.dailyAssignmentDao().insert(
                DailyAssignmentEntity(title = "数学练习", points = 5, assignedDate = "2026-11-25")
            )
            var callbackCount = 0

            val failure = runCatching {
                assignmentRepo.completeAssignment(assignmentId) { points, title ->
                    callbackCount++
                    pointRepo.checkIn(
                        bizKey = "ASSIGN:$assignmentId:2026-11-25",
                        actionType = "ASSIGNMENT",
                        description = "完成布置任务: $title",
                        points = points,
                        date = "2026-11-25"
                    )
                    error("injected points callback failure")
                }
            }.exceptionOrNull()

            assertEquals("injected points callback failure", failure?.message)
            assertFalse(db.dailyAssignmentDao().getAssignmentById(assignmentId)!!.isCompleted)
            assertTrue(db.pointApprovalDao().getAllApprovals().first().isEmpty())

            assignmentRepo.completeAssignment(assignmentId) { points, title ->
                callbackCount++
                pointRepo.checkIn(
                    bizKey = "ASSIGN:$assignmentId:2026-11-25",
                    actionType = "ASSIGNMENT",
                    description = "完成布置任务: $title",
                    points = points,
                    date = "2026-11-25"
                )
            }

            val completed = db.dailyAssignmentDao().getAssignmentById(assignmentId)!!
            assertTrue(completed.isCompleted)
            assertNotNull(completed.completedAt)
            assertEquals(2, callbackCount)
            val approvals = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(1, approvals.size)
            assertEquals(5, approvals.single().requestedPoints)

            assignmentRepo.completeAssignment(assignmentId) { _, _ -> error("completed assignment must not retry") }
            assertEquals(2, callbackCount)
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    private suspend fun insertTask(
        db: com.hstudy.app.data.local.AppDatabase,
        frequency: TaskFrequency,
        points: Int = 5,
        bonusPoints: Int = 0
    ): TaskEntity {
        val id = db.taskDao().insert(
            TaskEntity(
                title = "${frequency.name} task",
                category = TaskCategory.BASIC,
                points = points,
                bonusPoints = bonusPoints,
                frequency = frequency
            )
        )
        return db.taskDao().getTaskById(id)!!
    }

    private fun clockFor(date: String): FixedAppClock =
        FixedAppClock(LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12L * 60 * 60 * 1000)
}

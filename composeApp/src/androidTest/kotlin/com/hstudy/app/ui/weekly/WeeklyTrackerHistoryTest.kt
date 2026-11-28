package com.hstudy.app.ui.weekly

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.data.repository.ReportRepository
import com.hstudy.app.data.repository.TaskRepository
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import com.hstudy.app.ui.habit.ChildGoalTrackerScreen
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.WindowSizeClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyTrackerHistoryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun weeklyHeaderAndDailySummary_followOneAnchorAcrossWeekBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val sunday = LocalDate(2026, 11, 29)
        val monday = LocalDate(2026, 11, 30)
        try {
            runBlocking {
                val taskId = db.taskDao().insert(
                    TaskEntity(title = "历史任务", category = TaskCategory.BASIC, points = 2)
                )
                val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
                val task = db.taskDao().getTaskById(taskId)!!
                insertAwardedTask(db, task, sunday, 2)
                insertBonus(db, monday, 3)
                db.pointDao().insertPointRecord(
                    PointRecordEntity(points = -4, type = PointRecordType.PENALTY, date = millis(monday))
                )
                val pending = db.taskDao().insert(
                    TaskEntity(title = "待审", category = TaskCategory.BASIC, points = 7)
                )
                pointRepo.checkInForTask(
                    db.taskDao().getTaskById(pending)!!, monday.toString(), clockFor(monday)
                )
                val rejected = db.taskDao().insert(
                    TaskEntity(title = "被拒", category = TaskCategory.BASIC, points = 9)
                )
                pointRepo.checkInForTask(
                    db.taskDao().getTaskById(rejected)!!, monday.toString(), clockFor(monday)
                )
                pointRepo.rejectApproval(
                    db.pointApprovalDao().getAllApprovals().first().single { it.taskId == rejected }.id
                )
                db.taskDao().update(task.copy(points = 8))
                db.taskDao().softDelete(task.id)
            }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val taskRepo = TaskRepository(db.taskDao(), roomTransactionRunner(db))
            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            val today = mutableStateOf(sunday)

            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH
                    ) {
                        WeeklyTrackerScreen(
                            onNavigateBack = {},
                            isParentView = true,
                            windowSizeClass = WindowSizeClass(),
                            pointRepository = pointRepo,
                            taskRepository = taskRepo,
                            reportRepository = reportRepo,
                            today = today.value
                        )
                    }
                }
            }
            compose.waitForIdle()
            fun scrollToWeeklyPoint(date: String) {
                compose.waitUntil(timeoutMillis = 15_000) {
                    compose.onAllNodes(hasTestTag("weekly-points-$date")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(
                    hasScrollAction() and hasAnyDescendant(hasTestTag("weekly-points-total"))
                ).performScrollToNode(hasTestTag("weekly-points-$date"))
                compose.onNode(
                    hasScrollAction() and
                        hasAnyDescendant(hasTestTag("weekly-points-$date")) and
                        !hasAnyDescendant(hasTestTag("weekly-points-total"))
                ).performScrollToNode(hasTestTag("weekly-points-$date"))
                compose.waitForIdle()
            }
            fun assertWeeklyPoint(date: String, expected: String) {
                compose.waitUntil(timeoutMillis = 15_000) {
                    compose.onAllNodes(
                        hasText(expected) and hasAnyAncestor(hasTestTag("weekly-points-$date"))
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNode(
                    hasText(expected) and hasAnyAncestor(hasTestTag("weekly-points-$date"))
                ).assertTextEquals(expected)
            }
            fun awaitWeeklyTotal(expected: String) {
                compose.waitUntil(timeoutMillis = 15_000) {
                    compose.onAllNodes(
                        hasTestTag("weekly-points-total") and hasText(expected)
                    ).fetchSemanticsNodes().isNotEmpty()
                }
            }
            awaitWeeklyTotal("+2")
            compose.onNodeWithTag("weekly-points-total").assertTextEquals("+2")
            scrollToWeeklyPoint("2026-11-29")
            assertWeeklyPoint("2026-11-29", "+2")
            compose.onNode(hasTestTag("weekly-points-2026-11-30")).assertDoesNotExist()

            compose.runOnIdle { today.value = monday }
            compose.waitForIdle()
            awaitWeeklyTotal("+3")
            compose.onNodeWithTag("weekly-points-total").assertTextEquals("+3")
            compose.onNode(hasTestTag("weekly-points-2026-11-29")).assertDoesNotExist()
            scrollToWeeklyPoint("2026-11-30")
            assertWeeklyPoint("2026-11-30", "+3")
        } finally {
            db.close()
        }
    }

    @Test
    fun goalTracker_displaysClampedSnapshotRateAfterTaskDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val today = LocalDate(2026, 11, 25)
        try {
            runBlocking {
                val ids = (1..3).map { index ->
                    db.taskDao().insert(
                        TaskEntity(title = "任务$index", category = TaskCategory.BASIC, points = 2)
                    )
                }
                db.dailyTaskSnapshotDao().upsert(
                    DailyTaskSnapshotEntity(
                        date = today.toString(),
                        taskIdsJson = ids.joinToString(",", "[", "]"),
                        totalCount = 3,
                        createdAt = millis(today)
                    )
                )
                val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
                ids.forEach { id ->
                    pointRepo.checkInForTask(db.taskDao().getTaskById(id)!!, today.toString(), clockFor(today))
                }
                pointRepo.approveAllAndAward()
                db.taskDao().softDelete(ids.last())
            }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val taskRepo = TaskRepository(db.taskDao(), roomTransactionRunner(db))
            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH
                    ) {
                        ChildGoalTrackerScreen(
                            onNavigateBack = {},
                            windowSizeClass = WindowSizeClass(),
                            pointRepository = pointRepo,
                            reportRepository = reportRepo,
                            today = today
                        )
                    }
                }
            }
            compose.waitForIdle()
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText("100%").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("100%").assertExists()
            compose.onNodeWithText(AppStringsZh.childGoalTracker.tasksCompleted(3, 3)).assertExists()
            compose.onNodeWithText(AppStringsZh.childGoalTracker.completionRateSourceLabel(true)).assertExists()
        } finally {
            db.close()
        }
    }

    @Test
    fun goalTracker_labelsLegacyFallbackAndClampsRate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val today = LocalDate(2026, 11, 25)
        try {
            runBlocking {
                val ids = (1..3).map { index ->
                    db.taskDao().insert(
                        TaskEntity(title = "旧任务$index", category = TaskCategory.BASIC, points = 1)
                    )
                }
                val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
                ids.forEach { id ->
                    pointRepo.checkInForTask(db.taskDao().getTaskById(id)!!, today.toString(), clockFor(today))
                }
                pointRepo.approveAllAndAward()
                db.taskDao().softDelete(ids.last())
            }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val taskRepo = TaskRepository(db.taskDao(), roomTransactionRunner(db))
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(),
                clockFor(today.plus(1, DateTimeUnit.DAY))
            )
            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH
                    ) {
                        ChildGoalTrackerScreen(
                            onNavigateBack = {},
                            windowSizeClass = WindowSizeClass(),
                            pointRepository = pointRepo,
                            reportRepository = reportRepo,
                            today = today
                        )
                    }
                }
            }
            compose.waitForIdle()
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText("100%").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("100%").assertExists()
            compose.onNodeWithText(AppStringsZh.childGoalTracker.tasksCompleted(2, 2)).assertExists()
            compose.onNodeWithText(AppStringsZh.childGoalTracker.completionRateSourceLabel(false)).assertExists()
        } finally {
            db.close()
        }
    }

    private suspend fun insertAwardedTask(
        db: com.hstudy.app.data.local.AppDatabase,
        task: TaskEntity,
        date: LocalDate,
        points: Int
    ) {
        val approvalId = db.pointApprovalDao().insert(
            com.hstudy.app.data.local.entity.PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = task.title,
                requestedPoints = points,
                status = com.hstudy.app.data.local.entity.ApprovalStatus.APPROVED,
                taskId = task.id,
                bizKey = "TASK:${task.id}:$date",
                date = date.toString(),
                frequencyPeriod = date.toString()
            )
        )
        val recordId = db.pointDao().insertPointRecord(
            PointRecordEntity(
                taskId = task.id,
                points = points,
                type = PointRecordType.TASK_COMPLETE,
                date = noonMillis(date),
                approvalId = approvalId
            )
        )
        db.pointApprovalDao().update(
            db.pointApprovalDao().getById(approvalId)!!.copy(awardedRecordId = recordId)
        )
        db.pointDao().insertCompletion(
            com.hstudy.app.data.local.entity.TaskCompletionEntity(
                taskId = task.id,
                completedAt = noonMillis(date),
                pointsEarned = points,
                date = date.toString(),
                frequencyPeriod = date.toString()
            )
        )
    }

    private suspend fun insertBonus(
        db: com.hstudy.app.data.local.AppDatabase,
        date: LocalDate,
        points: Int
    ) {
        val approvalId = db.pointApprovalDao().insert(
            com.hstudy.app.data.local.entity.PointApprovalEntity(
                actionType = "OTHER",
                description = "bonus",
                requestedPoints = points,
                status = com.hstudy.app.data.local.entity.ApprovalStatus.APPROVED,
                date = date.toString()
            )
        )
        db.pointDao().insertPointRecord(
            PointRecordEntity(
                points = points,
                type = PointRecordType.BONUS,
                date = millis(date),
                approvalId = approvalId
            )
        )
    }

    private fun clockFor(date: LocalDate): FixedAppClock =
        FixedAppClock(noonMillis(date))

    private fun noonMillis(date: LocalDate): Long =
        date.atTime(12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun millis(date: LocalDate): Long =
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

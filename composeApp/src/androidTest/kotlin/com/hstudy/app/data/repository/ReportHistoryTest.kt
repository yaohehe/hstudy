package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.DailyTaskSnapshotDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.DailyTaskSnapshotEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportHistoryTest {

    @Test
    fun historicalTaskPoints_afterTaskEditAndSoftDelete_keepAwardedSnapshot() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val task = insertTask(db, "历史任务", points = 2)
            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            val monday = LocalDate(2026, 11, 23)

            insertAwardedTask(db, task, monday, points = 2)
            db.taskDao().update(task.copy(points = 5))
            db.taskDao().softDelete(task.id)

            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 2, awards = 1)),
                reportRepo.historicalTaskStats(millis(monday)..millis(monday.plusDays(7))).first()
            )
            assertEquals(
                mapOf(task.id to 2),
                reportRepo.historicalTaskPoints(millis(monday)..millis(monday.plusDays(7))).first()
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun lifetimeEarnedPoints_excludeRefundsAndOtherNonIncomePositiveRecords() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            db.pointDao().insertPointRecord(PointRecordEntity(points = 2, type = PointRecordType.TASK_COMPLETE))
            db.pointDao().insertPointRecord(PointRecordEntity(points = 3, type = PointRecordType.BONUS))
            db.pointDao().insertPointRecord(PointRecordEntity(points = 4, type = PointRecordType.ADJUSTMENT))
            assertEquals(9, db.pointDao().getLifetimeEarnedPoints().first())

            db.pointDao().insertPointRecord(
                PointRecordEntity(points = 7, type = PointRecordType.REWARD_EXCHANGE, note = "wish_refund:1")
            )
            db.pointDao().insertPointRecord(PointRecordEntity(points = 11, type = PointRecordType.REVERSAL))

            assertEquals(9, db.pointDao().getLifetimeEarnedPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun completionRate_usesSnapshotAndApprovedCompletions_afterTaskDeletion() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val tasks = (1..3).map { insertTask(db, "任务$it", points = 2) }
            db.dailyTaskSnapshotDao().upsert(
                DailyTaskSnapshotEntity(
                    date = date.toString(),
                    taskIdsJson = tasks.joinToString(",", "[", "]") { it.id.toString() },
                    totalCount = 3,
                    createdAt = millis(date)
                )
            )
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            tasks.forEach { pointRepo.checkInForTask(it, date.toString(), clockFor(date)) }
            pointRepo.approveAllAndAward()
            db.taskDao().softDelete(tasks.last().id)

            // A fourth pending completion and a rejected completion are not awarded history.
            val pending = insertTask(db, "待审", points = 9)
            pointRepo.checkInForTask(pending, date.toString(), clockFor(date))
            val rejected = insertTask(db, "被拒", points = 8)
            pointRepo.checkInForTask(rejected, date.toString(), clockFor(date))
            pointRepo.rejectApproval(db.pointApprovalDao().getAllApprovals().first().maxBy { it.id }.id)

            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            val report = reportRepo.completionRateReport(date).first()
            assertEquals(100, report.rate)
            assertEquals(3, report.completed)
            assertEquals(3, report.total)
            assertEquals(CompletionRateSource.DAILY_SNAPSHOT, report.source)
            assertEquals(100, reportRepo.completionRate(date).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun completionRate_legacyFallback_isLabelledAndClamped() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val tasks = (1..3).map { insertTask(db, "旧任务$it", points = 1) }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            tasks.forEach { pointRepo.checkInForTask(it, date.toString(), clockFor(date)) }
            pointRepo.approveAllAndAward()
            db.taskDao().softDelete(tasks.last().id)

            val report = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(date.plus(1, DateTimeUnit.DAY))
            ).completionRateReport(date).first()
            assertEquals(100, report.rate)
            assertEquals(2, report.completed)
            assertEquals(2, report.total)
            assertEquals(CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK, report.source)
            assertTrue(report.rate in 0..100)
        } finally {
            db.close()
        }
    }

    @Test
    fun historicalTaskStats_revokedBonusReversalDoesNotAffectTaskButRevokedTaskReversalNets() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val task = insertTask(db, "任务与额外奖励", points = 5)
            insertAwardedTask(db, task, date, points = 5)
            val taskApproval = db.pointApprovalDao().getAllApprovals().first()
                .single { it.taskId == task.id && it.actionType == "TASK_COMPLETE" }
            val bonusApprovalId = db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = "OTHER",
                    description = "task-linked bonus",
                    requestedPoints = 7,
                    status = ApprovalStatus.APPROVED,
                    taskId = task.id,
                    date = date.toString()
                )
            )
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = 7,
                    type = PointRecordType.BONUS,
                    date = millis(date),
                    approvalId = bonusApprovalId
                )
            )
            db.pointApprovalDao().update(
                db.pointApprovalDao().getById(bonusApprovalId)!!.copy(status = ApprovalStatus.REVOKED)
            )
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -7,
                    type = PointRecordType.REVERSAL,
                    date = millis(date),
                    approvalId = bonusApprovalId
                )
            )
            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            val range = millis(date)..millis(date.plusDays(1))

            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 5, awards = 1)),
                report.historicalTaskStats(range).first()
            )
            assertEquals(mapOf(date to 5), report.dailyAwardedPoints(date, date).first())

            db.pointApprovalDao().update(taskApproval.copy(status = ApprovalStatus.REVOKED))
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -5,
                    type = PointRecordType.REVERSAL,
                    date = millis(date),
                    approvalId = taskApproval.id
                )
            )
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(range).first()
            )
            assertEquals(mapOf(date to 0), report.dailyAwardedPoints(date, date).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun awardedIncome_nullApprovalIdBacklinkMustBeApprovedOrRevoked_butAdjustmentIsUnconditional() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val task = insertTask(db, "审批回链", points = 2)
            insertBacklinkedRecord(db, task.id, 2, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.APPROVED)
            insertBacklinkedRecord(db, task.id, 100, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.PENDING)
            insertBacklinkedRecord(db, task.id, 200, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.REJECTED)
            insertBacklinkedRecord(db, task.id, 300, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.CANCELLED)
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = 3,
                    type = PointRecordType.TASK_COMPLETE,
                    date = millis(date)
                )
            )
            insertRecord(db, 4, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.APPROVED, task.id)
            insertRecord(db, 50, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.PENDING, task.id)
            insertRecord(db, 60, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.CANCELLED, task.id)
            insertBacklinkedRecord(db, null, 7, PointRecordType.ADJUSTMENT, date, ApprovalStatus.PENDING)

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 9, awards = 3)),
                report.historicalTaskStats(millis(date)..millis(date.plusDays(1))).first()
            )
            assertEquals(mapOf(date to 16), report.dailyAwardedPoints(date, date).first())
            assertEquals(16, db.pointDao().getLifetimeEarnedPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun awardedIncome_revokedDirectAndBacklinkedTaskAwardsNetToZeroInHistoryDayAndWeek() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val task = insertTask(db, "撤销净额", points = 5)
            insertAwardedTask(db, task, date, points = 5)
            val directApproval = db.pointApprovalDao().getAllApprovals().first()
                .single { it.taskId == task.id && it.actionType == "TASK_COMPLETE" }
            val backlinkedApprovalId = insertBacklinkedRecord(
                db, task.id, 3, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.APPROVED
            )
            db.pointApprovalDao().update(directApproval.copy(status = ApprovalStatus.REVOKED))
            db.pointApprovalDao().update(
                db.pointApprovalDao().getById(backlinkedApprovalId)!!.copy(status = ApprovalStatus.REVOKED)
            )
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -5,
                    type = PointRecordType.REVERSAL,
                    date = millis(date),
                    approvalId = directApproval.id
                )
            )
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -3,
                    type = PointRecordType.REVERSAL,
                    date = millis(date),
                    approvalId = backlinkedApprovalId
                )
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            val range = millis(date)..millis(date.plusDays(1))
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 2)),
                report.historicalTaskStats(range).first()
            )
            assertEquals(mapOf(date to 0), report.dailyAwardedPoints(date, date).first())
            assertEquals(mapOf(date to 0), report.weeklyPoints(date).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun historicalAndDailyIncome_crossDayReversalNetsInOriginalDayNotReversalDay() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val awardDate = LocalDate(2026, 11, 23)
            val reversalDate = LocalDate(2026, 11, 24)
            val task = insertTask(db, "跨日撤销", points = 5)
            insertAwardedTask(db, task, awardDate, points = 5)
            val approval = db.pointApprovalDao().getAllApprovals().first().single { it.taskId == task.id }
            db.pointApprovalDao().update(approval.copy(status = ApprovalStatus.REVOKED))
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -5,
                    type = PointRecordType.REVERSAL,
                    date = noonMillis(reversalDate),
                    approvalId = approval.id
                )
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(awardDate.plusDays(1))).first()
            )
            assertEquals(
                emptyMap<Long, HistoricalTaskStats>(),
                report.historicalTaskStats(millis(reversalDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(mapOf(awardDate to 0), report.dailyAwardedPoints(awardDate, awardDate).first())
            assertEquals(emptyMap<LocalDate, Int>(), report.dailyAwardedPoints(reversalDate, reversalDate).first())
            assertEquals(mapOf(awardDate to 0), report.weeklyPoints(awardDate).first())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(mapOf(awardDate to 0), report.dailyAwardedPoints(awardDate, reversalDate).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun historicalAndDailyIncome_crossWeekReversalNetsInOriginalWeekNotReversalWeek() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val awardDate = LocalDate(2026, 11, 29)
            val reversalDate = LocalDate(2026, 11, 30)
            val task = insertTask(db, "跨周撤销", points = 5)
            insertAwardedTask(db, task, awardDate, points = 5)
            val approval = db.pointApprovalDao().getAllApprovals().first().single { it.taskId == task.id }
            db.pointApprovalDao().update(approval.copy(status = ApprovalStatus.REVOKED))
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -5,
                    type = PointRecordType.REVERSAL,
                    date = noonMillis(reversalDate),
                    approvalId = approval.id
                )
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(awardDate.plusDays(1))).first()
            )
            assertEquals(
                emptyMap<Long, HistoricalTaskStats>(),
                report.historicalTaskStats(millis(reversalDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(mapOf(awardDate to 0), report.dailyAwardedPoints(awardDate, awardDate).first())
            assertEquals(emptyMap<LocalDate, Int>(), report.dailyAwardedPoints(reversalDate, reversalDate).first())
            assertEquals(mapOf(awardDate to 0), report.weeklyPoints(awardDate).first())
            assertEquals(emptyMap<LocalDate, Int>(), report.weeklyPoints(reversalDate).first())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(mapOf(awardDate to 0), report.dailyAwardedPoints(awardDate, reversalDate).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun historicalAndDailyIncome_reversalWithoutOriginalRecordLinkUsesItsOwnDate() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val awardDate = LocalDate(2026, 11, 23)
            val reversalDate = LocalDate(2026, 11, 24)
            val task = insertTask(db, "无原记录回链", points = 5)
            insertRecord(db, 5, PointRecordType.TASK_COMPLETE, awardDate, ApprovalStatus.APPROVED, task.id)
            val approval = db.pointApprovalDao().getAllApprovals().first().single { it.taskId == task.id }
            db.pointApprovalDao().update(approval.copy(status = ApprovalStatus.REVOKED))
            db.pointDao().insertPointRecord(
                PointRecordEntity(
                    taskId = task.id,
                    points = -5,
                    type = PointRecordType.REVERSAL,
                    date = noonMillis(reversalDate),
                    approvalId = approval.id
                )
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 5, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(awardDate.plusDays(1))).first()
            )
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = -5, awards = 0)),
                report.historicalTaskStats(millis(reversalDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(mapOf(awardDate to 5), report.dailyAwardedPoints(awardDate, awardDate).first())
            assertEquals(mapOf(reversalDate to -5), report.dailyAwardedPoints(reversalDate, reversalDate).first())
            assertEquals(
                mapOf(task.id to HistoricalTaskStats(points = 0, awards = 1)),
                report.historicalTaskStats(millis(awardDate)..millis(reversalDate.plusDays(1))).first()
            )
            assertEquals(
                mapOf(awardDate to 5, reversalDate to -5),
                report.dailyAwardedPoints(awardDate, reversalDate).first()
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun dailyAwardedPoints_reversalsRequireRevokedApprovalOrLegacyNullApproval() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            insertRecord(db, 10, PointRecordType.TASK_COMPLETE, date, ApprovalStatus.APPROVED)
            val approvedApprovalId = db.pointApprovalDao().getAllApprovals().first().single().id
            listOf(
                approvedApprovalId to ApprovalStatus.APPROVED,
                null to ApprovalStatus.PENDING,
                null to ApprovalStatus.REJECTED,
                null to ApprovalStatus.CANCELLED
            ).forEach { (linkedApprovalId, status) ->
                val approvalId = linkedApprovalId ?: db.pointApprovalDao().insert(
                    com.hstudy.app.data.local.entity.PointApprovalEntity(
                        actionType = "TASK_COMPLETE",
                        description = "illegal reversal $status",
                        requestedPoints = 100,
                        status = status,
                        date = date.toString()
                    )
                )
                db.pointDao().insertPointRecord(
                    PointRecordEntity(
                        points = -100,
                        type = PointRecordType.REVERSAL,
                        date = millis(date),
                        approvalId = approvalId
                    )
                )
            }
            db.pointDao().insertPointRecord(
                PointRecordEntity(points = 2, type = PointRecordType.TASK_COMPLETE, date = millis(date))
            )
            db.pointDao().insertPointRecord(
                PointRecordEntity(points = -2, type = PointRecordType.REVERSAL, date = millis(date))
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(mapOf(date to 10), report.dailyAwardedPoints(date, date).first())
            assertEquals(mapOf(date to 10), report.weeklyPoints(date).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun approvedCompletions_canonicalWeeklyMonthlyOnceFrequenciesAreCounted() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val tasks = listOf(
                TaskFrequency.WEEKLY,
                TaskFrequency.MONTHLY,
                TaskFrequency.ONCE
            ).map { frequency ->
                db.taskDao().insert(
                    TaskEntity(
                        title = frequency.name,
                        category = TaskCategory.BASIC,
                        points = 2,
                        frequency = frequency
                    )
                )
            }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            tasks.forEach { id ->
                pointRepo.checkInForTask(db.taskDao().getTaskById(id)!!, date.toString(), clockFor(date))
            }
            pointRepo.approveAllAndAward()
            val taskIds = tasks.toSet()
            db.dailyTaskSnapshotDao().upsert(
                DailyTaskSnapshotEntity(
                    date = date.toString(),
                    taskIdsJson = taskIds.joinToString(",", "[", "]"),
                    totalCount = taskIds.size,
                    createdAt = millis(date)
                )
            )

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(date to taskIds),
                report.approvedCompletionsBetween(date, date).first()
            )
            assertEquals(
                mapOf(
                    date to CompletionRateReport(
                        rate = 100,
                        completed = 3,
                        total = 3,
                        source = CompletionRateSource.DAILY_SNAPSHOT
                    )
                ),
                report.completionRateReports(date, date).first()
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun approvedCompletions_duplicateLegacyApprovalsAndCompletionsCountOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val task = insertTask(db, "重复旧数据", points = 2)
            repeat(2) {
                db.pointDao().insertCompletion(
                    TaskCompletionEntity(
                        taskId = task.id,
                        completedAt = millis(date),
                        pointsEarned = 2,
                        date = date.toString(),
                        frequencyPeriod = null
                    )
                )
                val approvalId = db.pointApprovalDao().insert(
                    com.hstudy.app.data.local.entity.PointApprovalEntity(
                        actionType = "TASK_COMPLETE",
                        description = "legacy duplicate",
                        requestedPoints = 2,
                        status = ApprovalStatus.APPROVED,
                        taskId = task.id,
                        bizKey = "legacy-duplicate-$it",
                        date = date.toString(),
                        frequencyPeriod = null
                    )
                )
                val recordId = db.pointDao().insertPointRecord(
                    PointRecordEntity(
                        taskId = task.id,
                        points = 2,
                        type = PointRecordType.TASK_COMPLETE,
                        date = millis(date),
                        approvalId = approvalId
                    )
                )
                db.pointApprovalDao().update(
                    db.pointApprovalDao().getById(approvalId)!!.copy(awardedRecordId = recordId)
                )
            }

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(date to setOf(task.id)),
                report.approvedCompletionsBetween(date, date).first()
            )
            assertEquals(1, report.completionRateReport(date).first().completed)
        } finally {
            db.close()
        }
    }

    @Test
    fun completionRateReports_rangeMixesSnapshotAndIntersectedFallback() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val first = LocalDate(2026, 11, 23)
            val second = LocalDate(2026, 11, 24)
            val third = LocalDate(2026, 11, 25)
            val tasks = (1..2).map { insertTask(db, "区间任务$it", points = 1) }
            db.dailyTaskSnapshotDao().upsert(
                DailyTaskSnapshotEntity(
                    date = first.toString(),
                    taskIdsJson = tasks.joinToString(",", "[", "]") { it.id.toString() },
                    totalCount = 2,
                    createdAt = millis(first)
                )
            )
            db.dailyTaskSnapshotDao().upsert(
                DailyTaskSnapshotEntity(
                    date = third.toString(),
                    taskIdsJson = "[${tasks.first().id}]",
                    totalCount = 1,
                    createdAt = millis(third)
                )
            )
            insertAwardedTask(db, tasks.first(), first, points = 1)
            insertAwardedTask(db, tasks.last(), second, points = 1)

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(
                    first to CompletionRateReport(50, 1, 2, CompletionRateSource.DAILY_SNAPSHOT),
                    second to CompletionRateReport(50, 1, 2, CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK),
                    third to CompletionRateReport(0, 0, 1, CompletionRateSource.DAILY_SNAPSHOT)
                ),
                report.completionRateReports(first, third).first()
            )
            assertEquals(emptyMap<LocalDate, CompletionRateReport>(), report.completionRateReports(third, first).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun weeklyPoints_matchAwardedLedger_andExcludePendingRejectedPenaltyAndRefund() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val sunday = LocalDate(2026, 11, 29)
            val monday = LocalDate(2026, 11, 30)
            val task = insertTask(db, "周日任务", points = 2)
            insertAwardedTask(db, task, sunday, points = 2)
            insertRecord(db, 5, PointRecordType.BONUS, monday, ApprovalStatus.APPROVED)
            insertRecord(db, 3, PointRecordType.ADJUSTMENT, monday)
            insertRecord(db, 6, PointRecordType.REWARD_EXCHANGE, monday)
            db.pointDao().insertPointRecord(
                PointRecordEntity(points = -4, type = PointRecordType.PENALTY, date = millis(monday))
            )
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val pending = insertTask(db, "待审任务", points = 9)
            pointRepo.checkInForTask(pending, monday.toString(), clockFor(monday))
            val rejected = insertTask(db, "被拒任务", points = 8)
            pointRepo.checkInForTask(rejected, monday.toString(), clockFor(monday))
            val rejectedApproval = db.pointApprovalDao().getAllApprovals().first()
                .single { it.taskId == rejected.id }
            pointRepo.rejectApproval(rejectedApproval.id)

            val report = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao())
            assertEquals(
                mapOf(sunday to 2, monday to 8),
                report.weeklyPoints(sunday).first()
            )
            assertEquals(mapOf(monday to 8), report.weeklyPoints(monday).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun completionRate_withoutSnapshotOrTasks_isZero() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(date.plus(1, DateTimeUnit.DAY))
            )
            val report = reportRepo.completionRateReport(date).first()
            assertEquals(0, report.rate)
            assertEquals(CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK, report.source)
        } finally {
            db.close()
        }
    }

    @Test
    fun activeTaskObservationCapturesFirstDailySnapshotAndNeverRewritesIt() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val first = insertTask(db, "首个任务", points = 2)
            val second = insertTask(db, "第二个任务", points = 2)
            val clock = clockFor(date)
            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clock)
            val taskRepo = TaskRepository(
                db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), reportRepo.snapshotWriter
            )

            assertEquals(2, taskRepo.getAllActiveTasks().first().size)
            val original = db.dailyTaskSnapshotDao().getByDate(date.toString()).first()!!
            assertEquals(setOf(first.id, second.id), parseIds(original.taskIdsJson))
            assertEquals(2, original.totalCount)
            assertEquals(clock.nowMillis(), original.createdAt)

            insertTask(db, "后来添加", points = 3)
            assertEquals(3, taskRepo.getAllActiveTasks().first().size)
            assertEquals(original, db.dailyTaskSnapshotDao().getByDate(date.toString()).first())

            db.taskDao().softDelete(first.id)
            assertEquals(2, taskRepo.getAllActiveTasks().first().size)
            assertEquals(original, db.dailyTaskSnapshotDao().getByDate(date.toString()).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun ordinaryEmptyObservationDoesNotCreateSnapshot() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(date)
            )
            val taskRepo = TaskRepository(
                db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), reportRepo.snapshotWriter
            )

            assertTrue(taskRepo.getAllActiveTasks().first().isEmpty())
            assertNull(db.dailyTaskSnapshotDao().getByDate(date.toString()).first())

            val initialized = insertTask(db, "初始化任务", points = 2)
            assertEquals(1, taskRepo.getAllActiveTasks().first().size)
            val snapshot = db.dailyTaskSnapshotDao().getByDate(date.toString()).first()!!
            assertEquals(setOf(initialized.id), parseIds(snapshot.taskIdsJson))
            assertEquals(1, snapshot.totalCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun preexistingZeroSnapshotRemainsImmutableAfterEmptyAndNonEmptyObservations() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val existing = DailyTaskSnapshotEntity(
                date = date.toString(),
                taskIdsJson = "[]",
                totalCount = 0,
                createdAt = 123L,
            )
            db.dailyTaskSnapshotDao().upsert(existing)
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(date)
            )
            val taskRepo = TaskRepository(
                db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), reportRepo.snapshotWriter
            )

            assertTrue(taskRepo.getAllActiveTasks().first().isEmpty())
            assertEquals(existing, db.dailyTaskSnapshotDao().getByDate(date.toString()).first())

            insertTask(db, "后来任务", points = 2)
            assertEquals(1, taskRepo.getAllActiveTasks().first().size)
            assertEquals(existing, db.dailyTaskSnapshotDao().getByDate(date.toString()).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun categoryObservationNeverCapturesPartialActiveDenominator() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val basic = db.taskDao().insert(
                TaskEntity(title = "基础", category = TaskCategory.BASIC, points = 2)
            )
            val habit = db.taskDao().insert(
                TaskEntity(title = "习惯", category = TaskCategory.HABIT, points = 2)
            )
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(date)
            )
            val taskRepo = TaskRepository(
                db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), reportRepo.snapshotWriter
            )

            assertEquals(1, taskRepo.getTasksByCategory(TaskCategory.BASIC).first().size)
            assertNull(db.dailyTaskSnapshotDao().getByDate(date.toString()).first())

            assertEquals(2, taskRepo.getAllActiveTasks().first().size)
            val snapshot = db.dailyTaskSnapshotDao().getByDate(date.toString()).first()!!
            assertEquals(setOf(basic, habit), parseIds(snapshot.taskIdsJson))
            assertEquals(2, snapshot.totalCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun defaultTaskInitializationProducesDailySnapshot() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val date = LocalDate(2026, 11, 25)
            val clock = clockFor(date)
            val reportRepo = ReportRepository(db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clock)
            val taskRepo = TaskRepository(
                db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), reportRepo.snapshotWriter
            )

            taskRepo.ensureDefaultTasks()

            val snapshot = db.dailyTaskSnapshotDao().getByDate(date.toString()).first()!!
            val active = db.taskDao().getAllActiveTasksSnapshot()
            assertTrue(active.isNotEmpty())
            assertEquals(active.map { it.id }.toSet(), parseIds(snapshot.taskIdsJson))
            assertEquals(active.size, snapshot.totalCount)
            assertEquals(clock.nowMillis(), snapshot.createdAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun snapshotFailureRollsBackAddUpdateDeleteTemplateAndDefaultInitialization() = runBlocking {
        listOf("add", "update", "delete", "template", "default").forEach { operation ->
            val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            try {
                val date = LocalDate(2026, 11, 25)
                val failingSnapshotDao = object : DailyTaskSnapshotDao by db.dailyTaskSnapshotDao() {
                    override suspend fun insertIfAbsent(snapshot: DailyTaskSnapshotEntity) =
                        error("injected snapshot failure")
                }
                val writer = DailyTaskSnapshotWriter(db.taskDao(), failingSnapshotDao, clockFor(date))
                val repository = TaskRepository(
                    db.taskDao(), db.dailyScheduleDao(), roomTransactionRunner(db), writer
                )
                val original = insertTask(db, "原任务", points = 2)
                val remaining = insertTask(db, "保留任务", points = 2)
                val scheduleId = db.dailyScheduleDao().insert(
                    com.hstudy.app.data.local.entity.DailyScheduleEntity(
                        time = "16:00", title = "原日程", linkedTaskId = original.id
                    )
                )

                val failure = when (operation) {
                    "add" -> runCatching {
                        repository.addTask(
                            TaskEntity(title = "新任务", category = TaskCategory.BASIC, points = 3)
                        )
                    }.exceptionOrNull()
                    "update" -> runCatching {
                        repository.updateTask(original.copy(title = "已修改", points = 9))
                    }.exceptionOrNull()
                    "delete" -> runCatching { repository.deleteTask(original.id) }.exceptionOrNull()
                    "template" -> runCatching {
                        repository.applyTemplate(
                            TaskTemplate(
                                id = "test", name = "测试", description = "", emoji = "", ageRange = "",
                                tasks = listOf(
                                    TaskEntity(title = "模板任务", category = TaskCategory.BASIC, points = 4)
                                )
                            )
                        )
                    }.exceptionOrNull()
                    "default" -> {
                        db.taskDao().softDelete(original.id)
                        db.taskDao().softDelete(remaining.id)
                        db.dailyScheduleDao().unlinkTask(original.id)
                        runCatching { repository.initDefaultTasks() }.exceptionOrNull()
                    }
                    else -> error("unknown operation")
                }

                assertEquals(operation, "injected snapshot failure", failure?.message)
                when (operation) {
                    "add" -> assertEquals(listOf(original, remaining), db.taskDao().getAllActiveTasksSnapshot())
                    "update" -> assertEquals(original, db.taskDao().getTaskById(original.id))
                    "delete" -> {
                        assertEquals(original, db.taskDao().getTaskById(original.id))
                        assertEquals(remaining, db.taskDao().getTaskById(remaining.id))
                        assertEquals(
                            original.id,
                            db.dailyScheduleDao().getScheduleById(scheduleId)?.linkedTaskId
                        )
                    }
                    "template" -> {
                        assertEquals(listOf(original, remaining), db.taskDao().getAllActiveTasksSnapshot())
                        assertEquals(
                            original.id,
                            db.dailyScheduleDao().getScheduleById(scheduleId)?.linkedTaskId
                        )
                    }
                    "default" -> assertTrue(db.taskDao().getAllActiveTasksSnapshot().isEmpty())
                }
                assertNull(db.dailyTaskSnapshotDao().getByDate(date.toString()).first())
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun todayReportQueryCapturesSnapshotBeforeReadingButHistoricalDatesAreNotBackfilled() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val today = LocalDate(2026, 11, 25)
            val yesterday = today.plus(-1, DateTimeUnit.DAY)
            val task = insertTask(db, "今日任务", points = 2)
            val reportRepo = ReportRepository(
                db.reportDao(), db.dailyTaskSnapshotDao(), db.taskDao(), clockFor(today)
            )

            val todayReport = reportRepo.completionRateReport(today).first()
            assertEquals(CompletionRateSource.DAILY_SNAPSHOT, todayReport.source)
            assertEquals(1, todayReport.total)
            val snapshot = db.dailyTaskSnapshotDao().getByDate(today.toString()).first()!!
            assertEquals(setOf(task.id), parseIds(snapshot.taskIdsJson))

            val historicalReport = reportRepo.completionRateReport(yesterday).first()
            assertEquals(CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK, historicalReport.source)
            assertEquals(null, db.dailyTaskSnapshotDao().getByDate(yesterday.toString()).first())
        } finally {
            db.close()
        }
    }

    private fun parseIds(json: String): Set<Long> =
        kotlinx.serialization.json.Json.decodeFromString<List<Long>>(json).toSet()

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
                status = ApprovalStatus.APPROVED,
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

    private suspend fun insertTask(db: com.hstudy.app.data.local.AppDatabase, title: String, points: Int): TaskEntity {
        val id = db.taskDao().insert(TaskEntity(title = title, category = TaskCategory.BASIC, points = points))
        return db.taskDao().getTaskById(id)!!
    }

    private suspend fun insertBacklinkedRecord(
        db: com.hstudy.app.data.local.AppDatabase,
        taskId: Long?,
        points: Int,
        type: PointRecordType,
        date: LocalDate,
        status: ApprovalStatus
    ): Long {
        val recordId = db.pointDao().insertPointRecord(
            PointRecordEntity(
                taskId = taskId,
                points = points,
                type = type,
                date = millis(date),
                approvalId = null
            )
        )
        return db.pointApprovalDao().insert(
            com.hstudy.app.data.local.entity.PointApprovalEntity(
                actionType = if (type == PointRecordType.TASK_COMPLETE) "TASK_COMPLETE" else "OTHER",
                description = "backlinked record",
                requestedPoints = points,
                status = status,
                taskId = taskId,
                awardedRecordId = recordId,
                date = date.toString()
            )
        )
    }

    private suspend fun insertRecord(
        db: com.hstudy.app.data.local.AppDatabase,
        points: Int,
        type: PointRecordType,
        date: LocalDate,
        status: ApprovalStatus? = null,
        taskId: Long? = null
    ): Long {
        val approvalId = status?.let {
            db.pointApprovalDao().insert(
                com.hstudy.app.data.local.entity.PointApprovalEntity(
                    actionType = if (type == PointRecordType.TASK_COMPLETE) "TASK_COMPLETE" else "BONUS",
                    description = "test",
                    requestedPoints = points,
                    status = it,
                    taskId = taskId,
                    date = date.toString()
                )
            )
        }
        return db.pointDao().insertPointRecord(
            PointRecordEntity(
                taskId = taskId,
                points = points,
                type = type,
                date = noonMillis(date),
                approvalId = approvalId
            )
        )
    }

    private fun clockFor(date: LocalDate): FixedAppClock =
        FixedAppClock(noonMillis(date))

    private fun noonMillis(date: LocalDate): Long =
        date.atTime(12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun millis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
}

package com.hstudy.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyTargetResetTest {

    @Test
    fun resetRejectsPendingTargetApprovalWithoutMutatingOrDuplicatingIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-pending-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework", "reading"))
            preferences.setDailyTargetDone("homework", true)
            assertEquals(
                CheckInResult.CREATED,
                points.checkIn(
                    bizKey = "TARGET:homework:$DATE",
                    actionType = "DAILY_TARGET",
                    description = "完成每日目标：完成作业",
                    points = 2,
                    date = DATE
                )
            )

            val result = repository.reset(DATE, setOf("preview"))

            assertTrue(result.isFailure)
            val blocked = result.exceptionOrNull() as DailyTargetResetBlockedException
            assertEquals(setOf(DailyTargetResetBlocker.PENDING_TARGET_APPROVALS), blocked.blockers)
            assertEquals(setOf("homework", "reading"), preferences.dailyTargets.first())
            assertEquals(setOf("homework"), preferences.dailyTargetsDone.first())
            assertEquals(
                CheckInResult.ALREADY_PENDING,
                points.checkIn(
                    bizKey = "TARGET:homework:$DATE",
                    actionType = "DAILY_TARGET",
                    description = "完成每日目标：完成作业",
                    points = 2,
                    date = DATE
                )
            )
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun resetRejectsClaimedAllCompleteBonusAfterTargetApprovalIsResolved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-bonus-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework"))
            preferences.setDailyTargetDone("homework", true)
            preferences.setDailyTargetBonusDate(DATE)
            points.checkIn(
                bizKey = "TARGET:homework:$DATE",
                actionType = "DAILY_TARGET",
                description = "完成每日目标：完成作业",
                points = 2,
                date = DATE
            )
            points.checkIn(
                bizKey = "TARGET_ALL:$DATE",
                actionType = "DAILY_TARGET",
                description = "每日学习目标全部达成奖励",
                points = 10,
                date = DATE
            )
            points.approveAllAndAward()

            val result = repository.reset(DATE, setOf("reading"))

            assertTrue(result.isFailure)
            val blocked = result.exceptionOrNull() as DailyTargetResetBlockedException
            assertEquals(setOf(DailyTargetResetBlocker.CLAIMED_ALL_COMPLETE_BONUS), blocked.blockers)
            assertEquals(setOf("homework"), preferences.dailyTargets.first())
            assertEquals(setOf("homework"), preferences.dailyTargetsDone.first())
            assertEquals(DATE, preferences.dailyTargetBonusDate.first())
            assertTrue(db.pointApprovalDao().getAllApprovals().first().all { it.status == ApprovalStatus.APPROVED })
            assertEquals(12, points.getTotalPoints().first())
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun resetSucceedsWithoutSameDayPendingOrClaimedBonusAndOldAwardCannotBeReused() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-reset-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework"))
            preferences.setDailyTargetDone("homework", true)
            points.checkIn(
                bizKey = "TARGET:homework:$DATE",
                actionType = "DAILY_TARGET",
                description = "完成每日目标：完成作业",
                points = 2,
                date = DATE
            )
            points.approveAllAndAward()

            val result = repository.reset(DATE, setOf("preview"))

            assertTrue(result.isSuccess)
            assertEquals(setOf("preview"), preferences.dailyTargets.first())
            assertTrue(preferences.dailyTargetsDone.first().isEmpty())
            assertEquals(
                CheckInResult.ALREADY_CREDITED,
                points.checkIn(
                    bizKey = "TARGET:homework:$DATE",
                    actionType = "DAILY_TARGET",
                    description = "完成每日目标：完成作业",
                    points = 2,
                    date = DATE
                )
            )
            assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            assertEquals(2, points.getTotalPoints().first())
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun resetIgnoresPendingApprovalsAndClaimedBonusFromAnotherDate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-other-date-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            val yesterday = "2026-11-24"
            preferences.setDailyTargets(yesterday, setOf("homework"))
            preferences.setDailyTargetBonusDate(yesterday)
            points.checkIn(
                bizKey = "TARGET:homework:$yesterday",
                actionType = "DAILY_TARGET",
                description = "完成每日目标：完成作业",
                points = 2,
                date = yesterday
            )

            assertTrue(repository.reset(DATE, setOf("reading")).isSuccess)
            assertEquals(setOf("reading"), preferences.dailyTargets.first())
            assertTrue(preferences.dailyTargetsDone.first().isEmpty())
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun concurrentCompletionsCreateAllCompleteBonusExactlyOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-race-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework", "reading"))

            val results = coroutineScope {
                listOf(
                    async { repository.setDone(DATE, "homework", true) },
                    async { repository.setDone(DATE, "reading", true) }
                ).awaitAll()
            }

            assertTrue(results.all { it.isSuccess })
            assertEquals(setOf("homework", "reading"), preferences.dailyTargetsDone.first())
            assertEquals(DATE, preferences.dailyTargetBonusDate.first())
            val approvals = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(3, approvals.size)
            assertEquals(1, approvals.count { it.bizKey == "TARGET_ALL:$DATE" && it.requestedPoints == 10 })
            assertEquals(2, approvals.count { it.bizKey.startsWith("TARGET:") && it.requestedPoints == 2 })
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun concurrentCompletionAndResetCannotBothMutateTheSameTargetDate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-reset-race-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework", "reading"))

            val (doneResult, resetResult) = coroutineScope {
                val done = async { repository.setDone(DATE, "homework", true) }
                val reset = async { repository.reset(DATE, setOf("preview")) }
                done.await() to reset.await()
            }

            assertTrue("serialized mutations must reject the stale operation", doneResult.isFailure || resetResult.isFailure)
            if (resetResult.isSuccess) {
                assertEquals(setOf("preview"), preferences.dailyTargets.first())
                assertTrue(preferences.dailyTargetsDone.first().isEmpty())
                assertTrue(doneResult.isFailure)
                assertTrue(db.pointApprovalDao().getAllApprovals().first().isEmpty())
            } else {
                assertEquals(setOf("homework", "reading"), preferences.dailyTargets.first())
                assertEquals(setOf("homework"), preferences.dailyTargetsDone.first())
                assertTrue(doneResult.isSuccess)
                assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            }
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun uncheckingLastCompletedTargetUndoesAllCompleteBonusAndClaim() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-undo-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework"))
            assertTrue(repository.setDone(DATE, "homework", true).isSuccess)
            points.approveAllAndAward()
            assertEquals(12, points.getTotalPoints().first())

            assertTrue(repository.setDone(DATE, "homework", false).isSuccess)

            assertTrue(preferences.dailyTargetsDone.first().isEmpty())
            assertEquals("", preferences.dailyTargetBonusDate.first())
            assertEquals(0, points.getTotalPoints().first())
            assertTrue(db.pointApprovalDao().getAllApprovals().first().all { it.status == ApprovalStatus.REVOKED })
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun repeatedDoneRepairsRealRoomBonusApprovalAfterDoneWasPersisted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-bonus-approval-failure-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        var failBonusInsert = true
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            preferences.setDailyTargets(DATE, setOf("homework"))
            val gatedApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                override suspend fun insert(approval: PointApprovalEntity): Long {
                    if (failBonusInsert && approval.bizKey == "TARGET_ALL:$DATE") {
                        error("injected bonus approval failure")
                    }
                    return db.pointApprovalDao().insert(approval)
                }
            }
            val points = PointRepository(db.pointDao(), gatedApprovalDao, roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)

            assertTrue(repository.setDone(DATE, "homework", true).isFailure)
            assertEquals(setOf("homework"), preferences.dailyTargetsDone.first())
            assertEquals(listOf("TARGET:homework:$DATE"), db.pointApprovalDao().getAllApprovals().first().map { it.bizKey })
            assertNull(preferences.dailyTargetBonusDate.first())

            failBonusInsert = false
            assertTrue(repository.setDone(DATE, "homework", true).isSuccess)
            assertEquals(DATE, preferences.dailyTargetBonusDate.first())
            assertEquals(
                setOf("TARGET:homework:$DATE", "TARGET_ALL:$DATE"),
                db.pointApprovalDao().getAllApprovals().first().map { it.bizKey }.toSet()
            )
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun repeatedDoneRepairsRealDataStoreBonusClaimAfterApprovalWasCreated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-bonus-claim-failure-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        var failClaim = true
        try {
            val delegate = testDataStore(context, storeName)
            val preferences = UserPreferences(object : DataStore<Preferences> by delegate {
                override suspend fun updateData(
                    transform: suspend (Preferences) -> Preferences
                ): Preferences = delegate.updateData { current ->
                    transform(current).also { updated ->
                        if (failClaim && updated[UserPreferences.DAILY_TARGET_BONUS_DATE] == DATE) {
                            error("injected bonus claim failure")
                        }
                    }
                }
            })
            preferences.setDailyTargets(DATE, setOf("homework"))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)

            assertTrue(repository.setDone(DATE, "homework", true).isFailure)
            assertEquals(setOf("homework"), preferences.dailyTargetsDone.first())
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
            assertNull(preferences.dailyTargetBonusDate.first())

            failClaim = false
            assertTrue(repository.setDone(DATE, "homework", true).isSuccess)
            assertTrue(repository.setDone(DATE, "homework", true).isSuccess)
            assertEquals(DATE, preferences.dailyTargetBonusDate.first())
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun repeatedUndoRepairsRealRoomBonusUndoAfterDoneWasPersisted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-bonus-undo-failure-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        var failBonusReviewId: Long? = null
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            preferences.setDailyTargets(DATE, setOf("homework"))
            val gatedApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                override suspend fun cancelPending(id: Long, reviewedAt: Long): Int {
                    if (id == failBonusReviewId) error("injected bonus undo failure")
                    return db.pointApprovalDao().cancelPending(id, reviewedAt)
                }

                override suspend fun updateReview(
                    id: Long,
                    status: ApprovalStatus,
                    awardedRecordId: Long?,
                    reviewedAt: Long
                ): Int {
                    if (id == failBonusReviewId) error("injected bonus undo failure")
                    return db.pointApprovalDao().updateReview(id, status, awardedRecordId, reviewedAt)
                }
            }
            val points = PointRepository(db.pointDao(), gatedApprovalDao, roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            assertTrue(repository.setDone(DATE, "homework", true).isSuccess)
            failBonusReviewId = db.pointApprovalDao().getAllApprovals().first()
                .single { it.bizKey == "TARGET_ALL:$DATE" }.id

            assertTrue(repository.setDone(DATE, "homework", false).isFailure)
            assertTrue(preferences.dailyTargetsDone.first().isEmpty())
            assertEquals(DATE, preferences.dailyTargetBonusDate.first())
            val approvalsAfterFailure = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(
                ApprovalStatus.CANCELLED,
                approvalsAfterFailure.single { it.bizKey == "TARGET:homework:$DATE" }.status
            )
            assertEquals(
                ApprovalStatus.PENDING,
                approvalsAfterFailure.single { it.bizKey == "TARGET_ALL:$DATE" }.status
            )

            failBonusReviewId = null
            assertTrue(repository.setDone(DATE, "homework", false).isSuccess)
            assertTrue(repository.setDone(DATE, "homework", false).isSuccess)
            assertEquals("", preferences.dailyTargetBonusDate.first())
            assertTrue(db.pointApprovalDao().getAllApprovals().first().all { it.status == ApprovalStatus.CANCELLED })
            assertEquals(2, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun staleOrUnselectedTargetCompletionIsRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-stale-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repository = DailyTargetRepository(preferences, points, clock)
            preferences.setDailyTargets(DATE, setOf("homework"))

            assertTrue(repository.setDone(DATE, "reading", true).isFailure)
            assertTrue(repository.setDone("2026-11-24", "homework", true).isFailure)
            assertTrue(preferences.dailyTargetsDone.first().isEmpty())
            assertTrue(db.pointApprovalDao().getAllApprovals().first().isEmpty())
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    private val clock = FixedAppClock(
        LocalDate(2026, 11, 25).atTime(12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    )

    companion object {
        private const val DATE = "2026-11-25"
    }
}

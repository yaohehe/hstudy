package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointApprovalConcurrencyTest {
    @Test fun concurrentApproveAwardsExactlyOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val approvalId = db.pointApprovalDao().insert(
                PointApprovalEntity(
                    actionType = "TASK_COMPLETE",
                    description = "race",
                    requestedPoints = 5,
                    bizKey = "TASK:1:2026-11-25",
                    activeBizKey = "TASK:1:2026-11-25"
                )
            )
            val results = withTimeout(30_000) {
                coroutineScope {
                    val firstRead = CompletableDeferred<Unit>()
                    val releaseFirstRead = CompletableDeferred<Unit>()
                    val releaseAfterDuplicateCheck = CompletableDeferred<Unit>()
                    val reads = AtomicInteger()
                    val duplicateChecks = AtomicInteger()
                    val gatedDao = object : PointApprovalDao by db.pointApprovalDao() {
                        override suspend fun getById(id: Long): PointApprovalEntity? {
                            val row = db.pointApprovalDao().getById(id)
                            if (reads.incrementAndGet() == 1) firstRead.complete(Unit)
                            if (reads.get() == 2) releaseFirstRead.complete(Unit)
                            if (reads.get() == 1) releaseFirstRead.await()
                            return row
                        }

                        override suspend fun getActiveAwardedByBizKey(bizKey: String): PointApprovalEntity? {
                            val row = db.pointApprovalDao().getActiveAwardedByBizKey(bizKey)
                            // Hold both baseline callers after they have observed no active award.
                            // Otherwise one can finish before the other checks and hide the race.
                            if (duplicateChecks.incrementAndGet() == 2) releaseAfterDuplicateCheck.complete(Unit)
                            releaseAfterDuplicateCheck.await()
                            return row
                        }
                    }
                    val roomRunner = roomTransactionRunner(db)
                    val transactions = AtomicInteger()
                    val arrivalAwareRunner = object : DbTransactionRunner by roomRunner {
                        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
                            // Room serializes the fixed path before the second coroutine can read.
                            // Its transaction arrival must release both baseline/fixed barriers.
                            if (transactions.incrementAndGet() == 2) {
                                releaseFirstRead.complete(Unit)
                                releaseAfterDuplicateCheck.complete(Unit)
                            }
                            return roomRunner.runInTransaction(block)
                        }
                    }
                    val gatedRepo = PointRepository(db.pointDao(), gatedDao, arrivalAwareRunner)
                    val a = async { gatedRepo.approveAndAward(approvalId) }
                    firstRead.await()
                    val b = async { gatedRepo.approveAndAward(approvalId) }
                    releaseFirstRead.await()
                    listOf(a.await(), b.await())
                }
            }
            assertEquals(listOf(5, 0), results.sortedDescending())
            assertEquals(5, db.pointDao().getTotalPoints().first())
            val ledgerRecord = db.pointDao().getAllPointRecords().first().single()
            assertEquals(ledgerRecord.id, db.pointApprovalDao().getById(approvalId)?.awardedRecordId)
            assertNull(db.pointApprovalDao().getById(approvalId)?.activeBizKey)
        } finally {
            db.close()
        }
    }

    @Test fun updateReviewOnlyTransitionsPendingRows() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val id = db.pointApprovalDao().insert(
                PointApprovalEntity(
                    actionType = "TEST",
                    description = "cas",
                    requestedPoints = 5,
                    bizKey = "TEST:CAS",
                    activeBizKey = "TEST:CAS"
                )
            )

            assertEquals(1, db.pointApprovalDao().updateReview(id, ApprovalStatus.APPROVED, 42L, 123L))
            assertEquals(0, db.pointApprovalDao().updateReview(id, ApprovalStatus.REJECTED, null, 456L))

            val row = db.pointApprovalDao().getById(id)
            assertEquals(ApprovalStatus.APPROVED, row?.status)
            assertEquals(42L, row?.awardedRecordId)
            assertEquals(123L, row?.reviewedAt)
        } finally {
            db.close()
        }
    }

    @Test fun cancelPendingOnlyTransitionsPendingRowsAndClearsActiveKey() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val pendingId = db.pointApprovalDao().insert(
                PointApprovalEntity(
                    actionType = "TEST",
                    description = "pending cancel",
                    requestedPoints = 5,
                    bizKey = "TEST:CANCEL-PENDING",
                    activeBizKey = "TEST:CANCEL-PENDING"
                )
            )
            val approvedId = db.pointApprovalDao().insert(
                PointApprovalEntity(
                    actionType = "TEST",
                    description = "approved",
                    requestedPoints = 7,
                    status = ApprovalStatus.APPROVED,
                    awardedRecordId = 99L,
                    reviewedAt = 321L,
                    bizKey = "TEST:CANCEL-APPROVED",
                    activeBizKey = "TEST:CANCEL-APPROVED"
                )
            )

            assertEquals(1, db.pointApprovalDao().cancelPending(pendingId, 123L))
            val cancelled = db.pointApprovalDao().getById(pendingId)!!
            assertEquals(ApprovalStatus.CANCELLED, cancelled.status)
            assertEquals(123L, cancelled.reviewedAt)
            assertNull(cancelled.activeBizKey)
            assertEquals(0, db.pointApprovalDao().cancelPending(pendingId, 456L))
            assertEquals(0, db.pointApprovalDao().cancelPending(approvedId, 789L))

            val approved = db.pointApprovalDao().getById(approvedId)!!
            assertEquals(ApprovalStatus.APPROVED, approved.status)
            assertEquals(99L, approved.awardedRecordId)
            assertEquals("TEST:CANCEL-APPROVED", approved.activeBizKey)
            assertEquals(321L, approved.reviewedAt)
        } finally {
            db.close()
        }
    }

    @Test fun terminalReviewClearsActiveKeyAndRecheckInClaimsIt() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            assertEquals(CheckInResult.CREATED, repo.checkIn("TEST:ACTIVE", "TEST", "first", 5))
            val first = db.pointApprovalDao().getLatestByBizKey("TEST:ACTIVE")!!
            assertEquals("TEST:ACTIVE", first.activeBizKey)

            repo.rejectApproval(first.id)
            val rejected = db.pointApprovalDao().getById(first.id)!!
            assertEquals(ApprovalStatus.REJECTED, rejected.status)
            assertNull(rejected.activeBizKey)

            assertEquals(CheckInResult.CREATED, repo.checkIn("TEST:ACTIVE", "TEST", "second", 5))
            val second = db.pointApprovalDao().getLatestByBizKey("TEST:ACTIVE")!!
            assertEquals("TEST:ACTIVE", second.activeBizKey)
            assertEquals(5, repo.approveAndAward(second.id))
            assertNull(db.pointApprovalDao().getById(second.id)?.activeBizKey)
        } finally {
            db.close()
        }
    }

    @Test fun failedApprovalInsertRollsBackTaskCompletion() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val failingApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                override suspend fun insert(approval: PointApprovalEntity): Long = error("injected approval insert")
            }
            val repo = PointRepository(db.pointDao(), failingApprovalDao, roomTransactionRunner(db))

            val failure = runCatching { repo.checkInForTask(91L, 5, "rollback") }.exceptionOrNull()
            assertEquals("injected approval insert", failure?.message)

            val today = PointRepository.getTodayString()
            assertEquals(emptyList<Long>(), db.pointDao().getCompletedTaskIdsForDate(today).first())
            assertEquals(0, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test fun failedAwardInsertRollsBackApprovalClaim() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val id = db.pointApprovalDao().insert(
                PointApprovalEntity(
                    actionType = "TEST",
                    description = "rollback award",
                    requestedPoints = 5,
                    bizKey = "TEST:AWARD-RB",
                    activeBizKey = "TEST:AWARD-RB"
                )
            )
            val failingPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: com.hstudy.app.data.local.entity.PointRecordEntity): Long =
                    error("injected point insert")
            }
            val repo = PointRepository(failingPointDao, db.pointApprovalDao(), roomTransactionRunner(db))

            val failure = runCatching { repo.approveAndAward(id) }.exceptionOrNull()
            assertEquals("injected point insert", failure?.message)

            val row = db.pointApprovalDao().getById(id)!!
            assertEquals(ApprovalStatus.PENDING, row.status)
            assertEquals("TEST:AWARD-RB", row.activeBizKey)
            assertEquals(0, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }
}

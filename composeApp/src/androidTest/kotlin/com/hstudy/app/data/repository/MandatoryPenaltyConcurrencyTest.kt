package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandatoryPenaltyConcurrencyTest {
    private val today = LocalDate(2026, 11, 26)
    private val penaltyDate = "2026-11-25"

    @Test
    fun concurrentRunsForOnePeriodDeductExactlyOnceAndNeverOverdraw() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 10)
            db.taskDao().insert(
                TaskEntity(
                    title = "mandatory",
                    category = TaskCategory.BASIC,
                    points = 7,
                    isMandatory = true,
                )
            )
            val runner = roomTransactionRunner(db)
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)

            val results = withTimeout(30_000) {
                coroutineScope {
                    (1..12).map {
                        async {
                            MandatoryPenaltyHelper.processPenalties(
                                runDao = db.mandatoryPenaltyRunDao(),
                                transactionRunner = runner,
                                pointRepository = points,
                                today = today,
                            )
                        }
                    }.awaitAll()
                }
            }

            assertEquals(listOf(7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), results.sortedDescending())
            assertEquals(3, points.getTotalPoints().first())
            val penalties = db.pointDao().getAllPointRecords().first().filter { it.type == PointRecordType.PENALTY }
            assertEquals(1, penalties.size)
            assertEquals(-7, penalties.single().points)
            val marker = db.mandatoryPenaltyRunDao().getByPeriodKey(penaltyDate)
            assertNotNull(marker)
            assertEquals(7, checkNotNull(marker).deductedPoints)
            assertEquals(0, MandatoryPenaltyHelper.processPenalties(db.mandatoryPenaltyRunDao(), runner, points, today))
        } finally {
            db.close()
        }
    }

    @Test
    fun failedPenaltyInsertRollsBackMarkerAndRetrySucceedsOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 10)
            db.taskDao().insert(
                TaskEntity(
                    title = "mandatory retry",
                    category = TaskCategory.BASIC,
                    points = 7,
                    isMandatory = true,
                )
            )
            val runner = roomTransactionRunner(db)
            val failingPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long =
                    error("injected penalty ledger failure")
            }
            val failingPoints = PointRepository(failingPointDao, db.pointApprovalDao(), runner)

            val failure = runCatching {
                MandatoryPenaltyHelper.processPenalties(db.mandatoryPenaltyRunDao(), runner, failingPoints, today)
            }.exceptionOrNull()

            assertEquals("injected penalty ledger failure", failure?.message)
            assertEquals(0, db.mandatoryPenaltyRunDao().getByPeriodKey(penaltyDate)?.let { 1 } ?: 0)
            assertEquals(10, db.pointDao().getTotalPoints().first())
            assertEquals(0, db.pointDao().getAllPointRecords().first().count { it.type == PointRecordType.PENALTY })

            val retry = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            assertEquals(
                7,
                MandatoryPenaltyHelper.processPenalties(db.mandatoryPenaltyRunDao(), runner, retry, today),
            )
            assertEquals(3, retry.getTotalPoints().first())
            assertEquals(7, db.mandatoryPenaltyRunDao().getByPeriodKey(penaltyDate)?.deductedPoints)
            assertEquals(1, db.pointDao().getAllPointRecords().first().count { it.type == PointRecordType.PENALTY })
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentSpendAndPenaltyCannotDriveBalanceNegative() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 10)
            db.taskDao().insert(
                TaskEntity(
                    title = "mandatory race",
                    category = TaskCategory.BASIC,
                    points = 10,
                    isMandatory = true,
                )
            )
            val runner = roomTransactionRunner(db)
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)

            val (deducted, spent) = withTimeout(30_000) {
                coroutineScope {
                    val penalty = async {
                        MandatoryPenaltyHelper.processPenalties(db.mandatoryPenaltyRunDao(), runner, points, today)
                    }
                    val spend = async { points.spendPoints(10, "concurrent spend") }
                    penalty.await() to spend.await()
                }
            }

            assertTrue("exactly one writer may consume the ten points", listOf(deducted, if (spent) 10 else 0).sum() == 10)
            assertEquals(0, points.getTotalPoints().first())
            assertFalse(points.getTotalPoints().first() < 0)
            assertEquals(1, db.pointDao().getAllPointRecords().first().count { it.points == -10 })
            assertEquals(1, db.mandatoryPenaltyRunDao().getByPeriodKey(penaltyDate)?.let { 1 } ?: 0)
        } finally {
            db.close()
        }
    }

    private suspend fun fund(dao: PointDao, points: Int) {
        dao.insertPointRecord(
            PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = points, note = "test funding")
        )
    }
}

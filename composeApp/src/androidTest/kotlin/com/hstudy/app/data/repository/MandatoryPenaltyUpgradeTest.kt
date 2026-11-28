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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MandatoryPenaltyUpgradeTest {
    private val today = LocalDate(2026, 11, 26)
    private val yesterday = "2026-11-25"

    @Test
    fun emptyMarkerTableWithYesterdayLegacyCursorDoesNotReplayPenalty() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 10)
            insertIncompleteMandatoryTask(db)
            val runner = roomTransactionRunner(db)
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            assertNull(db.mandatoryPenaltyRunDao().getByPeriodKey(yesterday))

            val deducted = MandatoryPenaltyHelper.processPenalties(
                runDao = db.mandatoryPenaltyRunDao(),
                transactionRunner = runner,
                pointRepository = points,
                today = today,
                legacyCursor = yesterday,
            )

            assertEquals(0, deducted)
            assertEquals(10, points.getTotalPoints().first())
            assertEquals(0, db.pointDao().getAllPointRecords().first().count { it.type == PointRecordType.PENALTY })
            val seeded = db.mandatoryPenaltyRunDao().getByPeriodKey(yesterday)
            assertNotNull(seeded)
            assertEquals(0, checkNotNull(seeded).deductedPoints)
        } finally {
            db.close()
        }
    }

    @Test
    fun staleLegacyCursorIsSeededBeforeOnlyNewerDatesAreProcessed() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 10)
            insertIncompleteMandatoryTask(db)
            val runner = roomTransactionRunner(db)
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)

            val deducted = MandatoryPenaltyHelper.processPenalties(
                runDao = db.mandatoryPenaltyRunDao(),
                transactionRunner = runner,
                pointRepository = points,
                today = today,
                legacyCursor = "2026-11-24",
            )

            assertEquals(7, deducted)
            assertEquals(0, db.mandatoryPenaltyRunDao().getByPeriodKey("2026-11-24")?.deductedPoints)
            assertEquals(7, db.mandatoryPenaltyRunDao().getByPeriodKey(yesterday)?.deductedPoints)
            assertEquals(1, db.pointDao().getAllPointRecords().first().count { it.type == PointRecordType.PENALTY })
        } finally {
            db.close()
        }
    }

    @Test
    fun olderLegacyCursorIsSeededAndCatchUpIsClampedToSevenDays() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db.pointDao(), 100)
            insertIncompleteMandatoryTask(db)
            val runner = roomTransactionRunner(db)
            val points = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)

            val deducted = MandatoryPenaltyHelper.processPenalties(
                runDao = db.mandatoryPenaltyRunDao(),
                transactionRunner = runner,
                pointRepository = points,
                today = today,
                legacyCursor = "2026-11-01",
            )

            assertEquals(49, deducted)
            assertEquals(0, db.mandatoryPenaltyRunDao().getByPeriodKey("2026-11-01")?.deductedPoints)
            assertNull(db.mandatoryPenaltyRunDao().getByPeriodKey("2026-11-18"))
            assertEquals(7, db.mandatoryPenaltyRunDao().getByPeriodKey("2026-11-19")?.deductedPoints)
            assertEquals(7, db.mandatoryPenaltyRunDao().getByPeriodKey(yesterday)?.deductedPoints)
            assertEquals(7, db.pointDao().getAllPointRecords().first().count { it.type == PointRecordType.PENALTY })
        } finally {
            db.close()
        }
    }

    private suspend fun insertIncompleteMandatoryTask(db: com.hstudy.app.data.local.AppDatabase) {
        db.taskDao().insert(
            TaskEntity(
                title = "incomplete mandatory",
                category = TaskCategory.BASIC,
                points = 7,
                isMandatory = true,
            )
        )
    }

    private suspend fun fund(dao: PointDao, points: Int) {
        dao.insertPointRecord(
            PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = points, note = "test funding")
        )
    }
}

package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpendPointsConcurrencyTest {
    @Test fun concurrentTenPointSpendsCannotOverdraw() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 10))
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val results = withTimeout(30_000) {
                coroutineScope {
                    (1..20).map { async { repo.spendPoints(10, "pet", true) } }.awaitAll()
                }
            }
            assertEquals(1, results.count { it })
            assertEquals(0, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test fun defaultSpendRejectsInsufficientBalance() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 3))
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertFalse(repo.spendPoints(5, "default guard"))
            assertEquals(3, db.pointDao().getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test fun explicitOptOutCanCreateNegativeBalance() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 3))
            val repo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            assertTrue(repo.spendPoints(5, "explicit legacy opt-out", requireSufficientBalance = false))
            assertEquals(-2, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }
}

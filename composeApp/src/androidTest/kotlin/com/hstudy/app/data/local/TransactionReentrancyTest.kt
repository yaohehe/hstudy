package com.hstudy.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionReentrancyTest {
    @Test fun nestedFailureRollsBackOuterTransaction() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val runner = roomTransactionRunner(db)
            val result = runCatching {
                runner.runInTransaction {
                    db.pointDao().insertPointRecord(
                        com.hstudy.app.data.local.entity.PointRecordEntity(type = com.hstudy.app.data.local.entity.PointRecordType.ADJUSTMENT, points = 1)
                    )
                    runner.runInTransaction { error("inner") }
                }
            }
            assertEquals("inner", result.exceptionOrNull()?.message)
            assertEquals(0, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }
}

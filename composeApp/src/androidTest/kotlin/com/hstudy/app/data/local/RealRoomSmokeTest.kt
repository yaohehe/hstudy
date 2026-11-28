package com.hstudy.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealRoomSmokeTest {
    @Test fun driverBackedTransactionCommitsPointInsert() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            roomTransactionRunner(db).runInTransaction {
                db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 5))
            }
            assertEquals(5, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test fun realTransactionRollsBackPointInsert() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        try {
            val runner = roomTransactionRunner(db)
            val error = runCatching {
                runner.runInTransaction {
                    db.pointDao().insertPointRecord(PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = 5))
                    error("injected")
                }
            }.exceptionOrNull()
            assertEquals("injected", error?.message)
            assertEquals(0, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }
}

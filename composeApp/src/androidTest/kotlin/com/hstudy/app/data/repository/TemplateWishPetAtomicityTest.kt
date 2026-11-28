package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.dao.WishGoalDao
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.WishGoalEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateWishPetAtomicityTest {

    @Test
    fun failedSecondTemplateInsertRestoresOriginalActiveTasks() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val original = listOf(
                TaskEntity(title = "原任务一", category = TaskCategory.BASIC, points = 3),
                TaskEntity(title = "原任务二", category = TaskCategory.HABIT, points = 2)
            )
            original.forEach { db.taskDao().insert(it) }
            val originalIds = db.taskDao().getAllActiveTasksSnapshot().map { it.id }
            var insertAttempts = 0
            val failingDao = object : TaskDao by db.taskDao() {
                override suspend fun insert(task: TaskEntity): Long {
                    insertAttempts++
                    if (insertAttempts == 2) error("injected second template insert failure")
                    return db.taskDao().insert(task)
                }
            }
            val repo = TaskRepository(failingDao, db.dailyScheduleDao(), roomTransactionRunner(db))
            val template = TaskTemplate(
                id = "test",
                name = "测试模板",
                description = "",
                emoji = "",
                ageRange = "",
                tasks = listOf(
                    TaskEntity(title = "新任务一", category = TaskCategory.BASIC, points = 4),
                    TaskEntity(title = "新任务二", category = TaskCategory.BASIC, points = 5),
                    TaskEntity(title = "新任务三", category = TaskCategory.BASIC, points = 6)
                )
            )

            assertThrows(IllegalStateException::class.java) { runBlocking { repo.applyTemplate(template) } }

            val restored = db.taskDao().getAllActiveTasksSnapshot()
            assertEquals(originalIds, restored.map { it.id })
            assertEquals(original.map { it.title }, restored.map { it.title })
            assertEquals(0, restored.count { it.title.startsWith("新任务") })
        } finally {
            db.close()
        }
    }

    @Test
    fun depositAboveRemainingIsRejected() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val goalId = db.wishGoalDao().insert(WishGoalEntity(name = "目标", targetPoints = 50))
            fund(db, 100)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val wishRepo = WishGoalRepository(db.wishGoalDao(), pointRepo, roomTransactionRunner(db))

            assertFalse(wishRepo.depositPoints(goalId, 100))

            assertEquals(100, pointRepo.getTotalPoints().first())
            assertEquals(0, db.wishGoalDao().getGoalById(goalId)?.savedPoints)
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun depositAboveBalanceIsRejected() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val goalId = db.wishGoalDao().insert(WishGoalEntity(name = "目标", targetPoints = 500))
            fund(db, 20)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val wishRepo = WishGoalRepository(db.wishGoalDao(), pointRepo, roomTransactionRunner(db))

            assertFalse(wishRepo.depositPoints(goalId, 50))

            assertEquals(20, pointRepo.getTotalPoints().first())
            assertEquals(0, db.wishGoalDao().getGoalById(goalId)?.savedPoints)
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedWishProgressUpdateRollsBackDebit() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val goalId = db.wishGoalDao().insert(WishGoalEntity(name = "目标", targetPoints = 50))
            fund(db, 100)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val failingDao = object : WishGoalDao by db.wishGoalDao() {
                override suspend fun addSavedPoints(id: Long, amount: Int) = error("injected")
            }
            val wishRepo = WishGoalRepository(failingDao, pointRepo, roomTransactionRunner(db))

            assertFalse(runCatching { wishRepo.depositPoints(goalId, 10) }.isSuccess)

            assertEquals(100, pointRepo.getTotalPoints().first())
            assertEquals(0, db.wishGoalDao().getGoalById(goalId)?.savedPoints)
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedGoalDeleteRollsBackRefund() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val goalId = db.wishGoalDao().insert(WishGoalEntity(name = "目标", targetPoints = 50))
            fund(db, 100)
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val wishRepo = WishGoalRepository(db.wishGoalDao(), pointRepo, runner)
            assertTrue(wishRepo.depositPoints(goalId, 40))
            val failingDao = object : WishGoalDao by db.wishGoalDao() {
                override suspend fun delete(id: Long) = error("injected")
            }
            val failingRepo = WishGoalRepository(failingDao, pointRepo, runner)

            assertThrows(IllegalStateException::class.java) { runBlocking { failingRepo.deleteGoal(goalId) } }

            assertEquals(60, pointRepo.getTotalPoints().first())
            assertEquals(40, db.wishGoalDao().getGoalById(goalId)?.savedPoints)
            assertEquals(2, db.pointDao().getAllPointRecords().first().size)
            assertTrue(db.pointDao().getAllPointRecords().first().none { it.note == "wish_refund:$goalId" })
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentScenePurchasesDebitAndUnlockExactlyOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db, 10)
            val repo = PetRepository(db.pointDao(), db.petScenePurchaseDao(), roomTransactionRunner(db))

            val results = withTimeout(30_000) {
                coroutineScope {
                    (1..2).map { async { repo.purchaseScene("space", 10, "pet_scene:space") } }.awaitAll()
                }
            }

            assertEquals(1, results.count { it })
            assertEquals(listOf("space"), db.petScenePurchaseDao().getPurchasedSceneKeys().first())
            val records = db.pointDao().getAllPointRecords().first()
            assertEquals(1, records.count { it.points == -10 && it.type == PointRecordType.REWARD_EXCHANGE })
            assertEquals(0, records.sumOf { it.points })
            assertFalse(repo.purchaseScene("space", 10, "pet_scene:space"))
            assertEquals(0, db.pointDao().getTotalPoints().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun failedSceneLedgerInsertRollsBackOwnership() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db, 10)
            val failingPointDao = object : com.hstudy.app.data.local.dao.PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long =
                    error("injected scene ledger failure")
            }
            val repo = PetRepository(failingPointDao, db.petScenePurchaseDao(), roomTransactionRunner(db))

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repo.purchaseScene("space", 10, "pet_scene:space") }
            }

            assertTrue(db.petScenePurchaseDao().getPurchasedSceneKeys().first().isEmpty())
            assertEquals(10, db.pointDao().getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun feedRejectsInsufficientBalanceWithoutLedgerRow() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db, 5)
            val repo = PetRepository(db.pointDao(), db.petScenePurchaseDao(), roomTransactionRunner(db))

            assertFalse(repo.feed(10, "feed"))
            assertTrue(repo.feed(5, "feed"))

            assertEquals(0, db.pointDao().getTotalPoints().first())
            assertEquals(2, db.pointDao().getAllPointRecords().first().size)
            assertEquals(-5, db.pointDao().getAllPointRecords().first().single { it.points < 0 }.points)
        } finally {
            db.close()
        }
    }

    @Test
    fun legacySceneOwnershipImportsWithoutDebit() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            fund(db, 10)
            val repo = PetRepository(db.pointDao(), db.petScenePurchaseDao(), roomTransactionRunner(db))

            repo.importLegacyScenes(setOf("space", "space", ""))

            assertEquals(setOf("space"), repo.getPurchasedScenes().first())
            assertEquals(10, db.pointDao().getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun schema14ContainsPetScenePurchasesTable() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertNotNull(db.petScenePurchaseDao())
            assertTrue(db.petScenePurchaseDao().getPurchasedSceneKeys().first().isEmpty())
        } finally {
            db.close()
        }
    }

    private suspend fun fund(db: AppDatabase, points: Int) {
        db.pointDao().insertPointRecord(
            PointRecordEntity(points = points, type = PointRecordType.ADJUSTMENT, note = "test funding")
        )
    }
}

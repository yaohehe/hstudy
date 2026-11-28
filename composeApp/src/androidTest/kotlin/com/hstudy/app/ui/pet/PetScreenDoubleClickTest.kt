package com.hstudy.app.ui.pet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.data.repository.PetRepository
import com.hstudy.app.data.repository.TaskRepository
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.WindowSizeClass
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetScreenDoubleClickTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rapidFeedTapsDebitOnlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val insertEntered = CountDownLatch(1)
        val releaseInsert = CountDownLatch(1)
        val debitWritten = CountDownLatch(1)
        val feedInsertCount = AtomicInteger(0)
        try {
            runBlocking {
                db.pointDao().insertPointRecord(
                    PointRecordEntity(points = 30, type = PointRecordType.ADJUSTMENT, note = "test funding")
                )
            }
            val gatedPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long {
                    if (record.points == -10 && record.type == PointRecordType.REWARD_EXCHANGE) {
                        feedInsertCount.incrementAndGet()
                        insertEntered.countDown()
                        check(releaseInsert.await(15, TimeUnit.SECONDS))
                    }
                    return db.pointDao().insertPointRecord(record).also {
                        if (record.points == -10 && record.type == PointRecordType.REWARD_EXCHANGE) {
                            debitWritten.countDown()
                        }
                    }
                }
            }
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(gatedPointDao, db.pointApprovalDao(), runner)
            val petRepo = PetRepository(gatedPointDao, db.petScenePurchaseDao(), runner)
            val taskRepo = TaskRepository(db.taskDao(), runner)
            val preferences = UserPreferences(testDataStore(context, "pet-double-click-${UUID.randomUUID()}"))
            val feedLabel = AppStringsZh.pet.feedButton(10)

            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH
                    ) {
                        ChildPetScreen(
                            onNavigateBack = {},
                            windowSizeClass = WindowSizeClass(),
                            pointRepository = pointRepo,
                            taskRepository = taskRepo,
                            petRepository = petRepo,
                            userPreferences = preferences
                        )
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(feedLabel).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText(feedLabel).performScrollTo().performClick()
            assertTrue("first feed never reached ledger insert", insertEntered.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText(feedLabel).performClick()
            releaseInsert.countDown()
            assertTrue("first feed did not commit", debitWritten.await(10, TimeUnit.SECONDS))
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(AppStringsZh.pet.fedMessage).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()

            val records = runBlocking { db.pointDao().getAllPointRecords().first() }
            assertEquals(1, feedInsertCount.get())
            assertEquals(1, records.count { it.points == -10 && it.type == PointRecordType.REWARD_EXCHANGE })
            assertEquals(20, records.sumOf { it.points })
            assertTrue(records.sumOf { it.points } >= 0)
        } finally {
            releaseInsert.countDown()
            db.close()
        }
    }

    @Test
    fun purchaseWaitsForLegacyOwnershipImportAndOwnedSceneRemainsEquippable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val legacyReadStarted = CompletableDeferred<Unit>()
        val releaseLegacyRead = CompletableDeferred<Unit>()
        val blockLegacyReads = AtomicBoolean(false)
        val dataStore = testDataStore(context, "pet-purchase-readiness-${UUID.randomUUID()}")
        val preferences = UserPreferences(object : DataStore<Preferences> by dataStore {
            override val data: Flow<Preferences> = dataStore.data.onStart {
                if (blockLegacyReads.get()) {
                    legacyReadStarted.complete(Unit)
                    releaseLegacyRead.await()
                }
            }
        })
        try {
            runBlocking {
                preferences.addOwnedScene("forest")
                db.pointDao().insertPointRecord(
                    PointRecordEntity(points = 100, type = PointRecordType.ADJUSTMENT, note = "test funding")
                )
            }
            blockLegacyReads.set(true)
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val petRepo = PetRepository(db.pointDao(), db.petScenePurchaseDao(), runner)
            val taskRepo = TaskRepository(db.taskDao(), runner)

            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH
                    ) {
                        ChildPetScreen(
                            onNavigateBack = {},
                            windowSizeClass = WindowSizeClass(),
                            pointRepository = pointRepo,
                            taskRepository = taskRepo,
                            petRepository = petRepo,
                            userPreferences = preferences
                        )
                    }
                }
            }
            runBlocking {
                withTimeout(10_000) { legacyReadStarted.await() }
            }

            val buyLabel = AppStringsZh.pet.buyButton
            val equipLabel = AppStringsZh.pet.equipButton
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(buyLabel).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithText(buyLabel)[0].assertIsNotEnabled()
            compose.onAllNodesWithText(buyLabel)[0]
                .performScrollTo()
                .assertIsNotEnabled()
                .performClick()
            compose.waitForIdle()
            assertEquals(100, runBlocking { db.pointDao().getTotalPoints().first() })
            assertTrue(runBlocking { db.petScenePurchaseDao().getPurchasedSceneKeys().first() }.isEmpty())

            releaseLegacyRead.complete(Unit)
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(buyLabel).fetchSemanticsNodes().size == 5 &&
                    compose.onAllNodesWithText(equipLabel).fetchSemanticsNodes().size == 1
            }
            compose.waitForIdle()
            assertEquals(listOf("forest"), runBlocking { db.petScenePurchaseDao().getPurchasedSceneKeys().first() })
            assertEquals(100, runBlocking { db.pointDao().getTotalPoints().first() })

            compose.onAllNodesWithText(equipLabel)[0].performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals("forest", runBlocking { preferences.equippedPetScene.first() })
            assertEquals(100, runBlocking { db.pointDao().getTotalPoints().first() })
        } finally {
            releaseLegacyRead.complete(Unit)
            db.close()
        }
    }
}

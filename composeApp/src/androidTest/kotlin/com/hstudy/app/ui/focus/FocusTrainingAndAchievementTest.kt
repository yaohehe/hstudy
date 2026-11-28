package com.hstudy.app.ui.focus

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.HabitChallengeEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.MemoryTrainingState
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.HabitRepository
import com.hstudy.app.data.repository.MistakeRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.ui.child.ChildAchievementsScreen
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.WindowSizeClass
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusTrainingAndAchievementTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstFocusResultIsRecordAndConcurrentSlowerResultCannotRegressBest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "focus-best-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            assertFalse(preferences.tryUpdateFocusTrainingBest(0L))
            assertTrue("the first valid Schulte result must report a record", preferences.tryUpdateFocusTrainingBest(25_000L))
            assertEquals(25_000L, preferences.focusTrainingBestMs.first())

            assertTrue("20_000 beats the existing 25_000 record", preferences.tryUpdateFocusTrainingBest(20_000L))
            assertFalse("30_000 cannot beat 20_000", preferences.tryUpdateFocusTrainingBest(30_000L))
            assertEquals(20_000L, preferences.focusTrainingBestMs.first())

            val winners = coroutineScope {
                (1..20).map { candidate ->
                    async { preferences.tryUpdateFocusTrainingBest(10_000L + candidate) }
                }.awaitAll()
            }
            assertEquals(1, winners.count { it })
            assertEquals(10_001L, preferences.focusTrainingBestMs.first())
            assertFalse(preferences.tryUpdateFocusTrainingBest(30_000L))
            assertEquals(10_001L, preferences.focusTrainingBestMs.first())
        } finally {
            storeFile.delete()
        }
    }

    @Test
    fun memoryTrainingTransformsSerializeAgainstTheLatestState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "memory-transform-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            preferences.saveMemoryTrainingState(MemoryTrainingState())

            val changed = coroutineScope {
                (1..20).map {
                    async {
                        preferences.updateMemoryTrainingState { current ->
                            current.copy(bestLevel = current.bestLevel + 1)
                        }
                    }
                }.awaitAll()
            }

            assertEquals(List(20) { true }, changed)
            assertEquals(20, preferences.memoryTrainingState.first()!!.bestLevel)
        } finally {
            storeFile.delete()
        }
    }

    @Test
    fun memoryTrainingStartIsGatedUntilTheFirstRestoredValueArrives() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RecreatableTestDataStore(context, "memory-restore-gate-${UUID.randomUUID()}")
        val firstReadGate = CompletableDeferred<Unit>()
        val saved = MemoryTrainingState(
            phase = MemoryTrainingState.Phase.SHOWING,
            level = 4,
            sequence = "9876",
            bestLevel = 5,
            showUntilEpochMs = 4_102_444_800_000L,
        )
        try {
            runBlocking { UserPreferences(store.current).saveMemoryTrainingState(saved) }
            val preferences = UserPreferences(GatedFirstReadDataStore(store.current, firstReadGate))
            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH,
                    ) {
                        ChildMemoryTrainingScreen(onNavigateBack = {}, userPreferences = preferences)
                    }
                }
            }

            compose.onNodeWithText(AppStringsZh.focus.startTrainingButton).assertDoesNotExist()
            compose.onNodeWithText("9876").assertDoesNotExist()
            assertEquals(saved, runBlocking { UserPreferences(store.current).memoryTrainingState.first() })

            firstReadGate.complete(Unit)
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("9876").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("9876").assertExists()
            compose.onNodeWithText(AppStringsZh.focus.startTrainingButton).assertDoesNotExist()
            assertEquals(saved, runBlocking { UserPreferences(store.current).memoryTrainingState.first() })
        } finally {
            firstReadGate.complete(Unit)
            store.close()
        }
    }

    @Test
    fun memoryTrainingStateSurvivesScreenRemovalAndDataStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = RecreatableTestDataStore(context, "memory-state-${UUID.randomUUID()}")
        var showMemory by mutableStateOf(true)
        try {
            runBlocking {
                UserPreferences(store.current).saveMemoryTrainingState(
                    MemoryTrainingState(
                        schemaVersion = 1,
                        level = 5,
                        sequence = "12345",
                        bestLevel = 7,
                        lastCorrect = true,
                    )
                )
            }
            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH,
                    ) {
                        if (showMemory) {
                            ChildMemoryTrainingScreen(
                                onNavigateBack = {},
                                userPreferences = UserPreferences(store.current),
                            )
                        } else {
                            Text("memory screen removed")
                        }
                    }
                }
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(AppStringsZh.focus.memoryReadyLine(5)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(AppStringsZh.focus.memoryBestLine(7)).assertExists()

            compose.runOnIdle { showMemory = false }
            compose.waitForIdle()
            store.recreate()
            compose.runOnIdle { showMemory = true }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(AppStringsZh.focus.memoryReadyLine(5)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(AppStringsZh.focus.memoryBestLine(7)).assertExists()
            assertEquals(
                MemoryTrainingState(
                    schemaVersion = 1,
                    level = 5,
                    sequence = "12345",
                    bestLevel = 7,
                    lastCorrect = true,
                ),
                runBlocking { UserPreferences(store.current).memoryTrainingState.first() },
            )
        } finally {
            compose.runOnIdle { showMemory = false }
            store.close()
        }
    }

    @Test
    fun perfectDayAndHabitProgressRefreshWhileAchievementScreenRemainsOpen(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "achievement-progress-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val today = PointRepository.getTodayString()
            val taskIds = (1..2).map { index ->
                db.taskDao().insert(
                    TaskEntity(
                        title = "任务$index",
                        category = TaskCategory.BASIC,
                        points = 2,
                    )
                )
            }
            val pointRepository = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val habitRepository = HabitRepository(db.habitDao())
            val mistakeRepository = MistakeRepository(db.mistakeDao(), pointRepository, roomTransactionRunner(db))
            val focusRepository = FocusRepository(db.focusDao())
            val preferences = UserPreferences(testDataStore(context, storeName))

            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalStrings provides AppStringsZh,
                        LocalDisplayLanguage provides DisplayLanguage.ZH,
                    ) {
                        ChildAchievementsScreen(
                            onNavigateBack = {},
                            windowSizeClass = WindowSizeClass(),
                            habitRepository = habitRepository,
                            pointRepository = pointRepository,
                            mistakeRepository = mistakeRepository,
                            focusRepository = focusRepository,
                            userPreferences = preferences,
                        )
                    }
                }
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    compose.onNodeWithTag("achievement-progress-perfect_day").assertTextContains("0/1")
                }.isSuccess
            }
            compose.onNodeWithTag("achievement-progress-perfect_day").assertTextEquals("0/1")
            compose.onNodeWithTag("achievement-progress-habit_complete").assertTextEquals("0/1")

            db.pointDao().insertCompletion(
                TaskCompletionEntity(
                    taskId = taskIds.first(),
                    pointsEarned = 2,
                    date = today,
                    frequencyPeriod = today,
                )
            )
            compose.waitForIdle()
            compose.onNodeWithTag("achievement-progress-perfect_day").assertTextEquals("0/1")

            db.pointDao().insertCompletion(
                TaskCompletionEntity(
                    taskId = taskIds.last(),
                    pointsEarned = 2,
                    date = today,
                    frequencyPeriod = today,
                )
            )
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    compose.onNodeWithTag("achievement-progress-perfect_day").assertTextContains("1/1")
                }.isSuccess
            }
            compose.onNodeWithTag("achievement-progress-perfect_day").assertTextEquals("1/1")

            db.habitDao().insertChallenge(
                HabitChallengeEntity(
                    title = "二十一天挑战",
                    startDate = today,
                    totalDays = 21,
                    completedDays = 21,
                    isActive = false,
                    isCompleted = true,
                )
            )
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    compose.onNodeWithTag("achievement-progress-habit_complete").assertTextContains("1/1")
                }.isSuccess
            }
            compose.onNodeWithTag("achievement-progress-habit_complete").assertTextEquals("1/1")
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    private class GatedFirstReadDataStore(
        private val delegate: DataStore<Preferences>,
        private val firstReadGate: CompletableDeferred<Unit>,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = delegate.data.onStart { firstReadGate.await() }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            delegate.updateData(transform)
    }

    private class RecreatableTestDataStore(context: Context, name: String) {
        private val file = context.preferencesDataStoreFile(name)
        private var scope = newScope()
        var current: DataStore<Preferences> = newStore(scope)
            private set

        fun recreate() {
            runBlocking {
                scope.coroutineContext.job.cancelAndJoin()
            }
            scope = newScope()
            current = newStore(scope)
        }

        fun close() {
            runBlocking {
                scope.coroutineContext.job.cancelAndJoin()
            }
            file.delete()
        }

        private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun newStore(scope: CoroutineScope): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
    }
}

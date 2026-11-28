package com.hstudy.app.ui.target

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.DailyTargetRepository
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.MistakeRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.FixedAppClock
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.WindowSizeClass
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChildDailyTargetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun blockedResetShowsActionableBilingualMessageAndKeepsCurrentGoals() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "daily-target-compose-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            runBlocking {
                val preferences = UserPreferences(testDataStore(context, storeName))
                val points = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
                preferences.setDailyTargets(DATE, setOf("homework", "reading"))
                points.checkIn(
                    bizKey = "TARGET:homework:$DATE",
                    actionType = "DAILY_TARGET",
                    description = "完成每日目标：完成作业",
                    points = 2,
                    date = DATE
                )
                val repository = DailyTargetRepository(
                    preferences,
                    points,
                    FixedAppClock(
                        LocalDate(2026, 11, 25).atTime(12, 0)
                            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    )
                )
                val strings = AppStringsZh.childDailyTarget

                compose.setContent {
                    HStudyTheme {
                        CompositionLocalProvider(
                            LocalStrings provides AppStringsZh,
                            LocalDisplayLanguage provides DisplayLanguage.ZH
                        ) {
                            ChildDailyTargetScreen(
                                onNavigateBack = {},
                                windowSizeClass = WindowSizeClass(),
                                preferences = preferences,
                                dailyTargetRepository = repository,
                                mistakeRepository = MistakeRepository(db.mistakeDao(), points, roomTransactionRunner(db)),
                                focusRepository = FocusRepository(db.focusDao()),
                                today = DATE
                            )
                        }
                    }
                }
                compose.waitForIdle()
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(strings.resetGoalsButton).fetchSemanticsNodes().isNotEmpty()
                }

                compose.onNodeWithText(strings.resetGoalsButton).performScrollTo().performClick()
                compose.onNodeWithText(strings.setGoalsButton(2)).performScrollTo().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithText(strings.resetBlocked).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(strings.resetBlocked).assertIsDisplayed()
                compose.waitForIdle()

                assertEquals(setOf("homework", "reading"), preferences.dailyTargets.first())
                assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
            }
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    companion object {
        private const val DATE = "2026-11-25"
    }
}

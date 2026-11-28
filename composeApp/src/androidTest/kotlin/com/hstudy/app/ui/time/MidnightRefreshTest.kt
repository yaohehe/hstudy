package com.hstudy.app.ui.time

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hstudy.app.data.local.entity.DailyPlanEntity
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.ScheduleRepository
import com.hstudy.app.data.repository.TaskRepository
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.time.AppClock
import com.hstudy.app.time.FixedAppClock
import com.hstudy.app.time.SystemAppClock
import com.hstudy.app.ui.focus.ChildDailyPlanScreen
import com.hstudy.app.ui.schedule.ChildScheduleTimelineScreen
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.WindowSizeClass
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MidnightRefreshTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun composedScreensCrossMidnightWithoutRecreatingComposition() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val db = testDatabase(context)
        val clock = FixedAppClock(localMillis(BEFORE_DATE, 23, 59))
        val focusRepository = FocusRepository(db.focusDao())
        val scheduleRepository = ScheduleRepository(db.dailyScheduleDao())
        val taskRepository = TaskRepository(db.taskDao())
        val compositionToken = AtomicReference<Any>()
        val strings = AppStringsZh
        try {
            runBlocking {
                focusRepository.addPlan(
                    DailyPlanEntity(
                        date = BEFORE_DATE.toString(),
                        startHour = 20,
                        startMinute = 0,
                        endHour = 21,
                        endMinute = 0,
                        taskTitle = OLD_PLAN,
                    )
                )
                focusRepository.addPlan(
                    DailyPlanEntity(
                        date = AFTER_DATE.toString(),
                        startHour = 7,
                        startMinute = 0,
                        endHour = 8,
                        endMinute = 0,
                        taskTitle = NEW_PLAN,
                    )
                )
                scheduleRepository.addSchedule(
                    DailyScheduleEntity(
                        time = "23:30",
                        title = EVENING_SCHEDULE,
                        repeatDays = BEFORE_DATE.dayOfWeek.isoDayNumber.toString(),
                    )
                )
            }

            compose.mainClock.autoAdvance = false
            compose.setContent {
                HStudyTheme {
                    CompositionLocalProvider(
                        LocalAppClock provides clock,
                        LocalStrings provides strings,
                        LocalDisplayLanguage provides DisplayLanguage.ZH,
                    ) {
                        val token = remember { Any() }
                        SideEffect { compositionToken.set(token) }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                ChildDailyPlanScreen(
                                    onNavigateBack = {},
                                    windowSizeClass = WindowSizeClass(),
                                    clock = LocalAppClock.current,
                                    focusRepository = focusRepository,
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                ChildScheduleTimelineScreen(
                                    onNavigateBack = {},
                                    windowSizeClass = WindowSizeClass(),
                                    clock = LocalAppClock.current,
                                    scheduleRepository = scheduleRepository,
                                    taskRepository = taskRepository,
                                )
                            }
                        }
                    }
                }
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()

            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(OLD_PLAN).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(OLD_PLAN).assertExists()
            compose.onNodeWithText(NEW_PLAN).assertDoesNotExist()
            compose.onNodeWithText(EVENING_SCHEDULE).assertExists()
            compose.onNodeWithText(strings.childScheduleTimeline.nowLabel("23:59")).assertExists()
            compose.onNode(
                hasText(EVENING_SCHEDULE) and hasAnySibling(hasText(strings.childScheduleTimeline.nowBadge))
            ).assertExists()
            val tokenBeforeMidnight = compositionToken.get()

            clock.millis = localMillis(AFTER_DATE, 0, 1)
            compose.mainClock.advanceTimeBy(60_000)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.autoAdvance = true

            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(NEW_PLAN).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(OLD_PLAN).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText(OLD_PLAN).assertDoesNotExist()
            compose.onNodeWithText(NEW_PLAN).assertExists()
            compose.onNodeWithText(EVENING_SCHEDULE).assertDoesNotExist()
            compose.onNodeWithText(strings.childScheduleTimeline.nowLabel("00:01")).assertExists()
            compose.onNodeWithText(strings.childScheduleTimeline.nowLabel("23:59")).assertDoesNotExist()
            compose.onAllNodesWithText(strings.childScheduleTimeline.nowBadge).assertCountEquals(0)
            assertSame(tokenBeforeMidnight, compositionToken.get())
        } finally {
            db.close()
        }
    }

    private fun localMillis(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    private companion object {
        val BEFORE_DATE = LocalDate(2026, 11, 25)
        val AFTER_DATE = LocalDate(2026, 11, 26)
        const val OLD_PLAN = "midnight-old-plan"
        const val NEW_PLAN = "midnight-new-plan"
        const val EVENING_SCHEDULE = "midnight-evening-schedule"
    }
}

private val LocalAppClock = staticCompositionLocalOf<AppClock> { SystemAppClock }

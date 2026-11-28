package com.hstudy.app.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.AppGraph
import com.hstudy.app.MainActivity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.i18n.AppLanguage
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.ParentGateStringsZh
import com.hstudy.app.i18n.RoleStringsZh
import com.hstudy.app.i18n.SettingsStringsZh
import com.hstudy.app.i18n.TaskListStringsZh
import com.hstudy.app.i18n.appLanguageFromStorage
import com.hstudy.app.platform.NotificationRoutes
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBackAndDeepLinkTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(NotificationPermissionRule())
        .around(composeRule)

    private var previousLanguage: String? = null
    private var previousRole: String? = null
    private var previousPassword: UserPreferences.ParentPasswordRawSnapshot? = null
    private var createdTaskId: Long? = null

    @Before
    fun setUpAuthorizationFixture() = runBlocking {
        AppGraph.init()
        Platform.consumePendingRoute()
        previousLanguage = AppGraph.userPreferences.appLanguage.first()
        previousRole = AppGraph.userPreferences.currentRole.first()
        previousPassword = AppGraph.userPreferences.snapshotParentPasswordRawForTest()
        AppGraph.userPreferences.setAppLanguage(AppLanguage.ZH)
        AppGraph.userPreferences.setParentPassword("task13-parent-password")
        AppGraph.userPreferences.clearRole()
    }

    @After
    fun restoreAuthorizationFixture() = runBlocking {
        Platform.consumePendingRoute()
        createdTaskId?.let { AppGraph.taskRepository.deleteTask(it) }
        createdTaskId = null
        previousLanguage?.let { AppGraph.userPreferences.setAppLanguage(appLanguageFromStorage(it)) }
        if (previousRole == null) AppGraph.userPreferences.clearRole() else AppGraph.userPreferences.setRole(previousRole!!)
        val passwordSnapshot = previousPassword
        if (passwordSnapshot == null) {
            AppGraph.userPreferences.clearParentPassword()
        } else {
            AppGraph.userPreferences.restoreParentPasswordRawForTest(passwordSnapshot)
        }
        finishMainActivityBeforeRuleTeardown()
    }

    private fun finishMainActivityBeforeRuleTeardown() {
        val activity = runCatching { composeRule.activity }.getOrNull() ?: return
        if (activity.isDestroyed) return
        composeRule.runOnIdle { activity.finishAndRemoveTask() }
        composeRule.waitUntil(timeoutMillis = 5_000) { activity.isDestroyed }
    }

    @Test
    fun backAtDepthThreePopsToTaskListThenHome() {
        runBlocking {
            createdTaskId = AppGraph.taskRepository.addTask(
                TaskEntity(title = "Task 13 back navigation", category = TaskCategory.BASIC, points = 1)
            )
        }
        awaitRoleSelection()
        composeRule.onNodeWithText(RoleStringsZh.childEntry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(TaskListStringsZh.checkInTitle).fetchSemanticsNodes().isNotEmpty()
        }
        val taskTitle = "Task 13 back navigation"
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(taskTitle))
            }.isSuccess
        }
        composeRule.onNodeWithText(taskTitle).assertIsDisplayed()
        composeRule.onAllNodesWithText("📝")[0].performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(TaskListStringsZh.proofButton).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(TaskListStringsZh.proofButton).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(AppStringsZh.childProofCapture.title).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(TaskListStringsZh.checkInTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(TaskListStringsZh.checkInTitle).assertIsDisplayed()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun rootBackFinishesNormally() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.runOnIdle { assertTrue(composeRule.activity.isFinishing) }
    }

    @Test
    fun coldMainActivityIntentStoresRouteBeforeAuthorization() {
        composeRule.activityRule.scenario.close()
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java,
        ).apply {
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.WEEKLY_REPORT)
        }
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            scenario.onActivity { activity ->
                assertEquals(NotificationRoutes.WEEKLY_REPORT, Platform.pendingRoute)
                assertFalse(activity.isFinishing)
            }
        } finally {
            scenario.close()
            Platform.consumePendingRoute()
        }
    }

    @Test
    fun coldActivityRecreationDeepLinkWaitsForPasswordThenNavigatesAndConsumesRoute() {
        composeRule.runOnIdle {
            composeRule.activity.intent.putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.WEEKLY_REPORT)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(NotificationRoutes.WEEKLY_REPORT, Platform.pendingRoute) }

        awaitRoleSelection()
        composeRule.onNodeWithText(RoleStringsZh.parentEntry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { assertEquals(NotificationRoutes.WEEKLY_REPORT, Platform.pendingRoute) }
        val passwordField = composeRule.onNode(hasSetTextAction())
        passwordField.performTextReplacement("task13-parent-password")
        composeRule.onNodeWithText(SettingsStringsZh.passwordOkButton).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateTitle)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(AppStringsZh.weeklyTracker.titleParent).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(AppStringsZh.weeklyTracker.titleParent).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(Platform.pendingRoute) }
    }

    @Test
    fun warmMainActivityIntentUpdatesRouteBeforeAuthorization() {
        val intent = Intent(composeRule.activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.SCHEDULE)
        }

        deliverWarmIntent(intent)
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(NotificationRoutes.SCHEDULE, Platform.pendingRoute) }
    }

    @Test
    fun parentDeepLinkWaitsForPasswordThenNavigatesAndConsumesRoute() {
        val intent = Intent(composeRule.activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.WEEKLY_REPORT)
        }
        deliverWarmIntent(intent)
        composeRule.waitForIdle()
        awaitRoleSelection()
        composeRule.onNodeWithText(RoleStringsZh.parentEntry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("wrong-password")
        composeRule.onNodeWithText(SettingsStringsZh.passwordOkButton).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateWrongPasswordError).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { assertEquals(NotificationRoutes.WEEKLY_REPORT, Platform.pendingRoute) }

        val passwordField = composeRule.onNode(hasSetTextAction())
        passwordField.performTextReplacement("task13-parent-password")
        composeRule.onNodeWithText(SettingsStringsZh.passwordOkButton).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateTitle).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(AppStringsZh.weeklyTracker.titleParent).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(AppStringsZh.weeklyTracker.titleParent).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(Platform.pendingRoute) }
    }

    @Test
    fun multipleNotificationClicksBeforeAuthorizationUseTheLatestRoute() {
        val firstIntent = Intent(composeRule.activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.SCHEDULE)
        }
        deliverWarmIntent(firstIntent)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(NotificationRoutes.SCHEDULE, Platform.pendingRoute) }

        val latestIntent = Intent(composeRule.activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.WEEKLY_REPORT)
        }
        deliverWarmIntent(latestIntent)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(NotificationRoutes.WEEKLY_REPORT, Platform.pendingRoute) }

        awaitRoleSelection()
        composeRule.onNodeWithText(RoleStringsZh.parentEntry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(ParentGateStringsZh.passwordGateTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("task13-parent-password")
        composeRule.onNodeWithText(SettingsStringsZh.passwordOkButton).performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(AppStringsZh.weeklyTracker.titleParent).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(AppStringsZh.weeklyTracker.titleParent).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(Platform.pendingRoute) }
    }

    private fun awaitRoleSelection() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(RoleStringsZh.childEntry).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(RoleStringsZh.parentEntry).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun deliverWarmIntent(intent: Intent) {
        composeRule.runOnIdle {
            val activity = composeRule.activity
            val scenarioIntent = activity.intent
            MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
                .apply { isAccessible = true }
                .invoke(activity, intent)
            activity.setIntent(scenarioIntent)
        }
    }

    @Test
    fun parentDeepLinkIsConsumedWithoutNavigationWhenChildRoleIsActive() {
        awaitRoleSelection()
        composeRule.onNodeWithText(RoleStringsZh.childEntry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).fetchSemanticsNodes().isNotEmpty()
        }
        val intent = Intent(composeRule.activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationRoutes.EXTRA_ROUTE, NotificationRoutes.WEEKLY_REPORT)
        }
        deliverWarmIntent(intent)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AppStringsZh.childHome.ctaTodayMustDoTitle).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(AppStringsZh.weeklyTracker.titleParent)
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.runOnIdle { assertNull(Platform.pendingRoute) }
    }
}

/** Grants before the ActivityScenario rule launches MainActivity on Android 13+. */
private class NotificationPermissionRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val context = instrumentation.targetContext
                val output = instrumentation.uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
                )
                ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
                check(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    "POST_NOTIFICATIONS must be granted before MainActivity launches"
                }
            }
            base.evaluate()
        }
    }
}

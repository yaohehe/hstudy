package com.hstudy.app.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.sync.FamilySyncHelper
import com.hstudy.app.i18n.AppStringsZh
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.platform.FileSharer
import com.hstudy.app.platform.ShareResult
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import com.hstudy.app.ui.sync.FamilySyncExportShareButton
import com.hstudy.app.ui.theme.HStudyTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyShareResultTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val strings = AppStringsZh.settings
    private lateinit var cacheDirectory: File
    private lateinit var cacheDir: Path

    @Before
    fun setUp() {
        cacheDirectory = File(context.cacheDir, "family-share-ui-${UUID.randomUUID()}").apply {
            assertTrue(mkdirs())
        }
        cacheDir = cacheDirectory.path.replace('\\', '/').toPath()
    }

    @After
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun injectedFileSharerFailureDisplaysFailureAndNeverClaimsShareSuccess() {
        val db = testDatabase(context)
        try {
            runBlocking {
                db.taskDao().insert(TaskEntity(title = "task", category = TaskCategory.BASIC, points = 5))
                db.rewardDao().insertReward(RewardEntity(name = "reward", pointsCost = 20))
            }
            val helper = FamilySyncHelper(
                database = db,
                userPreferences = UserPreferences(testDataStore(context, "family-share-ui-${UUID.randomUUID()}")),
                transactionRunner = roomTransactionRunner(db),
                fs = FileSystem.SYSTEM,
                cacheDir = cacheDir,
                fileSharer = object : FileSharer {
                    override fun shareTextFile(
                        fileName: String,
                        content: String,
                        mimeType: String,
                        onResult: (ShareResult) -> Unit,
                    ) = ShareResult.Failed("injected chooser failure")

                    override fun pickTextFile(onResult: (String?) -> Unit) = Unit
                },
            )

            renderButton(
                onExport = { helper.exportControlConfig() },
                onShare = { path, _ -> helper.shareFile(path, strings.exportChooserTitle) },
            )
            compose.onNodeWithText(strings.pushSettingsButton).performClick()
            awaitText(strings.shareFailedMessage("injected chooser failure"))

            compose.onNodeWithText(strings.shareFailedMessage("injected chooser failure")).assertIsDisplayed()
            compose.onNodeWithText(strings.settingsExportedMessage).assertIsDisplayed()
            compose.onNodeWithText(strings.sharePresentedMessage).assertDoesNotExist()
            compose.onNodeWithText(strings.shareCompletedMessage).assertDoesNotExist()
            compose.onNodeWithText(strings.shareCancelledMessage).assertDoesNotExist()
        } finally {
            db.close()
        }
    }

    @Test
    fun presentedResultDisplaysPresentedRatherThanCompleted() {
        renderButton(onShare = { _, _ -> ShareResult.Presented })

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.sharePresentedMessage)

        compose.onNodeWithText(strings.sharePresentedMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.shareCompletedMessage).assertDoesNotExist()
    }

    @Test
    fun completedResultDisplaysCompleted() {
        renderButton(onShare = { _, _ -> ShareResult.Completed })

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.shareCompletedMessage)

        compose.onNodeWithText(strings.shareCompletedMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.sharePresentedMessage).assertDoesNotExist()
    }

    @Test
    fun cancelledResultDisplaysCancelledRatherThanSuccess() {
        renderButton(onShare = { _, _ -> ShareResult.Cancelled })

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.shareCancelledMessage)

        compose.onNodeWithText(strings.shareCancelledMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.shareCompletedMessage).assertDoesNotExist()
        compose.onNodeWithText(strings.sharePresentedMessage).assertDoesNotExist()
    }

    @Test
    fun synchronousTerminalCallbackIsNotOverwrittenByPresentedReturn() {
        renderButton(
            onShare = { _, onResult ->
                onResult(ShareResult.Completed)
                ShareResult.Presented
            },
        )

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.shareCompletedMessage)

        compose.onNodeWithText(strings.shareCompletedMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.sharePresentedMessage).assertDoesNotExist()
    }

    @Test
    fun staleAsyncCallbackCannotOverwriteNewShareAttempt() {
        val callbacks = mutableListOf<(ShareResult) -> Unit>()
        renderButton(
            onShare = { _, onResult ->
                callbacks += onResult
                ShareResult.Presented
            },
        )

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.sharePresentedMessage)
        compose.waitForIdle()
        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        compose.waitForIdle()
        assertEquals(2, callbacks.size)

        compose.runOnIdle { callbacks[0](ShareResult.Completed) }
        compose.waitForIdle()

        compose.onNodeWithText(strings.sharePresentedMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.shareCompletedMessage).assertDoesNotExist()
        compose.onNodeWithText(strings.shareCancelledMessage).assertDoesNotExist()

        compose.runOnIdle { callbacks[1](ShareResult.Cancelled) }
        awaitText(strings.shareCancelledMessage)
        compose.onNodeWithText(strings.shareCancelledMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.shareCompletedMessage).assertDoesNotExist()
    }

    @Test
    fun exportFailureDoesNotDisplayExportedState() {
        renderButton(onExport = { Result.failure(IllegalStateException("export failed")) })

        compose.onNodeWithText(strings.pushSettingsButton).performClick()
        awaitText(strings.exportFailedMessage("export failed"))

        compose.onNodeWithText(strings.exportFailedMessage("export failed")).assertIsDisplayed()
        compose.onNodeWithText(strings.settingsExportedMessage).assertDoesNotExist()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun renderButton(
        onExport: suspend () -> Result<String> = { Result.success("unused.json") },
        onShare: (String, (ShareResult) -> Unit) -> ShareResult = { _, _ -> ShareResult.Presented },
    ) {
        compose.setContent {
            HStudyTheme {
                CompositionLocalProvider(LocalDisplayLanguage provides DisplayLanguage.ZH) {
                    FamilySyncExportShareButton(
                        label = strings.pushSettingsButton,
                        processingLabel = strings.processingLabel,
                        exportedMessage = strings.settingsExportedMessage,
                        sharePresentedMessage = strings.sharePresentedMessage,
                        shareCompletedMessage = strings.shareCompletedMessage,
                        shareCancelledMessage = strings.shareCancelledMessage,
                        shareFailedMessage = strings.shareFailedMessage,
                        exportFailedMessage = strings.exportFailedMessage,
                        onExport = onExport,
                        onShare = onShare,
                    )
                }
            }
        }
    }
}

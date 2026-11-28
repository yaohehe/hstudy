package com.hstudy.app.ui.navigation

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationRouteCoverageTest {
    private val closedScreens = listOf(
        Screen.RoleSelect,
        Screen.ParentHome,
        Screen.ParentTaskManage,
        Screen.ParentTaskEdit,
        Screen.ParentPoints,
        Screen.ParentRewardManage,
        Screen.ParentRewardEdit,
        Screen.ParentDashboard,
        Screen.ParentWeeklyReport,
        Screen.ParentGrowthReport,
        Screen.ParentProofReview,
        Screen.ParentMistakeReview,
        Screen.ParentSettings,
        Screen.ParentApproval,
        Screen.ParentAssignTask,
        Screen.ParentTaskTemplate,
        Screen.ParentWishGoalManage,
        Screen.ParentScheduleManage,
        Screen.ParentTutoringAdvice,
        Screen.ChildHome,
        Screen.ChildTaskList,
        Screen.ChildPoints,
        Screen.ChildRewardStore,
        Screen.ChildWeeklyView,
        Screen.ChildLuckyDraw,
        Screen.ChildMistakeList,
        Screen.ChildMistakeAdd,
        Screen.ChildMistakeDetail,
        Screen.ChildPomodoro,
        Screen.ChildFocusTraining,
        Screen.ChildMemoryTraining,
        Screen.ChildDailyPlan,
        Screen.ChildFocusStats,
        Screen.ChildProofCapture,
        Screen.PhotoViewer,
        Screen.ChildHabitChallenge,
        Screen.ChildGoalTracker,
        Screen.ChildEfficiencyReport,
        Screen.ChildWishGoal,
        Screen.ChildScheduleTimeline,
        Screen.ChildPet,
        Screen.ChildDailyTarget,
        Screen.ChildAchievements,
    )

    @Test
    fun everyClosedScreenRouteHasAnAppNavGraphBranch() {
        assertTrue(closedScreens.isNotEmpty(), "The closed Screen registry must not be empty")
        assertEquals(closedScreens.size, closedScreens.map { it.route }.toSet().size, "Screen routes must be unique")

        val declaredNames = Regex("""\bdata\s+object\s+(\w+)\s*:\s*Screen\b""")
            .findAll(maskKotlinCommentsAndStrings(readScreenSource()))
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            closedScreens.map { it::class.simpleName }.toSet(),
            declaredNames,
            "The closed Screen registry must enumerate every Screen data object",
        )

        val graphSource = maskKotlinCommentsAndStrings(readAppNavGraphSource())
        val missing = closedScreens.filter { screen ->
            val name = screen::class.simpleName!!
            !hasRealDispatchBranch(graphSource, name, parameterized = screen.route.contains("{"))
        }
        assertEquals(emptyList(), missing.map { it.route }, "Every Screen route must have a real AppNavGraph dispatch branch")
    }

    @Test
    fun commentAndStringOnlyRouteOccurrencesDoNotCountAsDispatchBranches() {
        val tripleQuote = "\"\"\""
        val source = listOf(
            "// route == Screen.Ghost.route -> Unit",
            "/* outer /* route.startsWith(argPrefix(Screen.Ghost.route)) -> Unit */ still commented */",
            "val text = \"route == Screen.Ghost.route -> Unit\"",
            "val raw = $tripleQuote",
            "route.startsWith(argPrefix(Screen.Ghost.route)) -> Unit",
            tripleQuote,
        ).joinToString("\n")

        val masked = maskKotlinCommentsAndStrings(source)
        assertFalse(
            hasRealDispatchBranch(masked, "Ghost", parameterized = false),
            "Comments and strings must not satisfy route coverage",
        )
        assertFalse(
            hasRealDispatchBranch(masked, "Ghost", parameterized = true),
            "Raw strings must not satisfy parameterized route coverage",
        )
        assertTrue(
            hasRealDispatchBranch(
                maskKotlinCommentsAndStrings("when { route == Screen.Ghost.route -> Unit }"),
                "Ghost",
                parameterized = false,
            ),
        )
        assertTrue(
            hasRealDispatchBranch(
                maskKotlinCommentsAndStrings(
                    "when { route.startsWith(argPrefix(Screen.Ghost.route)) -> Unit }"
                ),
                "Ghost",
                parameterized = true,
            ),
        )
    }

    @Test
    fun freeTextRouteArgumentsEncodeAndRoundTripEdgeCases() {
        val cases = listOf(
            "family/photo.jpg" to "family%2Fphoto.jpg",
            "100%" to "100%25",
            "" to "",
            "任务/50%" to "任务%2F50%25",
        )
        cases.forEach { (input, encoded) ->
            assertEquals(encoded, encodeRouteArg(input), "Unexpected encoding for <$input>")
            assertEquals(input, decodeRouteArg(encoded), "Round trip failed for <$input>")
            assertEquals("photo_viewer/$encoded", Screen.PhotoViewer.createRoute(input))
        }
    }

    @Test
    fun malformedPercentEscapesAreReturnedUnchanged() {
        listOf("%", "%ZZ", "bad%2").forEach { malformed ->
            assertEquals(malformed, decodeRouteArg(malformed))
        }
    }

    @Test
    fun parameterizedRoutesKeepSeparatorsAndEmptyArguments() {
        assertEquals(
            "child_proof_capture/7/family%2Fphoto.jpg",
            Screen.ChildProofCapture.createRoute(7, "family/photo.jpg"),
        )
        assertEquals("photo_viewer/", Screen.PhotoViewer.createRoute(""))
    }

    @Test
    fun invalidNumericRouteArgumentsFallBackToZero() {
        val invalidValues = listOf("", "abc", "12x", "9223372036854775808")
        invalidValues.forEach { value ->
            assertEquals(0L, numericRouteArg("parent_task_edit/$value", Screen.ParentTaskEdit.route))
            assertEquals(0L, numericRouteArg("parent_reward_edit/$value", Screen.ParentRewardEdit.route))
            assertEquals(0L, numericRouteArg("child_mistake_detail/$value", Screen.ChildMistakeDetail.route))
            assertEquals(
                0L,
                numericRouteArg("child_proof_capture/$value/title", Screen.ChildProofCapture.route),
            )
        }
    }

    @Test
    fun validNumericRouteArgumentsArePreserved() {
        assertEquals(42L, numericRouteArg("parent_task_edit/42", Screen.ParentTaskEdit.route))
        assertEquals(42L, numericRouteArg("parent_reward_edit/42", Screen.ParentRewardEdit.route))
        assertEquals(42L, numericRouteArg("child_mistake_detail/42", Screen.ChildMistakeDetail.route))
        assertEquals(42L, numericRouteArg("child_proof_capture/42/title", Screen.ChildProofCapture.route))
    }

    /** Mask comments, regular/character literals, and raw strings without changing offsets. */
    private fun maskKotlinCommentsAndStrings(source: String): String {
        val masked = source.toCharArray()
        var state = "code"
        var blockDepth = 0
        var i = 0
        fun blank(index: Int) {
            if (masked[index] != '\n') masked[index] = ' '
        }
        while (i < source.length) {
            val next = source.getOrNull(i + 1)
            when (state) {
                "line_comment" -> {
                    if (source[i] == '\n') state = "code" else blank(i)
                }
                "block_comment" -> {
                    when {
                        source[i] == '/' && next == '*' -> {
                            blank(i); blank(i + 1); blockDepth++; i++
                        }
                        source[i] == '*' && next == '/' -> {
                            blank(i); blank(i + 1); blockDepth--; i++
                            if (blockDepth == 0) state = "code"
                        }
                        else -> blank(i)
                    }
                }
                "string" -> {
                    when {
                        source[i] == '\\' -> {
                            blank(i)
                            if (i + 1 < source.length && source[i + 1] != '\n') {
                                blank(i + 1); i++
                            }
                        }
                        source[i] == '"' -> { blank(i); state = "code" }
                        else -> blank(i)
                    }
                }
                "char" -> {
                    when {
                        source[i] == '\\' -> {
                            blank(i)
                            if (i + 1 < source.length) { blank(i + 1); i++ }
                        }
                        source[i] == '\'' -> { blank(i); state = "code" }
                        else -> blank(i)
                    }
                }
                "raw_string" -> {
                    if (source.startsWith("\"\"\"", i)) {
                        blank(i); blank(i + 1); blank(i + 2); i += 2; state = "code"
                    } else {
                        blank(i)
                    }
                }
                else -> {
                    when {
                        source[i] == '/' && next == '/' -> {
                            blank(i); blank(i + 1); i++; state = "line_comment"
                        }
                        source[i] == '/' && next == '*' -> {
                            blank(i); blank(i + 1); i++; blockDepth = 1; state = "block_comment"
                        }
                        source.startsWith("\"\"\"", i) -> {
                            blank(i); blank(i + 1); blank(i + 2); i += 2; state = "raw_string"
                        }
                        source[i] == '"' -> { blank(i); state = "string" }
                        source[i] == '\'' -> { blank(i); state = "char" }
                    }
                }
            }
            i++
        }
        return String(masked)
    }

    /** Match only a real equality/prefix branch, never a route reference in prose or data. */
    private fun hasRealDispatchBranch(masked: String, screenName: String, parameterized: Boolean): Boolean {
        val name = Regex.escape(screenName)
        val branch = if (parameterized) {
            """\broute\s*\.\s*startsWith\s*\(\s*argPrefix\s*\(\s*Screen\.$name\.route\s*\)\s*\)\s*->"""
        } else {
            """\broute\s*==\s*Screen\.$name\.route\s*->"""
        }
        return Regex(branch).containsMatchIn(masked)
    }

    private fun readNavigationSource(fileName: String): String {
        val relative = "src/commonMain/kotlin/com/hstudy/app/ui/navigation/$fileName"
        val candidates = listOf(relative, "composeApp/$relative", "../composeApp/$relative")
        return candidates.firstNotNullOfOrNull { path ->
            runCatching { FileSystem.SYSTEM.read(path.toPath()) { readUtf8() } }.getOrNull()
        } ?: error("$fileName not found from Gradle test working directory")
    }

    private fun readScreenSource(): String = readNavigationSource("Screen.kt")

    private fun readAppNavGraphSource(): String = readNavigationSource("AppNavGraph.kt")
}

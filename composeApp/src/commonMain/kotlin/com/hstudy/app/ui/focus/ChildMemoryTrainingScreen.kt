package com.hstudy.app.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.preferences.MemoryTrainingState
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class MemoryTrainingRestore(
    val loaded: Boolean = false,
    val state: MemoryTrainingState? = null,
)

/**
 * 数字记忆广度训练：短暂显示一串数字后让孩子回忆，训练工作记忆（与舒尔特方格的选择性注意互补）。
 * 工作记忆是学习能力的基础，记住的数字越多说明工作记忆容量越大。
 */
@Composable
fun ChildMemoryTrainingScreen(
    onNavigateBack: () -> Unit,
    userPreferences: UserPreferences = AppGraph.userPreferences,
) {
    val app = AppGraph
    val strings = LocalStrings.current.focus
    val common = LocalStrings.current.common
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val restored by remember(userPreferences) {
        userPreferences.memoryTrainingState.map { MemoryTrainingRestore(loaded = true, state = it) }
    }.collectAsState(initial = MemoryTrainingRestore())
    val state = restored.state ?: MemoryTrainingState()
    var userInput by remember { mutableStateOf("") }

    // 展示截止时间使用墙钟持久化；进程在 showing 阶段被杀后只等待剩余时长，不重新计时。
    // 到期写入带 phase/sequence/deadline 守卫，旧定时器不能把新一轮 SHOWING 提前改成 INPUT。
    LaunchedEffect(restored.loaded, state.phase, state.sequence, state.showUntilEpochMs) {
        if (restored.loaded && state.phase == MemoryTrainingState.Phase.SHOWING) {
            val expectedSequence = state.sequence
            val expectedDeadline = state.showUntilEpochMs
            val remaining = expectedDeadline - com.hstudy.app.ui.dt.nowMillis()
            if (remaining > 0L) delay(remaining)
            userPreferences.updateMemoryTrainingState { current ->
                current.afterShow(expectedSequence, expectedDeadline)
            }
        }
    }

    fun startRound(digits: Int) {
        val expectedPhase = state.phase
        val expectedSequence = state.sequence
        val sequence = (1..digits).map { (0..9).random() }.joinToString("")
        val showUntilEpochMs = com.hstudy.app.ui.dt.nowMillis() + digits * 800L + 500L
        userInput = ""
        scope.launch {
            userPreferences.updateMemoryTrainingState { current ->
                current.startRound(
                    sequence = sequence,
                    level = digits,
                    showUntilEpochMs = showUntilEpochMs,
                    expectedPhase = expectedPhase,
                    expectedSequence = expectedSequence,
                )
            }
        }
    }

    fun checkAnswer() {
        val expectedSequence = state.sequence
        val answer = userInput
        scope.launch {
            var correct = false
            val changed = userPreferences.updateMemoryTrainingState { current ->
                val next = current.answer(answer, expectedSequence)
                correct = next != current && next.phase == MemoryTrainingState.Phase.RESULT && next.lastCorrect
                next
            }
            if (changed && correct) {
                userInput = ""
                com.hstudy.app.ui.common.AchievementManager.evaluate(app)
            }
        }
    }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.memoryTitle, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!restored.loaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    strings.memoryHeading,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.memoryHowTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (state.bestLevel > 0) {
                    Text(
                        strings.memoryBestLine(state.bestLevel),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (state.phase) {
                    MemoryTrainingState.Phase.IDLE -> {
                        Text(strings.memoryReadyLine(state.level), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { startRound(state.level) }) { Text(strings.startTrainingButton) }
                    }
                    MemoryTrainingState.Phase.SHOWING -> {
                        Text(strings.memoryShowPrompt, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            state.sequence,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 8.sp,
                        )
                        Text(
                            strings.memoryWatchHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MemoryTrainingState.Phase.INPUT -> {
                        Text(strings.memoryInputPrompt(state.level), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it.filter { c -> c.isDigit() }.take(state.level) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.7f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        Button(
                            onClick = { checkAnswer() },
                            enabled = userInput.length == state.level,
                        ) { Text(common.confirmButton) }
                    }
                    MemoryTrainingState.Phase.RESULT -> {
                        if (state.lastCorrect) {
                            Text(
                                strings.memorySuccessLine(state.level, state.level),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { startRound(state.level + 1) }) {
                                    Text(strings.memoryChallengeButton(state.level + 1))
                                }
                                OutlinedButton(onClick = { startRound(state.level) }) {
                                    Text(strings.memoryPracticeAgainButton)
                                }
                            }
                        } else {
                            Text(strings.memoryMissPrompt, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                state.sequence,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                letterSpacing = 6.sp,
                            )
                            Text(
                                strings.memoryEncourageHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { startRound(state.level) }) { Text(strings.memoryRetryButton) }
                                if (state.level > 3) {
                                    OutlinedButton(onClick = { startRound(state.level - 1) }) {
                                        Text(strings.memoryStepBackButton(state.level - 1))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

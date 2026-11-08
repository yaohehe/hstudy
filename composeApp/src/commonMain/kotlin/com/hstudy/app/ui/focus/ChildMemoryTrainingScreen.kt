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
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 数字记忆广度训练：短暂显示一串数字后让孩子回忆，训练工作记忆（与舒尔特方格的选择性注意互补）。
 * 工作记忆是学习能力的基础，记住的数字越多说明工作记忆容量越大。
 */
@Composable
fun ChildMemoryTrainingScreen(onNavigateBack: () -> Unit) {
    val app = AppGraph
    val strings = LocalStrings.current.focus
    val common = LocalStrings.current.common
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 阶段：idle(未开始) / showing(展示数字) / input(输入回忆) / result(结果)
    var phase by remember { mutableStateOf("idle") }
    var level by remember { mutableIntStateOf(3) }       // 当前数字位数
    var sequence by remember { mutableStateOf("") }     // 要记忆的数字串
    var userInput by remember { mutableStateOf("") }
    var lastCorrect by remember { mutableStateOf(false) }
    var bestLevel by remember { mutableIntStateOf(0) }  // 本次最高记住的位数

    fun startRound(digits: Int) {
        level = digits
        sequence = (1..digits).map { (0..9).random() }.joinToString("")
        userInput = ""
        phase = "showing"
        scope.launch {
            delay(digits * 800L + 500L)  // 展示时长随位数增加
            phase = "input"
        }
    }

    fun checkAnswer() {
        lastCorrect = userInput == sequence
        if (lastCorrect) {
            if (level > bestLevel) bestLevel = level
            val points = 0  // 训练不直接加分（防止无限刷分）
            scope.launch {
                com.hstudy.app.ui.common.AchievementManager.evaluate(app)
            }
        }
        phase = "result"
    }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.memoryTitle, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(strings.memoryHeading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.memoryHowTo,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)

            if (bestLevel > 0) {
                Text(strings.memoryBestLine(bestLevel), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (phase) {
                "idle" -> {
                    Text(strings.memoryReadyLine(level), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { startRound(level) }) { Text(strings.startTrainingButton) }
                }
                "showing" -> {
                    Text(strings.memoryShowPrompt, style = MaterialTheme.typography.bodyMedium)
                    Text(sequence, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 8.sp)
                    Text(strings.memoryWatchHint, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "input" -> {
                    Text(strings.memoryInputPrompt(level), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it.filter { c -> c.isDigit() }.take(level) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.7f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                    )
                    Button(onClick = { checkAnswer() }, enabled = userInput.length == level) { Text(common.confirmButton) }
                }
                "result" -> {
                    if (lastCorrect) {
                        Text(strings.memorySuccessLine(level, level),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { startRound(level + 1) }) { Text(strings.memoryChallengeButton(level + 1)) }
                            OutlinedButton(onClick = { startRound(level) }) { Text(strings.memoryPracticeAgainButton) }
                        }
                    } else {
                        Text(strings.memoryMissPrompt, style = MaterialTheme.typography.bodyMedium)
                        Text(sequence, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary, letterSpacing = 6.sp)
                        Text(strings.memoryEncourageHint, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { startRound(level) }) { Text(strings.memoryRetryButton) }
                            if (level > 3) {
                                OutlinedButton(onClick = { startRound(level - 1) }) { Text(strings.memoryStepBackButton(level - 1)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

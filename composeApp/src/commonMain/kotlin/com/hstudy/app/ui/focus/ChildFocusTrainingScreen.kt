package com.hstudy.app.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 舒尔特方格专注力训练：按 1→25 的顺序尽快点击，经典的注意力集中训练工具。
 * 作为专注前的"注意力热身"，帮助孩子快速进入专注状态。
 */
@Composable
fun ChildFocusTrainingScreen(onNavigateBack: () -> Unit) {
    val strings = LocalStrings.current.focus
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var grid by remember { mutableStateOf((1..25).shuffled()) }
    var nextExpected by remember { mutableIntStateOf(1) }
    var startTime by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var mistakes by remember { mutableIntStateOf(0) }
    var rewarded by remember { mutableStateOf(false) }
    val bestMs by app.userPreferences.focusTrainingBestMs.collectAsState(initial = 0L)
    var isNewRecord by remember { mutableStateOf(false) }
    val trainingCount by app.userPreferences.focusTrainingCount.collectAsState(initial = 0)

    // 计时器：运行时每100ms刷新用时
    LaunchedEffect(running) {
        if (running) {
            while (true) {
                elapsedMs = com.hstudy.app.ui.dt.nowMillis() - startTime
                delay(100)
            }
        }
    }

    fun start() {
        grid = (1..25).shuffled()
        nextExpected = 1
        elapsedMs = 0
        mistakes = 0
        finished = false
        rewarded = false
        isNewRecord = false
        startTime = com.hstudy.app.ui.dt.nowMillis()
        running = true
    }

    fun onCellClick(num: Int) {
        if (!running) return
        if (num == nextExpected) {
            if (nextExpected == 25) {
                // 完成
                elapsedMs = com.hstudy.app.ui.dt.nowMillis() - startTime
                running = false
                finished = true
                val seconds = elapsedMs / 1000f
                // 检测个人最佳记录（自我竞赛）
                val isRecord = bestMs != 0L && elapsedMs < bestMs
                if (bestMs == 0L || elapsedMs < bestMs) {
                    scope.launch { app.userPreferences.setFocusTrainingBest(elapsedMs) }
                }
                isNewRecord = isRecord
                val points = 0 // 训练不直接加分（防止无限刷分，积分通过学习活动获取）
                if (!rewarded) {
                    rewarded = true
                    scope.launch {
                        app.userPreferences.incrementFocusTrainingCount()
                        com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                        snackbarHostState.showSnackbar(strings.trainingDoneSnackbar(com.hstudy.app.ui.dt.strFormat("%.1f", seconds)))
                    }
                }
            } else {
                nextExpected++
            }
        } else {
            mistakes++
        }
    }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.trainingTitle, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.schulteTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.schulteHowTo,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)

            // 用时显示
            Text(strings.elapsedValue(com.hstudy.app.ui.dt.strFormat("%.1f", elapsedMs / 1000f)),
                fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            // 个人最佳记录（自我竞赛目标）
            if (bestMs > 0L) {
                Text(strings.personalBestValue(com.hstudy.app.ui.dt.strFormat("%.1f", bestMs / 1000f)),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary)
            }
            // 累计训练次数（坚持训练的见证）
            if (trainingCount > 0) {
                Text(strings.totalSessionsLine(trainingCount), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                finished -> {
                    val seconds = elapsedMs / 1000f
                    val verdict = when {
                        seconds < 25 -> strings.verdictExcellent
                        seconds < 40 -> strings.verdictGood
                        else -> strings.verdictPractice
                    }
                    if (isNewRecord) {
                        Text(strings.newRecordBanner,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(strings.finishedLine(com.hstudy.app.ui.dt.strFormat("%.1f", seconds), mistakes),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(verdict, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    Button(onClick = { start() }) { Text(strings.tryAgainButton) }
                }
                !running -> {
                    Text(strings.dailyPracticeHint,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { start() }) { Text(strings.startTrainingButton) }
                }
                else -> {
                    Text(strings.nextTargetLabel(nextExpected), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // 5x5 方格（仅在进行中/完成后显示）
            if (running || finished) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 5) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 5) {
                                val num = grid[row * 5 + col]
                                val isDone = num < nextExpected
                                val isNext = num == nextExpected && running
                                val bg = when {
                                    isDone -> MaterialTheme.colorScheme.surfaceVariant
                                    isNext -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(bg, RoundedCornerShape(10.dp))
                                        .clickable { onCellClick(num) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$num",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

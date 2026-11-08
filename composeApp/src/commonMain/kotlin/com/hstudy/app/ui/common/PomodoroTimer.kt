package com.hstudy.app.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.feedback
import com.hstudy.app.ui.dt.monotonicMillis
import kotlinx.coroutines.delay

enum class PomodoroState {
    IDLE, BREATHING, RUNNING, PAUSED, BREAK, READY_NEXT
}

/**
 * 番茄钟组件 - 显示倒计时圆环和控制按钮
 * @param focusMinutes 专注时长（分钟）
 * @param breakMinutes 休息时长（分钟）
 * @param onComplete 完成一个番茄钟时的回调
 */
@Composable
fun PomodoroTimer(
    focusMinutes: Int = 25,
    breakMinutes: Int = 5,
    skipBreathing: Boolean = false,
    onComplete: (Int) -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current.pomodoro
    val currentStrings by rememberUpdatedState(strings)
    var state by remember { mutableStateOf(PomodoroState.IDLE) }
    var remainingSeconds by remember { mutableIntStateOf(focusMinutes * 60) }
    var totalSeconds by remember { mutableIntStateOf(focusMinutes * 60) }
    var completedPomodoros by remember { mutableIntStateOf(0) }
    // 目标结束时间戳（基于 [monotonicMillis] 单调时钟，息屏/后台期间仍持续计时）
    var endTimeMillis by remember { mutableLongStateOf(0L) }

    // 通知父组件计时器是否活跃（用于禁用时长设置等）
    val isActive = state != PomodoroState.IDLE
    LaunchedEffect(isActive) { onActiveChange(isActive) }

    // ===== 专注前呼吸引导（帮助孩子平静心神、快速进入专注状态）=====
    val totalBreathCycles = 3
    var breathCycle by remember { mutableIntStateOf(0) }
    var breathIn by remember { mutableStateOf(true) } // true=吸气 false=呼气
    val breathScale by animateFloatAsState(
        targetValue = if (breathIn) 1f else 0.55f,
        animationSpec = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
        label = "breathScale"
    )

    // 呼吸引导流程：吸气4秒→呼气4秒为一个循环，完成后自动开始专注
    LaunchedEffect(state) {
        if (state == PomodoroState.BREATHING) {
            breathCycle = 0
            breathIn = true
            while (breathCycle < totalBreathCycles) {
                breathIn = true
                delay(4000)
                breathIn = false
                delay(4000)
                breathCycle++
            }
            // 呼吸完成，自动进入专注
            totalSeconds = focusMinutes * 60
            remainingSeconds = totalSeconds
            endTimeMillis = monotonicMillis() + totalSeconds * 1000L
            state = PomodoroState.RUNNING
        }
    }

    // 计时器核心逻辑：基于时间戳计算剩余时间，避免息屏后 delay 被挂起导致停表
    LaunchedEffect(state) {
        if (state == PomodoroState.RUNNING || state == PomodoroState.BREAK) {
            var notifCounter = 0
            while (true) {
                val remaining = ((endTimeMillis - monotonicMillis()) / 1000L).toInt()
                remainingSeconds = if (remaining > 0) remaining else 0
                if (remainingSeconds <= 0) break
                // 每15秒更新一次常驻进度通知（让孩子息屏后也能看进度）
                notifCounter++
                if (notifCounter % 60 == 1) { // 250ms * 60 = 15s
                    val min = remainingSeconds / 60
                    val sec = remainingSeconds % 60
                    Platform.feedback.showRunningNotification(
                        title = if (state == PomodoroState.BREAK) currentStrings.phaseBreak else currentStrings.phaseFocus,
                        text = currentStrings.remainTime(min, sec),
                        progressPercent = if (totalSeconds > 0) (totalSeconds - remainingSeconds) * 100 / totalSeconds else 0
                    )
                }
                delay(250) // 高频刷新，息屏恢复后能立即追上正确时间
            }
            // 时间到
            Platform.feedback.cancelRunningNotification()
            if (state == PomodoroState.RUNNING) {
                completedPomodoros++
                onComplete(focusMinutes)
                // 完成提示音+震动（让孩子知道专注结束了）
                playCompletionAlert()
                // 切换到休息
                state = PomodoroState.BREAK
                totalSeconds = breakMinutes * 60
                remainingSeconds = totalSeconds
                endTimeMillis = monotonicMillis() + totalSeconds * 1000L
            } else if (state == PomodoroState.BREAK) {
                // 休息结束→过渡引导状态（而非直接回IDLE，给孩子一个明确的下一步）
                playCompletionAlert()
                state = PomodoroState.READY_NEXT
            }
        } else {
            // 非计时状态时取消通知
            Platform.feedback.cancelRunningNotification()
        }
    }

    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    val isBreak = state == PomodoroState.BREAK

    // 专注中途鼓励（过半时显示，给孩子打气）
    // remember 存的是候选项的**索引**而不是文案本身：语言在计时进行中切换时组合会重新执行，
    // 于是用新的 strings 实例重新取词，而 key 仍是 completedPomodoros —— 同一轮番茄钟不会重新抽签，
    // random() 的调用次数与取值范围也没变（Ruling 20(c)：不改控制流，只改"记住什么"）。
    val cheerCandidates = listOf(
        strings.cheerHalfway,
        strings.cheerProudest,
        strings.cheerAlmostDone,
        strings.cheerBrainWorking,
        strings.cheerEveryMinute
    )
    val cheerIndex = remember(completedPomodoros) { cheerCandidates.indices.random() }
    val halfwayEncouragement = cheerCandidates.getOrElse(cheerIndex) { cheerCandidates.first() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 圆环进度显示
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state == PomodoroState.BREATHING) {
                // 呼吸动画圆圈：随吸气放大、呼气缩小，引导深呼吸
                Box(
                    modifier = Modifier
                        .size((200 * breathScale).dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (breathIn) strings.breatheIn else strings.breatheOut,
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strings.deepBreathCount((breathCycle + 1).coerceAtMost(totalBreathCycles), totalBreathCycles),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
            // 背景圆环
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val progressColor = if (isBreak)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.primary

            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 12.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Progress
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 中间文字
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                Text(
                    text = com.hstudy.app.ui.dt.strFormat("%02d:%02d", minutes, seconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                Text(
                    text = if (isBreak) strings.phaseBreak else if (state == PomodoroState.IDLE) strings.phaseStartReady
                        else if (state == PomodoroState.READY_NEXT) strings.phaseBreakOver else strings.phaseFocus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 已完成番茄数
        Text(
            text = "🍅 × ${completedPomodoros}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 专注中途鼓励（过半时显示，给孩子打气）
        if (state == PomodoroState.RUNNING && progress < 0.5f) {
            Text(
                halfwayEncouragement,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        // 休息结束过渡提示
        if (state == PomodoroState.READY_NEXT) {
            Text(
                strings.breakOverSummary(completedPomodoros),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 呼吸引导说明
        if (state == PomodoroState.BREATHING) {
            Text(
                strings.breathingGuide,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 专注后科学休息建议（与专注前呼吸引导呼应，形成科学专注闭环）
        if (state == PomodoroState.BREAK) {
            val breakTips = listOf(
                strings.tipWalkAround,
                strings.tipLookFarAway,
                strings.tipDrinkWater,
                strings.tipBreathe,
                strings.tipStretch,
                strings.tipListenMusic
            )
            val tip = breakTips[completedPomodoros % breakTips.size]
            Card(
                modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.breakCardTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(strings.breakCardNote, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 控制按钮
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state) {
                PomodoroState.IDLE -> {
                    FilledTonalButton(onClick = {
                        if (skipBreathing) {
                            // 跳过呼吸，直接开始专注
                            totalSeconds = focusMinutes * 60
                            remainingSeconds = totalSeconds
                            endTimeMillis = monotonicMillis() + totalSeconds * 1000L
                            state = PomodoroState.RUNNING
                        } else {
                            // 先进入专注前呼吸引导，帮助平静心神
                            state = PomodoroState.BREATHING
                        }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.startFocusButton)
                    }
                }
                PomodoroState.BREATHING -> {
                    TextButton(onClick = {
                        // 跳过呼吸引导，直接开始专注
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                        endTimeMillis = monotonicMillis() + totalSeconds * 1000L
                        state = PomodoroState.RUNNING
                    }) {
                        Text(strings.skipBreathingButton)
                    }
                }
                PomodoroState.RUNNING -> {
                    OutlinedButton(onClick = { state = PomodoroState.PAUSED }) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.pauseButton)
                    }
                    TextButton(onClick = {
                        state = PomodoroState.IDLE
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.stopButton)
                    }
                }
                PomodoroState.PAUSED -> {
                    FilledTonalButton(onClick = {
                        // 从暂停恢复：以当前剩余时间重设结束时间戳
                        endTimeMillis = monotonicMillis() + remainingSeconds * 1000L
                        state = PomodoroState.RUNNING
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.resumeButton)
                    }
                    TextButton(onClick = {
                        state = PomodoroState.IDLE
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.stopButton)
                    }
                }
                PomodoroState.BREAK -> {
                    TextButton(onClick = {
                        state = PomodoroState.READY_NEXT
                    }) {
                        Text(strings.skipBreakButton)
                    }
                }
                PomodoroState.READY_NEXT -> {
                    // 休息结束过渡引导：明确告诉孩子下一步
                    FilledTonalButton(onClick = {
                        state = PomodoroState.BREATHING
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.startNextRoundButton)
                    }
                    TextButton(onClick = {
                        state = PomodoroState.IDLE
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                    }) {
                        Text(strings.restFirstButton)
                    }
                }
            }
        }
    }
}

/**
 * 完成提示音 + 震动（让孩子知道专注/休息结束了）。
 * 原实现直接调用 RingtoneManager + Vibrator（需 Context），跨平台层改由
 * [Platform.feedback] 提供：Android=AndroidDeviceFeedback（系统默认通知音 + 300ms 震动，行为不变），
 * iOS=IosDeviceFeedback（AudioServicesPlayAlertSound + 默认强度震动），未安装实现时静默无操作。
 */
private fun playCompletionAlert() {
    Platform.feedback.playAlarm()
    Platform.feedback.vibrate(300)
}

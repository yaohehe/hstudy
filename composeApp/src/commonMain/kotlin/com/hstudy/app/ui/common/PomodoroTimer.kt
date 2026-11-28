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
import com.hstudy.app.AppGraph
import com.hstudy.app.data.focus.PomodoroFocusSettlement
import com.hstudy.app.data.focus.PomodoroSessionPhase
import com.hstudy.app.data.focus.PomodoroSessionState
import com.hstudy.app.data.focus.PomodoroSettlementResult
import com.hstudy.app.data.preferences.PomodoroWriteExpectation
import com.hstudy.app.i18n.CanonicalFocusText
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.platform.AnalyticsEvent
import com.hstudy.app.platform.AnalyticsReporter
import com.hstudy.app.platform.KeepScreenOn
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.feedback
import com.hstudy.app.ui.dt.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PomodoroState {
    IDLE, BREATHING, RUNNING, PAUSED, BREAK, READY_NEXT
}

/**
 * 番茄钟组件 - 显示倒计时圆环和控制按钮
 *
 * v3.3.1 D-05/07（指令 §4.2）：计时状态改由**时间戳锚定的持久化会话**驱动——
 * 唯一权威状态源是 `AppGraph.userPreferences.pomodoroSession`（DataStore，
 * [PomodoroSessionState]），倒计时的显示值每次由 `deadline - now` 派生，组件内**不存"剩余秒数"**
 * （remainingSeconds/totalSeconds 只是每 tick 的展示缓存，进程内派生值，不是状态源）。
 * 按返回销毁页面、系统杀进程都不再丢计时：重进组合从持久化会话无缝续倒数（含休息段）；
 * 到期未结算的专注段由结算路径（与宿主页完成回调共用
 * [PomodoroFocusSettlement.settleFocusSession]）认领入账，双触发只结算一笔（要求 4）。
 *
 * @param focusMinutes 专注时长（分钟，IDLE 起锚时用；已在计时中以持久化会话为准）
 * @param breakMinutes 休息时长（分钟）
 * @param focusTarget 专注目标（规范值，写入会话并随结算进 taskTitle/description）
 * @param onComplete 专注段到期时回调结算产物；null = 认领被并发触发者赢走（本次不入账，仅完成埋点语义）
 */
@Composable
fun PomodoroTimer(
    focusMinutes: Int = 25,
    breakMinutes: Int = 5,
    skipBreathing: Boolean = false,
    focusTarget: String = CanonicalFocusText.TARGET_FREE_STUDY,
    onComplete: (PomodoroSettlementResult?) -> Unit = {},
    onActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val app = AppGraph
    val strings = LocalStrings.current.pomodoro
    val currentStrings by rememberUpdatedState(strings)
    val scope = rememberCoroutineScope()

    // ===== 权威状态源：持久化会话（跨导航/跨进程从这里恢复）=====
    val session by app.userPreferences.pomodoroSession.collectAsState(initial = null)

    // 会话之外的三段局部态：IDLE / BREATHING（呼吸引导是起锚前仪式，不持久化）/ READY_NEXT。
    // 会话非空时显示态完全由会话派生，本变量不出场。
    var idlePhase by remember { mutableStateOf(PomodoroState.IDLE) }

    // 显示态由会话派生（RUNNING/PAUSED/BREAK 不再可能是"只活在内存里"的状态）
    val state = when {
        session == null -> idlePhase
        session!!.phase == PomodoroSessionPhase.FOCUS ->
            if (session!!.isPaused) PomodoroState.PAUSED else PomodoroState.RUNNING
        else -> PomodoroState.BREAK
    }

    // 展示缓存（每 tick 由 deadline - now 派生；初值仅为 IDLE 首帧观感）
    var remainingSeconds by remember { mutableIntStateOf(focusMinutes * 60) }
    var totalSeconds by remember { mutableIntStateOf(focusMinutes * 60) }
    var completedPomodoros by remember { mutableIntStateOf(0) }

    // 通知父组件计时器是否活跃（用于禁用时长设置等）
    val isActive = state != PomodoroState.IDLE
    LaunchedEffect(isActive) { onActiveChange(isActive) }

    // D-06：计时中（专注/休息段）屏幕常亮；暂停、结束、离开页面（组合销毁）自动释放。
    KeepScreenOn(enabled = state == PomodoroState.RUNNING || state == PomodoroState.BREAK)

    // ===== 专注前呼吸引导（帮助孩子平静心神、快速进入专注状态）=====
    val totalBreathCycles = 3
    var breathCycle by remember { mutableIntStateOf(0) }
    var breathIn by remember { mutableStateOf(true) } // true=吸气 false=呼气
    val breathScale by animateFloatAsState(
        targetValue = if (breathIn) 1f else 0.55f,
        animationSpec = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
        label = "breathScale"
    )

    // 呼吸引导流程：吸气4秒→呼气4秒为一个循环，完成后自动**写入持久化专注会话**起锚。
    // key 带上 session：计时中（含"跳过呼吸"按钮刚写完会话）本效果重启后条件不成立，
    // 循环被取消，不会再覆盖已开始的会话起点。
    LaunchedEffect(idlePhase, session) {
        if (idlePhase == PomodoroState.BREATHING && session == null) {
            breathCycle = 0
            breathIn = true
            while (breathCycle < totalBreathCycles) {
                breathIn = true
                delay(4000)
                breathIn = false
                delay(4000)
                breathCycle++
            }
            // 呼吸完成，自动进入专注：起锚点=墙钟 now，倒计时之后全靠派生
            totalSeconds = focusMinutes * 60
            remainingSeconds = totalSeconds
            app.userPreferences.savePomodoroSession(
                PomodoroSessionState.startFocus(nowMillis(), focusMinutes, focusTarget)
            )
            AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.POMODORO_START))
        }
    }

    // 计时核心：会话（或其暂停/到期变化）一变就重挂循环；每个显示值都由
    // `deriveRemainingMs(now)` 从持久化锚点现场派生——息屏、后台、导航销毁都追得上真实时间。
    LaunchedEffect(session) {
        val s = session
        if (s == null) {
            // 非计时状态时取消通知
            Platform.feedback.cancelRunningNotification()
            return@LaunchedEffect
        }
        totalSeconds = s.plannedMinutes * 60
        remainingSeconds = (s.deriveRemainingMs(nowMillis()) / 1000L).toInt()
        if (s.isPaused) {
            // 暂停 = 冻结：deriveRemainingMs 在暂停期间对任意 now 恒定，无需挂循环计时
            Platform.feedback.cancelRunningNotification()
            return@LaunchedEffect
        }
        var notifCounter = 0
        while (true) {
            val remaining = (s.deriveRemainingMs(nowMillis()) / 1000L).toInt()
            remainingSeconds = if (remaining > 0) remaining else 0
            if (remainingSeconds <= 0) break
            // 每15秒更新一次常驻进度通知（让孩子息屏后也能看进度）
            notifCounter++
            if (notifCounter % 60 == 1) { // 250ms * 60 = 15s
                val min = remainingSeconds / 60
                val sec = remainingSeconds % 60
                Platform.feedback.showRunningNotification(
                    title = if (s.phase == PomodoroSessionPhase.BREAK) currentStrings.phaseBreak else currentStrings.phaseFocus,
                    text = currentStrings.remainTime(min, sec),
                    progressPercent = if (totalSeconds > 0) (totalSeconds - remainingSeconds) * 100 / totalSeconds else 0
                )
            }
            delay(250) // 高频刷新，息屏恢复后能立即追上正确时间
        }
        // 时间到
        Platform.feedback.cancelRunningNotification()
        if (s.phase == PomodoroSessionPhase.FOCUS) {
            completedPomodoros++
            // 结算走与启动补结算**同一条**共用路径：先 CAS 认领（今日番茄数/宠物心情随认领
            // 一次性入账），再 recordFocusSession → FOCUS bizKey checkIn，最后把会话原地转成
            // 休息段（nextSession，避免"会话为空"的闪烁帧）。埋点 POMODORO_COMPLETE 仍只在
            // 宿主页完成回调里打点（要求 6：数量与位置语义不变）。
            val result = PomodoroFocusSettlement.settleFocusSession(
                userPreferences = app.userPreferences,
                focusRepository = app.focusRepository,
                pointRepository = app.pointRepository,
                session = s,
                nowMs = nowMillis(),
                nextSession = s.focusCompletedToBreak(nowMillis(), breakMinutes),
            )
            onComplete(result)
            // 完成提示音+震动（让孩子知道专注结束了）
            playCompletionAlert()
        } else {
            // 休息结束→过渡引导状态（而非直接回IDLE，给孩子一个明确的下一步）
            playCompletionAlert()
            app.userPreferences.savePomodoroSession(
                null,
                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
            )
            idlePhase = PomodoroState.READY_NEXT
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
                        // displayMedium=28sp Bold：与原裸字号同值，改走排版槽（§3.2 排版节奏）
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(strings.deepBreathCount((breathCycle + 1).coerceAtMost(totalBreathCycles), totalBreathCycles),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
            // 背景圆环（轨道用语义容器层，指令 3.4-专注1）
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
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

            // 中间文字：计时表盘居中 + displayLarge 排版槽（指令 3.4-专注1，去裸字号）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                Text(
                    text = com.hstudy.app.ui.dt.strFormat("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.displayLarge,
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
                            // 跳过呼吸，直接起锚开始专注（写入持久化会话）
                            totalSeconds = focusMinutes * 60
                            remainingSeconds = totalSeconds
                            scope.launch {
                                app.userPreferences.savePomodoroSession(
                                    PomodoroSessionState.startFocus(nowMillis(), focusMinutes, focusTarget)
                                )
                            }
                            AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.POMODORO_START))
                        } else {
                            // 先进入专注前呼吸引导，帮助平静心神
                            idlePhase = PomodoroState.BREATHING
                        }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.startFocusButton)
                    }
                }
                PomodoroState.BREATHING -> {
                    TextButton(onClick = {
                        // 跳过呼吸引导，直接起锚开始专注（会话落盘后本效果自动让位）
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                        scope.launch {
                            app.userPreferences.savePomodoroSession(
                                PomodoroSessionState.startFocus(nowMillis(), focusMinutes, focusTarget)
                            )
                        }
                        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.POMODORO_START))
                    }) {
                        Text(strings.skipBreathingButton)
                    }
                }
                PomodoroState.RUNNING -> {
                    OutlinedButton(onClick = {
                        // 暂停 = 在会话上记暂停锚点（deadline 随 now 顺延 → 剩余冻结）
                        val s = session
                        if (s != null) scope.launch {
                            app.userPreferences.savePomodoroSession(
                                s.paused(nowMillis()),
                                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.pauseButton)
                    }
                    TextButton(onClick = {
                        // 放弃本轮：清会话、不结算（与原"停止即丢"口径一致，孩子主动放弃不补分）
                        idlePhase = PomodoroState.IDLE
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                        val s = session
                        if (s != null) scope.launch {
                            app.userPreferences.savePomodoroSession(
                                null,
                                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.stopButton)
                    }
                }
                PomodoroState.PAUSED -> {
                    FilledTonalButton(onClick = {
                        // 从暂停恢复：累计暂停毫秒固化，deadline 顺延（剩余秒数不落地，纯派生）
                        val s = session
                        if (s != null) scope.launch {
                            app.userPreferences.savePomodoroSession(
                                s.resumed(nowMillis()),
                                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
                            )
                        }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.resumeButton)
                    }
                    TextButton(onClick = {
                        idlePhase = PomodoroState.IDLE
                        totalSeconds = focusMinutes * 60
                        remainingSeconds = totalSeconds
                        val s = session
                        if (s != null) scope.launch {
                            app.userPreferences.savePomodoroSession(
                                null,
                                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.stopButton)
                    }
                }
                PomodoroState.BREAK -> {
                    TextButton(onClick = {
                        // 跳过休息：清会话进入过渡态（休息不产生结算）
                        idlePhase = PomodoroState.READY_NEXT
                        val s = session
                        if (s != null) scope.launch {
                            app.userPreferences.savePomodoroSession(
                                null,
                                PomodoroWriteExpectation(s.sessionStartEpochMs, s.settled, s.version),
                            )
                        }
                    }) {
                        Text(strings.skipBreakButton)
                    }
                }
                PomodoroState.READY_NEXT -> {
                    // 休息结束过渡引导：明确告诉孩子下一步
                    FilledTonalButton(onClick = {
                        idlePhase = PomodoroState.BREATHING
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.startNextRoundButton)
                    }
                    TextButton(onClick = {
                        idlePhase = PomodoroState.IDLE
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

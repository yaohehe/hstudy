package com.hstudy.app.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.CanonicalFocusText
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun ChildPomodoroScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass,
    onNavigateToTraining: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {}
) {
    val app = AppGraph
    val strings = LocalStrings.current.pomodoro
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var focusMinutes by remember { mutableIntStateOf(25) }
    var breakMinutes by remember { mutableIntStateOf(5) }
    var isTimerActive by remember { mutableStateOf(false) }

    // 今日专注总览（让孩子知道今天已经专注了多久）
    val todayStr = com.hstudy.app.data.repository.FocusRepository.getTodayString()
    val todayFocusMin by app.focusRepository.getTotalFocusMinutesForDate(todayStr).collectAsState(initial = 0)
    // 今日番茄钟完成数（持久化，离开页面不丢失）
    val todayPomodoroCount by app.userPreferences.pomodoroTodayCount.collectAsState(initial = 0)
    // 跳过呼吸引导偏好（记住用户选择）
    val skipBreathing by app.userPreferences.pomodoroSkipBreathing.collectAsState(initial = false)

    // 专注目标：让专注更有目的性（靶向专注）
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val presetTargets = CanonicalFocusText.presetTargets
    val allTargets = presetTargets + tasks.map { it.title }
    var focusTarget by remember { mutableStateOf(CanonicalFocusText.TARGET_FREE_STUDY) }
    var targetExpanded by remember { mutableStateOf(false) }

    // 黄金专注时段：根据历史专注数据找出效率最高的时段，引导把难题安排在最佳时间
    val allSessions by app.focusRepository.getAllSessions().collectAsState(initial = emptyList())
    val goldenHour = remember(allSessions) {
        if (allSessions.isEmpty()) null else {
            allSessions.groupBy { s ->
                com.hstudy.app.ui.dt.Calendar.getInstance().apply { timeInMillis = s.completedAt }
                    .get(com.hstudy.app.ui.dt.Calendar.HOUR_OF_DAY)
            }.mapValues { e -> e.value.sumOf { s -> s.durationMinutes } }
                .maxByOrNull { it.value }?.key
        }
    }
    val currentHour = remember { com.hstudy.app.ui.dt.Calendar.getInstance().get(com.hstudy.app.ui.dt.Calendar.HOUR_OF_DAY) }

    Scaffold(
        topBar = {
            HStudyTopBar(title = strings.pageTitle, onNavigateBack = onNavigateBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
            ) {
                // 专注目标选择（靶向专注：先明确这次专注要攻克什么）
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(strings.targetHeading, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = targetExpanded,
                        onExpandedChange = { targetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = strings.targetLabel(localizedPresetText(focusTarget, displayLanguage)),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = targetExpanded,
                            onDismissRequest = { targetExpanded = false }
                        ) {
                            allTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(strings.targetLabel(localizedPresetText(target, displayLanguage))) },
                                    onClick = { focusTarget = target; targetExpanded = false }
                                )
                            }
                        }
                    }
                }

                // 黄金专注时段提示（引导孩子把难题安排在高效时段）
                if (goldenHour != null) {
                    val goldenLabel = goldenHourLabel(goldenHour)
                    val isGoldenNow = currentHour == goldenHour
                    Card(
                        modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGoldenNow) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            if (isGoldenNow) strings.goldenHourNowNote(goldenLabel)
                            else strings.goldenHourGeneralNote(goldenLabel),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isGoldenNow) FontWeight.Bold else FontWeight.Normal,
                            color = if (isGoldenNow) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 今日专注总览（看见今天的专注投入）
                if (todayFocusMin > 0 || todayPomodoroCount > 0) {
                    Card(
                        modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🍅", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    strings.todayFocusSummary(todayFocusMin, if (todayFocusMin >= 60) strings.overHoursNote(todayFocusMin / 60) else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (todayPomodoroCount > 0) {
                                    Text(
                                        strings.completedPomodorosLine(todayPomodoroCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // 番茄钟主体
                PomodoroTimer(
                    focusMinutes = focusMinutes,
                    breakMinutes = breakMinutes,
                    skipBreathing = skipBreathing,
                    onActiveChange = { isTimerActive = it },
                    onComplete = { minutes ->
                        scope.launch {
                            // 记录专注时长（每5分钟=1分，积分待家长审核）
                            val points = minutes / 5
                            app.focusRepository.recordFocusSession(minutes, focusTarget, 0)
                            if (points > 0) {
                                app.pointApprovalRepository.submitApproval("FOCUS_COMPLETE", CanonicalFocusText.recordDescription(focusTarget, minutes), points)
                            }
                            app.userPreferences.boostPetMood(3)
                            app.userPreferences.incrementPomodoroTodayCount()
                            snackbarHostState.showSnackbar(strings.focusDoneSnackbar(strings.targetLabel(localizedPresetText(focusTarget, displayLanguage)), points))
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 40.dp))

                // 时长设置（计时中禁用，避免误触造成困惑）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(strings.focusLengthLabel, style = MaterialTheme.typography.labelLarge,
                        color = if (isTimerActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    listOf(15, 25, 30, 45).forEach { min ->
                        FilterChip(
                            selected = focusMinutes == min,
                            onClick = { if (!isTimerActive) focusMinutes = min },
                            enabled = !isTimerActive,
                            label = { Text(strings.durationChip(min, common.minuteLabel)) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(strings.breakLengthLabel, style = MaterialTheme.typography.labelLarge,
                        color = if (isTimerActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    listOf(3, 5, 10).forEach { min ->
                        FilterChip(
                            selected = breakMinutes == min,
                            onClick = { if (!isTimerActive) breakMinutes = min },
                            enabled = !isTimerActive,
                            label = { Text(strings.durationChip(min, common.minuteLabel)) }
                        )
                    }
                }
                if (isTimerActive) {
                    Text(strings.lengthLockedNote, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 专注偏好设置
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.breathingSwitchTitle, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (skipBreathing) strings.breathingStateSkipped else strings.breathingStateOn, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(
                        checked = !skipBreathing,
                        onCheckedChange = { enabled ->
                            scope.launch { app.userPreferences.setPomodoroSkipBreathing(!enabled) }
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }

                // 积分说明
                Card(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.pointsRulesTitle, style = MaterialTheme.typography.titleSmall)
                        Text(strings.pointsPerFocusRule(5, 1), style = MaterialTheme.typography.bodySmall)
                        Text(strings.pointsLadderNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(strings.lockScreenNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 专注力训练入口（专注前热身）
                OutlinedButton(onClick = onNavigateToTraining) {
                    Text(strings.schulteEntryButton)
                }
                OutlinedButton(onClick = onNavigateToMemory) {
                    Text(strings.memoryEntryButton)
                }
            }
        }
    }
}

/** 将小时（0-23）转为友好的时段描述 */
@Composable
private fun goldenHourLabel(hour: Int): String {
    val strings = LocalStrings.current.pomodoro
    val period = when (hour) {
        in 5..8 -> strings.goldenPeriodEarlyMorning
        in 9..11 -> strings.goldenPeriodMorning
        in 12..13 -> strings.goldenPeriodMidday
        in 14..17 -> strings.goldenPeriodAfternoon
        in 18..22 -> strings.goldenPeriodEvening
        else -> strings.goldenPeriodLateNight
    }
    return strings.goldenPeriodHour(period, hour)
}

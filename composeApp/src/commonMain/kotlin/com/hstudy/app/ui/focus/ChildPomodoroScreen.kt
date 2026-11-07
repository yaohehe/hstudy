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
    val presetTargets = listOf("自由学习", "复习错题", "预习新课", "完成作业")
    val allTargets = presetTargets + tasks.map { it.title }
    var focusTarget by remember { mutableStateOf("自由学习") }
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
            HStudyTopBar(title = "🍅 番茄钟", onNavigateBack = onNavigateBack)
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
                    Text("🎯 本次专注目标", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = targetExpanded,
                        onExpandedChange = { targetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = focusTarget,
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
                                    text = { Text(target) },
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
                            if (isGoldenNow) "🌟 现在正是你的黄金专注时段（$goldenLabel），效率最高，抓紧专注！"
                            else "💡 你的黄金专注时段是 $goldenLabel，那时专注效率最高哦～",
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
                                    "今日已专注 $todayFocusMin 分钟${if (todayFocusMin >= 60) "（超${todayFocusMin / 60}小时）" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (todayPomodoroCount > 0) {
                                    Text(
                                        "完成 $todayPomodoroCount 个番茄钟 🍅×$todayPomodoroCount",
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
                                app.pointApprovalRepository.submitApproval("FOCUS_COMPLETE", "专注「$focusTarget」${minutes}分钟", points)
                            }
                            app.userPreferences.boostPetMood(3)
                            app.userPreferences.incrementPomodoroTodayCount()
                            snackbarHostState.showSnackbar("🍅 「$focusTarget」专注完成！+${points}分已提交家长审核")
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 40.dp))

                // 时长设置（计时中禁用，避免误触造成困惑）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("专注:", style = MaterialTheme.typography.labelLarge,
                        color = if (isTimerActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    listOf(15, 25, 30, 45).forEach { min ->
                        FilterChip(
                            selected = focusMinutes == min,
                            onClick = { if (!isTimerActive) focusMinutes = min },
                            enabled = !isTimerActive,
                            label = { Text("${min}分") }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("休息:", style = MaterialTheme.typography.labelLarge,
                        color = if (isTimerActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    listOf(3, 5, 10).forEach { min ->
                        FilterChip(
                            selected = breakMinutes == min,
                            onClick = { if (!isTimerActive) breakMinutes = min },
                            enabled = !isTimerActive,
                            label = { Text("${min}分") }
                        )
                    }
                }
                if (isTimerActive) {
                    Text("⏱️ 计时中，时长设置已锁定", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // 专注偏好设置
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧘 专注前呼吸引导", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (skipBreathing) "已跳过" else "开启", style = MaterialTheme.typography.labelSmall,
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
                        Text("💡 积分规则", style = MaterialTheme.typography.titleSmall)
                        Text("每专注 5 分钟 = +1 分（所有时长均有效）", style = MaterialTheme.typography.bodySmall)
                        Text("15分钟+3分 · 25分钟+5分 · 30分钟+6分 · 45分钟+9分", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("息屏后仍会继续计时，放心锁屏专注！", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 专注力训练入口（专注前热身）
                OutlinedButton(onClick = onNavigateToTraining) {
                    Text("🎯 专注力训练·舒尔特方格（专注前热身）")
                }
                OutlinedButton(onClick = onNavigateToMemory) {
                    Text("🧠 记忆广度训练·数字记忆（练工作记忆）")
                }
            }
        }
    }
}

/** 将小时（0-23）转为友好的时段描述 */
private fun goldenHourLabel(hour: Int): String {
    val period = when (hour) {
        in 5..8 -> "早上"
        in 9..11 -> "上午"
        in 12..13 -> "中午"
        in 14..17 -> "下午"
        in 18..22 -> "晚上"
        else -> "深夜"
    }
    return "$period${hour}点"
}

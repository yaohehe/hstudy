package com.hstudy.app.ui.habit

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.HabitChallengeEntity
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.platform.AnalyticsEvent
import com.hstudy.app.platform.AnalyticsReporter
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildHabitChallengeScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.childHabitChallenge
    // P1-01 撤销文案复用 ChildTaskListScreen 的取词路径（RA4：撤销按钮/如实反馈 Snackbar 同一词面），
    // 不再新建同值字段；撤销路径不打埋点（既有裁决，两处成功分支 CHECKIN 原样保留）。
    val taskList = LocalStrings.current.taskList
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val activeChallenges by app.habitRepository.getActiveChallenges().collectAsState(initial = emptyList())
    val allChallenges by app.habitRepository.getAllChallenges().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = strings.newChallengeContentDescription)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (allChallenges.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeChallenges.isNotEmpty()) {
                    item { Text(strings.activeSectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(activeChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(
                            challenge = challenge,
                            isCheckedInToday = app.habitRepository.isCheckedInToday(challenge),
                            onUndo = {
                                undoTodayCheckIn(challenge, taskList, app, snackbarHostState, scope)
                            },
                            onCheckIn = {
                                scope.launch {
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    when (app.habitRepository.checkInChallenge(challenge)) {
                                        com.hstudy.app.data.repository.HabitRepository.CheckInResult.ALREADY_CHECKED_IN -> {
                                            snackbarHostState.showSnackbar(strings.alreadyCheckedInMessage)
                                        }
                                        com.hstudy.app.data.repository.HabitRepository.CheckInResult.CHALLENGE_COMPLETED -> {
                                            // 习惯打卡同样走审批：此刻只登记申请，审批通过后才计分
                                            if (challenge.dailyPoints > 0) {
                                                app.pointRepository.checkIn(
                                                    bizKey = "HABIT:${challenge.id}:$today",
                                                    actionType = "HABIT_CHECKIN",
                                                    description = "习惯打卡: ${challenge.title}",
                                                    points = challenge.dailyPoints,
                                                    date = today
                                                )
                                                AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
                                            }
                                            snackbarHostState.showSnackbar(strings.challengeCompleteMessage(challenge.title, challenge.dailyPoints))
                                            app.habitRepository.tryUnlockAchievement(
                                                "habit_complete", "习惯达人", "完成一个21天挑战", "🏆"
                                            )
                                        }
                                        com.hstudy.app.data.repository.HabitRepository.CheckInResult.SUCCESS -> {
                                            if (challenge.dailyPoints > 0) {
                                                app.pointRepository.checkIn(
                                                    bizKey = "HABIT:${challenge.id}:$today",
                                                    actionType = "HABIT_CHECKIN",
                                                    description = "习惯打卡: ${challenge.title}",
                                                    points = challenge.dailyPoints,
                                                    date = today
                                                )
                                                AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
                                            }
                                            snackbarHostState.showSnackbar(strings.checkInSuccessMessage(challenge.dailyPoints, challenge.completedDays + 1, challenge.totalDays))
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch { app.habitRepository.deleteChallenge(challenge.id) }
                            }
                        )
                    }
                }

                val completed = allChallenges.filter { it.isCompleted }
                if (completed.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { Text(strings.completedSectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(completed, key = { it.id }) { challenge ->
                        ChallengeCard(
                            challenge = challenge,
                            isCheckedInToday = app.habitRepository.isCheckedInToday(challenge),
                            onCheckIn = {},
                            onUndo = {
                                undoTodayCheckIn(challenge, taskList, app, snackbarHostState, scope)
                            },
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddChallengeDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, desc, days, emoji ->
                scope.launch {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    app.habitRepository.addChallenge(
                        HabitChallengeEntity(
                            title = title, description = desc,
                            startDate = today, totalDays = days, emoji = emoji
                        )
                    )
                    showAddDialog = false
                    snackbarHostState.showSnackbar(strings.challengeCreated)
                }
            }
        )
    }
}

@Composable
private fun ChallengeCard(
    challenge: HabitChallengeEntity,
    isCheckedInToday: Boolean,
    onCheckIn: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current.childHabitChallenge
    val progress = if (challenge.totalDays > 0) challenge.completedDays.toFloat() / challenge.totalDays else 0f
    val isCompleted = challenge.isCompleted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 进度环
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    val progressColor = if (isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 6.dp.toPx()
                        val radius = (size.minDimension - strokeW) / 2
                        val topLeft = Offset((size.width - radius * 2) / 2, (size.height - radius * 2) / 2)
                        drawArc(color = Color.Gray.copy(alpha = 0.2f), startAngle = -90f, sweepAngle = 360f,
                            useCenter = false, topLeft = topLeft, size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeW))
                        drawArc(color = progressColor, startAngle = -90f, sweepAngle = 360f * progress,
                            useCenter = false, topLeft = topLeft, size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round))
                    }
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(challenge.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(strings.daysProgress(challenge.completedDays, challenge.totalDays),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!isCompleted && challenge.isActive) {
                    if (isCheckedInToday) {
                        // P1-01 撤销闭环（指令【P1-01】目标文件 2）：今日已打卡不再是死状态，
                        // 提供撤销入口（habit 回退 + pointRepository.undoCheckIn(HABIT bizKey)）。
                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.checkedInToday, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onUndo) {
                                Text(LocalStrings.current.taskList.undoButton,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        FilledTonalButton(onClick = onCheckIn) {
                            Text(strings.checkInButton)
                        }
                    }
                } else if (isCompleted) {
                    // 完成日那次打卡若就发生在今天，同样允许撤销（undoCheckInChallenge 会重新激活挑战）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", style = MaterialTheme.typography.headlineSmall)
                        if (isCheckedInToday) {
                            TextButton(onClick = onUndo) {
                                Text(LocalStrings.current.taskList.undoButton,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (challenge.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(challenge.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * P1-01 撤销今日打卡（指令【P1-01】目标文件 2 的闭环入口）：
 * habit 侧回退（[HabitRepository.undoCheckInChallenge]）+ 积分侧
 * [com.hstudy.app.data.repository.PointRepository.undoCheckIn]（bizKey=`HABIT:id:date`），
 * Snackbar 如实反馈扣回/取消，镜像 ChildTaskListScreen 的 undoAndReport 流（RA4 复用 [TaskListStrings] 取词路径）。
 * 撤销路径不打埋点（既有裁决），checkIn 成功分支的两条 CHECKIN 埋点原样保留。
 */
private fun undoTodayCheckIn(
    challenge: HabitChallengeEntity,
    taskList: com.hstudy.app.i18n.TaskListStrings,
    app: AppGraph,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // 回审 P1-1：bizKey 用打卡时落库的 lastCheckInDate（跨午夜撤销对称），缺失回退今天（M-1 同策略）
        val checkInDate = challenge.lastCheckInDate.ifEmpty { today }
        app.habitRepository.undoCheckInChallenge(challenge)
        val clawedBack = app.pointRepository.undoCheckIn(
            bizKey = "HABIT:${challenge.id}:$checkInDate",
            date = checkInDate
        )
        snackbarHostState.showSnackbar(
            if (clawedBack > 0) taskList.revertedClawedBackMessage(challenge.title, clawedBack)
            else taskList.revertedPendingApprovalMessage(challenge.title)
        )
    }
}

@Composable
private fun AddChallengeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String) -> Unit
) {
    val strings = LocalStrings.current.childHabitChallenge
    val common = LocalStrings.current.common
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(21) }
    var emoji by remember { mutableStateOf("🎯") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newChallengeDialogTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text(strings.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text(strings.descLabel) }, modifier = Modifier.fillMaxWidth())
                Text(strings.daysLabel, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 21, 30).forEach { d ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(strings.daysOption(d)) })
                    }
                }
                Text(strings.iconLabel, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🎯", "📚", "🏃", "✍️", "🎵", "💪").forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onAdd(title, desc, days, emoji) }, enabled = title.isNotBlank()) { Text(strings.createButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(common.cancelButton) } }
    )
}

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
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildHabitChallengeScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val activeChallenges by app.habitRepository.getActiveChallenges().collectAsState(initial = emptyList())
    val allChallenges by app.habitRepository.getAllChallenges().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = "🏆 习惯挑战", onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建挑战")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (allChallenges.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("开始你的21天习惯挑战吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeChallenges.isNotEmpty()) {
                    item { Text("🔥 进行中的挑战", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(activeChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(
                            challenge = challenge,
                            isCheckedInToday = app.habitRepository.isCheckedInToday(challenge),
                            onCheckIn = {
                                scope.launch {
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    when (app.habitRepository.checkInChallenge(challenge)) {
                                        com.hstudy.app.data.repository.HabitRepository.CheckInResult.ALREADY_CHECKED_IN -> {
                                            snackbarHostState.showSnackbar("😊 今天已经打过卡啦，明天再来！")
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
                                            }
                                            snackbarHostState.showSnackbar("🎉 恭喜完成挑战: ${challenge.title}! +${challenge.dailyPoints}分待家长审核")
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
                                            }
                                            snackbarHostState.showSnackbar("✅ 打卡成功 +${challenge.dailyPoints}分（待家长审核）(${challenge.completedDays + 1}/${challenge.totalDays})")
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
                    item { Text("✅ 已完成的挑战", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(completed, key = { it.id }) { challenge ->
                        ChallengeCard(challenge = challenge, isCheckedInToday = false, onCheckIn = {}, onDelete = {})
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
                    snackbarHostState.showSnackbar("🎯 挑战已创建！")
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
    onDelete: () -> Unit
) {
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
                    Text("${challenge.completedDays} / ${challenge.totalDays} 天",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!isCompleted && challenge.isActive) {
                    if (isCheckedInToday) {
                        Text("✅ 今日已打卡", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    } else {
                        FilledTonalButton(onClick = onCheckIn) {
                            Text("打卡")
                        }
                    }
                } else if (isCompleted) {
                    Text("🏆", style = MaterialTheme.typography.headlineSmall)
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

@Composable
private fun AddChallengeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(21) }
    var emoji by remember { mutableStateOf("🎯") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 新建习惯挑战") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("挑战名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("描述（可选）") }, modifier = Modifier.fillMaxWidth())
                Text("天数: ", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 14, 21, 30).forEach { d ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text("${d}天") })
                    }
                }
                Text("图标:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🎯", "📚", "🏃", "✍️", "🎵", "💪").forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onAdd(title, desc, days, emoji) }, enabled = title.isNotBlank()) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

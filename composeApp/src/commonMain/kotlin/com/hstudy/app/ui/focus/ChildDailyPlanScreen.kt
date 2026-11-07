package com.hstudy.app.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyPlanEntity
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildDailyPlanScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val plans by app.focusRepository.getPlansByDate(today).collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val completedCount = plans.count { it.isCompleted }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = "📋 今日计划",
                onNavigateBack = onNavigateBack,
                actions = {
                    Text("${completedCount}/${plans.size}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp))
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加计划")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (plans.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📅", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("还没有今日计划，点击下方 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plans, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        onToggle = {
                            scope.launch {
                                app.focusRepository.setPlanCompleted(plan.id, !plan.isCompleted)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                app.focusRepository.deletePlan(plan.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // 添加计划对话框
    if (showAddDialog) {
        AddPlanDialog(
            today = today,
            onDismiss = { showAddDialog = false },
            onAdd = { title, startH, startM, endH, endM ->
                scope.launch {
                    app.focusRepository.addPlan(
                        DailyPlanEntity(
                            date = today,
                            startHour = startH, startMinute = startM,
                            endHour = endH, endMinute = endM,
                            taskTitle = title
                        )
                    )
                    showAddDialog = false
                    snackbarHostState.showSnackbar("✅ 计划已添加")
                }
            }
        )
    }
}

@Composable
private fun PlanCard(
    plan: DailyPlanEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = if (plan.isCompleted)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                if (plan.isCompleted) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "已完成",
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                } else {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "未完成", modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plan.taskTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    com.hstudy.app.ui.dt.strFormat("%02d:%02d - %02d:%02d", plan.startHour, plan.startMinute, plan.endHour, plan.endMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddPlanDialog(
    today: String,
    onDismiss: () -> Unit,
    onAdd: (String, Int, Int, Int, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(8) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(9) }
    var endMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📋 添加学习计划") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("学习内容") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("时间段", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimePickerField("开始", startHour, startMinute) { h, m -> startHour = h; startMinute = m }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("~")
                    Spacer(modifier = Modifier.width(8.dp))
                    TimePickerField("结束", endHour, endMinute) { h, m -> endHour = h; endMinute = m }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onAdd(title, startHour, startMinute, endHour, endMinute) },
                enabled = title.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TimePickerField(label: String, hour: Int, minute: Int, onTimeChange: (Int, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label ", style = MaterialTheme.typography.labelSmall)
        com.hstudy.app.ui.common.HourMinuteStepper(
            hour = hour,
            minute = minute,
            onTimeChange = { h, m -> onTimeChange(h, m) }
        )
    }
}

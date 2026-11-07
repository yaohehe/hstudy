package com.hstudy.app.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

/**
 * 家长端 - 每日日程管理：添加/编辑/删除日程项，开关控制提醒
 */
@Composable
fun ParentScheduleManageScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val schedules by app.scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())

    var editingSchedule by remember { mutableStateOf<DailyScheduleEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = "⏰ 每日日程", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加日程")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text("💡 为孩子设置每日时间日程（如 7:00 起床、16:00 写作业），到点自动通知提醒。可关联具体任务，引导孩子打卡。",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }

                    if (schedules.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("⏰", fontSize = 48.sp)
                                Text("还没有日程安排", style = MaterialTheme.typography.titleMedium)
                                Text("点击下方按钮一键载入示例日程（起床/早餐/写作业/睡觉等），再按需调整时间即可。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = {
                                    scope.launch {
                                        val samples = listOf(
                                            com.hstudy.app.data.local.entity.DailyScheduleEntity(time = "07:00", title = "起床啦", emoji = "🌅"),
                                            com.hstudy.app.data.local.entity.DailyScheduleEntity(time = "07:30", title = "吃早餐", emoji = "🍳"),
                                            com.hstudy.app.data.local.entity.DailyScheduleEntity(time = "16:00", title = "开始写作业", emoji = "✏️"),
                                            com.hstudy.app.data.local.entity.DailyScheduleEntity(time = "19:00", title = "阅读时间", emoji = "📖"),
                                            com.hstudy.app.data.local.entity.DailyScheduleEntity(time = "21:00", title = "准备睡觉", emoji = "😴")
                                        )
                                        samples.forEach { s ->
                                            val id = app.scheduleRepository.addSchedule(s)
                                            ScheduleAlarmHelper.schedule(s.copy(id = id))
                                        }
                                        snackbarHostState.showSnackbar("✅ 已载入示例日程")
                                    }
                                }) { Text("✨ 一键载入示例日程") }
                                Text("或点击右下角 + 自行添加", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    schedules.forEach { schedule ->
                        val linkedTask = tasks.find { it.id == schedule.linkedTaskId }
                        ScheduleManageCard(
                            schedule = schedule,
                            linkedTaskTitle = linkedTask?.title,
                            onToggle = { enabled ->
                                scope.launch {
                                    app.scheduleRepository.setEnabled(schedule.id, enabled)
                                    val updated = schedule.copy(isEnabled = enabled)
                                    if (enabled) ScheduleAlarmHelper.schedule(updated)
                                    else ScheduleAlarmHelper.cancel(updated)
                                }
                            },
                            onEdit = { editingSchedule = schedule },
                            onDelete = {
                                scope.launch {
                                    ScheduleAlarmHelper.cancel(schedule)
                                    app.scheduleRepository.deleteSchedule(schedule.id)
                                    snackbarHostState.showSnackbar("已删除「${schedule.title}」")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 新建对话框
    if (showAddDialog) {
        ScheduleEditDialog(
            schedule = null,
            tasks = tasks,
            onDismiss = { showAddDialog = false },
            onSaved = { newSchedule ->
                showAddDialog = false
                scope.launch {
                    val id = app.scheduleRepository.addSchedule(newSchedule)
                    if (newSchedule.isEnabled) {
                        ScheduleAlarmHelper.schedule(newSchedule.copy(id = id))
                    }
                    snackbarHostState.showSnackbar("✅ 已添加日程")
                }
            }
        )
    }

    // 编辑对话框
    editingSchedule?.let { schedule ->
        ScheduleEditDialog(
            schedule = schedule,
            tasks = tasks,
            onDismiss = { editingSchedule = null },
            onSaved = { updated ->
                editingSchedule = null
                scope.launch {
                    app.scheduleRepository.updateSchedule(updated)
                    ScheduleAlarmHelper.cancel(schedule)
                    if (updated.isEnabled) ScheduleAlarmHelper.schedule(updated)
                    snackbarHostState.showSnackbar("✅ 已更新日程")
                }
            }
        )
    }
}

@Composable
private fun ScheduleManageCard(
    schedule: DailyScheduleEntity,
    linkedTaskTitle: String?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isEnabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(schedule.emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(schedule.time, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(schedule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                if (linkedTaskTitle != null) {
                    Text("🔗 关联任务：$linkedTaskTitle", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                Text(repeatDaysText(schedule.repeatDays), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = schedule.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Text("✏️") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScheduleEditDialog(
    schedule: DailyScheduleEntity?,
    tasks: List<com.hstudy.app.data.local.entity.TaskEntity>,
    onDismiss: () -> Unit,
    onSaved: (DailyScheduleEntity) -> Unit
) {
    var hour by remember { mutableIntStateOf(schedule?.time?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7) }
    var minute by remember { mutableIntStateOf(schedule?.time?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0) }
    var title by remember { mutableStateOf(schedule?.title ?: "") }
    var emoji by remember { mutableStateOf(schedule?.emoji ?: "⏰") }
    var linkedTaskId by remember { mutableStateOf(schedule?.linkedTaskId) }
    var enabled by remember { mutableStateOf(schedule?.isEnabled ?: true) }

    val emojiOptions = listOf("⏰", "🌅", "🍳", "📖", "✏️", "🍚", "😴", "🏃", "🎹", "🧹", "🛁", "🥛")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (schedule == null) "添加日程" else "编辑日程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 时间选择（步进器，避免文本输入光标问题）
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("时间", style = MaterialTheme.typography.labelMedium)
                    com.hstudy.app.ui.common.HourMinuteStepper(
                        hour = hour,
                        minute = minute,
                        onTimeChange = { h, m -> hour = h; minute = m }
                    )
                }
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("日程标题（如：起床、写作业）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                // 图标
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    emojiOptions.forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e },
                            label = { Text(e, style = MaterialTheme.typography.titleMedium) })
                    }
                }

                // 关联任务
                Text("关联任务（可选）", style = MaterialTheme.typography.labelMedium)
                var taskExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = taskExpanded, onExpandedChange = { taskExpanded = it }) {
                    OutlinedTextField(
                        value = tasks.find { it.id == linkedTaskId }?.title ?: "无",
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = taskExpanded, onDismissRequest = { taskExpanded = false }) {
                        DropdownMenuItem(text = { Text("无") }, onClick = { linkedTaskId = null; taskExpanded = false })
                        tasks.forEach { t ->
                            DropdownMenuItem(text = { Text(t.title) }, onClick = { linkedTaskId = t.id; taskExpanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用提醒", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) return@Button
                val time = com.hstudy.app.ui.dt.strFormat("%02d:%02d", hour, minute)
                onSaved(
                    DailyScheduleEntity(
                        id = schedule?.id ?: 0,
                        time = time,
                        title = title,
                        emoji = emoji,
                        linkedTaskId = linkedTaskId,
                        isEnabled = enabled,
                        repeatDays = schedule?.repeatDays ?: "1,2,3,4,5,6,7",
                        sortOrder = schedule?.sortOrder ?: 0
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun repeatDaysText(repeatDays: String): String {
    val days = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (days.size == 7) return "每天"
    val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
    return "周" + days.mapNotNull { names[it] }.joinToString("、")
}

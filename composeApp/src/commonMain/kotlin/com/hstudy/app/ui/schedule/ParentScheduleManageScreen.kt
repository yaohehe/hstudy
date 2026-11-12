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
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
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
    val strings = LocalStrings.current.parentSchedule
    val displayLanguage = LocalDisplayLanguage.current
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val schedules by app.scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())

    var editingSchedule by remember { mutableStateOf<DailyScheduleEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = strings.addSchedule)
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
                        Text(strings.hint,
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
                                Text(strings.emptyTitle, style = MaterialTheme.typography.titleMedium)
                                Text(strings.emptyHint,
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
                                        snackbarHostState.showSnackbar(strings.samplesLoaded)
                                    }
                                }) { Text(strings.loadSamplesButton) }
                                Text(strings.addOwnHint, style = MaterialTheme.typography.labelSmall,
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
                                    snackbarHostState.showSnackbar(strings.removedMessage(localizedPresetText(schedule.title, displayLanguage)))
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
                    snackbarHostState.showSnackbar(strings.addedMessage)
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
                    snackbarHostState.showSnackbar(strings.updatedMessage)
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
    val strings = LocalStrings.current.parentSchedule
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current

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
                    Text(localizedPresetText(schedule.title, displayLanguage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
                if (linkedTaskTitle != null) {
                    Text(strings.linkedTaskLabel(localizedPresetText(linkedTaskTitle, displayLanguage)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
                Text(strings.repeatDays(schedule.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = schedule.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) { Text("✏️") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription, tint = MaterialTheme.colorScheme.error)
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
    val strings = LocalStrings.current.parentSchedule
    val common = LocalStrings.current.common
    val taskEdit = LocalStrings.current.parentTaskEdit
    val displayLanguage = LocalDisplayLanguage.current

    var hour by remember { mutableIntStateOf(schedule?.time?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7) }
    var minute by remember { mutableIntStateOf(schedule?.time?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0) }
    var title by remember { mutableStateOf(schedule?.title ?: "") }
    var emoji by remember { mutableStateOf(schedule?.emoji ?: "⏰") }
    var linkedTaskId by remember { mutableStateOf(schedule?.linkedTaskId) }
    var enabled by remember { mutableStateOf(schedule?.isEnabled ?: true) }

    val emojiOptions = listOf("⏰", "🌅", "🍳", "📖", "✏️", "🍚", "😴", "🏃", "🎹", "🧹", "🛁", "🥛")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (schedule == null) strings.addSchedule else strings.editTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 时间选择（步进器，避免文本输入光标问题）
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.timeLabel, style = MaterialTheme.typography.labelMedium)
                    com.hstudy.app.ui.common.HourMinuteStepper(
                        hour = hour,
                        minute = minute,
                        onTimeChange = { h, m -> hour = h; minute = m }
                    )
                }
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text(strings.titleFieldLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())

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
                Text(strings.linkedTaskOptional, style = MaterialTheme.typography.labelMedium)
                var taskExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = taskExpanded, onExpandedChange = { taskExpanded = it }) {
                    OutlinedTextField(
                        value = tasks.find { it.id == linkedTaskId }?.title?.let { localizedPresetText(it, displayLanguage) } ?: strings.none,
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = taskExpanded, onDismissRequest = { taskExpanded = false }) {
                        DropdownMenuItem(text = { Text(strings.none) }, onClick = { linkedTaskId = null; taskExpanded = false })
                        tasks.forEach { t ->
                            DropdownMenuItem(text = { Text(localizedPresetText(t.title, displayLanguage)) }, onClick = { linkedTaskId = t.id; taskExpanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.enableReminder, modifier = Modifier.weight(1f))
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
            }) { Text(taskEdit.saveButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(common.cancelButton) } }
    )
}

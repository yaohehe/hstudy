package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentAssignTaskScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val todayAssignments by app.assignmentRepository.getTodayAssignments().collectAsState(initial = emptyList())
    val taskTemplates = remember { app.taskRepository.getSingleTaskSuggestions() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("5") }
    var parentNote by remember { mutableStateOf("") }

    Scaffold(
        topBar = { HStudyTopBar(title = "📣 布置今日任务", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // ===== 快速布置表单 =====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("✍️ 布置新任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            // 从模板快选
                            Text("从模板快速选择：", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                taskTemplates.take(8).forEach { t ->
                                    SuggestionChip(
                                        onClick = {
                                            title = t.title
                                            description = t.description
                                            points = t.points.toString()
                                        },
                                        label = { Text("${t.title} +${t.points}") }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = title, onValueChange = { title = it },
                                label = { Text("任务标题") },
                                placeholder = { Text("如：今天帮妈妈洗碗") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = description, onValueChange = { description = it },
                                label = { Text("任务描述（选填）") },
                                modifier = Modifier.fillMaxWidth(), minLines = 2
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = points, onValueChange = { points = it.filter { c -> c.isDigit() } },
                                    label = { Text("积分值") },
                                    modifier = Modifier.weight(1f), singleLine = true
                                )
                                OutlinedTextField(
                                    value = parentNote, onValueChange = { parentNote = it },
                                    label = { Text("鼓励语（选填）") },
                                    placeholder = { Text("如：相信你一定能做到！") },
                                    modifier = Modifier.weight(2f), singleLine = true
                                )
                            }

                            Button(
                                onClick = {
                                    if (title.isBlank()) return@Button
                                    scope.launch {
                                        app.assignmentRepository.assignTask(
                                            title = title,
                                            description = description,
                                            points = points.toIntOrNull() ?: 5,
                                            parentNote = parentNote
                                        )
                                        title = ""; description = ""; points = "5"; parentNote = ""
                                        snackbarHostState.showSnackbar("✅ 任务已布置给孩子！")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = title.isNotBlank()
                            ) {
                                Text("📤 布置给孩子")
                            }
                        }
                    }

                    // ===== 今日已布置任务列表 =====
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📋 今日已布置 (${todayAssignments.size})",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        if (todayAssignments.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("今天还没有布置任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            todayAssignments.forEach { a ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("+${a.points}分", style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (a.parentNote.isNotEmpty()) {
                                                Text("💬 ${a.parentNote}", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                if (a.isCompleted) "✅ 孩子已完成" else "⏳ 待完成",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (a.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                app.assignmentRepository.deleteAssignment(a.id)
                                                snackbarHostState.showSnackbar("已删除任务")
                                            }
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

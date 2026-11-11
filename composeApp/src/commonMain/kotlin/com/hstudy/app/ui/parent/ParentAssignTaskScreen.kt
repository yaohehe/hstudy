package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentAssignTaskScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val todayAssignments by app.assignmentRepository.getTodayAssignments().collectAsState(initial = emptyList())
    val taskTemplates = remember { app.taskRepository.getSingleTaskSuggestions() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("5") }
    var parentNote by remember { mutableStateOf("") }

    val strings = LocalStrings.current.parentAssignTask
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
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
                            Text(strings.formHeading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            // 从模板快选
                            Text(strings.templateQuickSelect, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                taskTemplates.take(8).forEach { t ->
                                    SuggestionChip(
                                        onClick = {
                                            title = t.title
                                            description = t.description
                                            points = t.points.toString()
                                        },
                                        label = { Text(strings.suggestionChip(localizedPresetText(t.title, displayLanguage), t.points)) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = title, onValueChange = { title = it },
                                label = { Text(strings.titleLabel) },
                                placeholder = { Text(strings.titlePlaceholder) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            OutlinedTextField(
                                value = description, onValueChange = { description = it },
                                label = { Text(strings.descriptionLabel) },
                                modifier = Modifier.fillMaxWidth(), minLines = 2
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = points, onValueChange = { points = it.filter { c -> c.isDigit() } },
                                    label = { Text(strings.pointsLabel) },
                                    modifier = Modifier.weight(1f), singleLine = true
                                )
                                OutlinedTextField(
                                    value = parentNote, onValueChange = { parentNote = it },
                                    label = { Text(strings.noteLabel) },
                                    placeholder = { Text(strings.notePlaceholder) },
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
                                        snackbarHostState.showSnackbar(strings.assignedMessage)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = title.isNotBlank()
                            ) {
                                Text(strings.assignButton)
                            }
                        }
                    }

                    // ===== 今日已布置任务列表 =====
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.todayHeading(todayAssignments.size),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        if (todayAssignments.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(strings.emptyState, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text(localizedPresetText(a.title, displayLanguage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(strings.pointsBadge(a.points), style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (a.parentNote.isNotEmpty()) {
                                                Text("💬 ${a.parentNote}", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                if (a.isCompleted) strings.completedStatus else strings.pendingStatus,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (a.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                app.assignmentRepository.deleteAssignment(a.id)
                                                snackbarHostState.showSnackbar(strings.deletedMessage)
                                            }
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription,
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

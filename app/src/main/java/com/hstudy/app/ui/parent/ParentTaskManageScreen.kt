package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentTaskManageScreen(
    onNavigateBack: () -> Unit,
    onEditTask: (Long) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }

    val filteredTasks = if (selectedCategory == null) tasks else tasks.filter { it.category == selectedCategory }

    Scaffold(
        topBar = { HStudyTopBar(title = "任务管理", onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditTask(0L) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加任务")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 分类筛选
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.BASIC,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.BASIC) null else TaskCategory.BASIC },
                    label = { Text("基础") }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.ENHANCEMENT,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.ENHANCEMENT) null else TaskCategory.ENHANCEMENT },
                    label = { Text("提升") }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.HABIT,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.HABIT) null else TaskCategory.HABIT },
                    label = { Text("习惯") }
                )
            }

            // 任务列表
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskManageCard(task = task, onEdit = { onEditTask(task.id) }, onDelete = {
                                scope.launch { app.taskRepository.deleteTask(task.id) }
                            })
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskManageCard(
    task: TaskEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryLabel = when (task.category) {
        TaskCategory.BASIC -> "基础"
        TaskCategory.ENHANCEMENT -> "提升"
        TaskCategory.HABIT -> "习惯"
    }
    val categoryColor = when (task.category) {
        TaskCategory.BASIC -> MaterialTheme.colorScheme.primary
        TaskCategory.ENHANCEMENT -> MaterialTheme.colorScheme.tertiary
        TaskCategory.HABIT -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(categoryLabel, style = MaterialTheme.typography.labelSmall) },
                        border = null
                    )
                }
                if (task.description.isNotEmpty()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("基础 ${task.points}分${if (task.bonusPoints > 0) " · 奖励 +${task.bonusPoints}分" else ""}",
                    style = MaterialTheme.typography.labelMedium, color = categoryColor)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

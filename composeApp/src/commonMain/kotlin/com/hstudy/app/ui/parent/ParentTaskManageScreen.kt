package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentTaskManageScreen(
    onNavigateBack: () -> Unit,
    onEditTask: (Long) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }

    val strings = LocalStrings.current.parentTaskManage
    val taskList = LocalStrings.current.taskList
    val parentHome = LocalStrings.current.parentHome

    val filteredTasks = if (selectedCategory == null) tasks else tasks.filter { it.category == selectedCategory }

    Scaffold(
        topBar = { HStudyTopBar(title = parentHome.menuTaskManageTitle, onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditTask(0L) }) {
                Icon(Icons.Filled.Add, contentDescription = strings.addContentDescription)
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
                    label = { Text(taskList.filterAll) }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.BASIC,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.BASIC) null else TaskCategory.BASIC },
                    label = { Text(taskList.filterBasic) }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.ENHANCEMENT,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.ENHANCEMENT) null else TaskCategory.ENHANCEMENT },
                    label = { Text(taskList.filterEnhancement) }
                )
                FilterChip(
                    selected = selectedCategory == TaskCategory.HABIT,
                    onClick = { selectedCategory = if (selectedCategory == TaskCategory.HABIT) null else TaskCategory.HABIT },
                    label = { Text(taskList.filterHabits) }
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
    val strings = LocalStrings.current.parentTaskManage
    val taskList = LocalStrings.current.taskList
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current

    val categoryLabel = when (task.category) {
        TaskCategory.BASIC -> taskList.filterBasic
        TaskCategory.ENHANCEMENT -> taskList.filterEnhancement
        TaskCategory.HABIT -> taskList.filterHabits
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
                    Text(localizedPresetText(task.title, displayLanguage), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(categoryLabel, style = MaterialTheme.typography.labelSmall) },
                        border = null
                    )
                }
                if (task.description.isNotEmpty()) {
                    Text(localizedPresetText(task.description, displayLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(strings.pointsLine(task.points, task.bonusPoints),
                    style = MaterialTheme.typography.labelMedium, color = categoryColor)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = strings.editContentDescription) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

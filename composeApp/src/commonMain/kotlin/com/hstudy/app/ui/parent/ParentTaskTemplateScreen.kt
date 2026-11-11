package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hstudy.app.data.repository.TaskTemplate
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentTaskTemplateScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val templates = app.taskRepository.getPresets()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmDialog by remember { mutableStateOf<TaskTemplate?>(null) }
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())

    val strings = LocalStrings.current.parentTaskTemplate
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ResponsiveContent(
            windowSizeClass = windowSizeClass,
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 说明
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.usageHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(strings.usageHint,
                            style = MaterialTheme.typography.bodySmall)
                        Text(strings.currentTaskCount(tasks.size), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }

                // 模板列表
                templates.forEach { template ->
                    TemplateCard(
                        template = template,
                        onApply = { showConfirmDialog = template }
                    )
                }
            }
        }
    }

    // 确认对话框
    showConfirmDialog?.let { template ->
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text(strings.applyDialogTitle(template.emoji, localizedPresetText(template.name, displayLanguage))) },
            text = {
                Column {
                    Text(strings.replaceHint(template.tasks.size),
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(strings.previewHeading, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    template.tasks.take(5).forEach { task ->
                        val catEmoji = when(task.category) {
                            com.hstudy.app.data.local.entity.TaskCategory.BASIC -> "📝"
                            com.hstudy.app.data.local.entity.TaskCategory.ENHANCEMENT -> "📈"
                            com.hstudy.app.data.local.entity.TaskCategory.HABIT -> "🏆"
                        }
                        Text(strings.previewLine(catEmoji, localizedPresetText(task.title, displayLanguage), task.points),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (template.tasks.size > 5) {
                        Text(strings.moreTasks(template.tasks.size - 5), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.taskRepository.applyTemplate(template)
                        showConfirmDialog = null
                        snackbarHostState.showSnackbar(strings.appliedMessage(localizedPresetText(template.name, displayLanguage)))
                    }
                }) { Text(strings.confirmButton) }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = null }) { Text(common.cancelButton) } }
        )
    }
}

@Composable
private fun TemplateCard(
    template: TaskTemplate,
    onApply: () -> Unit
) {
    val strings = LocalStrings.current.parentTaskTemplate
    val displayLanguage = LocalDisplayLanguage.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(template.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(localizedPresetText(template.name, displayLanguage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(localizedPresetText(template.ageRange, displayLanguage), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(localizedPresetText(template.description, displayLanguage), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            // 分类统计
            val basic = template.tasks.count { it.category == com.hstudy.app.data.local.entity.TaskCategory.BASIC }
            val enhance = template.tasks.count { it.category == com.hstudy.app.data.local.entity.TaskCategory.ENHANCEMENT }
            val habit = template.tasks.count { it.category == com.hstudy.app.data.local.entity.TaskCategory.HABIT }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(onClick = {}, label = { Text(strings.basicCount(basic)) })
                AssistChip(onClick = {}, label = { Text(strings.enhancementCount(enhance)) })
                AssistChip(onClick = {}, label = { Text(strings.habitCount(habit)) })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.applyButton)
            }
        }
    }
}

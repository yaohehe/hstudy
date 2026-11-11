package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentTaskEditScreen(
    taskId: Long,
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("5") }
    var bonusPoints by remember { mutableStateOf("0") }
    var category by remember { mutableStateOf(TaskCategory.BASIC) }
    var frequency by remember { mutableStateOf(TaskFrequency.DAILY) }
    var difficulty by remember { mutableIntStateOf(1) }
    var isMandatory by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val strings = LocalStrings.current.parentTaskEdit
    val displayLanguage = LocalDisplayLanguage.current

    // 单任务模板库
    val suggestions = remember { app.taskRepository.getSingleTaskSuggestions() }

    // 加载已有任务数据
    LaunchedEffect(taskId) {
        if (taskId > 0) {
            isLoading = true
            val task = app.taskRepository.getTaskById(taskId)
            task?.let {
                title = it.title
                description = it.description
                points = it.points.toString()
                bonusPoints = it.bonusPoints.toString()
                category = it.category
                frequency = it.frequency
                difficulty = it.difficulty
                isMandatory = it.isMandatory
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = if (taskId == 0L) strings.newTitle else strings.editTitle,
                onNavigateBack = onNavigateBack,
                actions = {
                    TextButton(onClick = {
                        if (title.isBlank()) return@TextButton
                        scope.launch {
                            val task = TaskEntity(
                                id = taskId,
                                title = title,
                                description = description,
                                category = category,
                                points = points.toIntOrNull() ?: 5,
                                bonusPoints = bonusPoints.toIntOrNull() ?: 0,
                                frequency = frequency,
                                difficulty = difficulty,
                                isMandatory = isMandatory && frequency == TaskFrequency.DAILY
                            )
                            if (taskId == 0L) app.taskRepository.addTask(task)
                            else app.taskRepository.updateTask(task)
                            onNavigateBack()
                        }
                    }) {
                        Text(strings.saveButton, style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.titleLabel) },
                    placeholder = { Text(strings.titlePlaceholder) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 从模板快速选择
                Text(strings.suggestionsHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { s ->
                        val catEmoji = when (s.category) {
                            TaskCategory.BASIC -> "📝"
                            TaskCategory.ENHANCEMENT -> "📈"
                            TaskCategory.HABIT -> "🏆"
                        }
                        SuggestionChip(
                            onClick = {
                                title = s.title
                                description = s.description
                                category = s.category
                                points = s.points.toString()
                                bonusPoints = s.bonusPoints.toString()
                            },
                            label = { Text(strings.suggestionChip(catEmoji, localizedPresetText(s.title, displayLanguage), s.points)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.descriptionLabel) },
                    placeholder = { Text(strings.descriptionPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 分类选择
                Text(strings.categoryHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskCategory.entries.forEach { cat ->
                        val label = when (cat) {
                            TaskCategory.BASIC -> strings.categoryBasic
                            TaskCategory.ENHANCEMENT -> strings.categoryEnhancement
                            TaskCategory.HABIT -> strings.categoryHabit
                        }
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 任务频率
                Text(strings.frequencyHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFrequency.entries.forEach { freq ->
                        val label = when (freq) {
                            TaskFrequency.DAILY -> strings.freqDaily
                            TaskFrequency.WEEKLY -> strings.freqWeekly
                            TaskFrequency.MONTHLY -> strings.freqMonthly
                            TaskFrequency.ONCE -> strings.freqOnce
                        }
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 每日必须开关（仅每日任务可选，未完成自动扣分）
                if (frequency == TaskFrequency.DAILY) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.mandatoryTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(strings.mandatoryHint(points.toIntOrNull() ?: 5), 
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isMandatory,
                                onCheckedChange = { isMandatory = it }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 难度等级
                Text(strings.difficultyHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to strings.difficultyNormal, 2 to strings.difficultyAdvanced, 3 to strings.difficultyChallenge).forEach { (level, label) ->
                        FilterChip(
                            selected = difficulty == level,
                            onClick = { difficulty = level },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    when (difficulty) {
                        2 -> strings.difficultyNoteAdvanced
                        3 -> strings.difficultyNoteChallenge
                        else -> strings.difficultyNoteNormal
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 积分设置
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = points,
                        onValueChange = { points = it.filter { c -> c.isDigit() } },
                        label = { Text(strings.basePointsLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bonusPoints,
                        onValueChange = { bonusPoints = it.filter { c -> c.isDigit() } },
                        label = { Text(strings.bonusPointsLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (taskId > 0L) {
                    Button(
                        onClick = {
                            scope.launch {
                                app.taskRepository.deleteTask(taskId)
                                onNavigateBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.deleteButton)
                    }
                }
            }
        }
    }
}

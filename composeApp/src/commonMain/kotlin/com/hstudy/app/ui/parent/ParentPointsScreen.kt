package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ParentPointsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())

    // 近7天打卡记录（用于任务积分排行）
    val sevenDaysAgoMillis = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis }
    val recentCompletions by app.pointRepository.getCompletionsBetween(sevenDaysAgoMillis, com.hstudy.app.ui.dt.nowMillis()).collectAsState(initial = emptyList())

    var showAdjustDialog by remember { mutableStateOf(false) }
    var adjustPoints by remember { mutableStateOf("") }
    var adjustNote by remember { mutableStateOf("") }

    val strings = LocalStrings.current.parentPoints
    val common = LocalStrings.current.common
    val parentHome = LocalStrings.current.parentHome
    val displayLanguage = LocalDisplayLanguage.current

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = parentHome.menuPointsTitle,
                onNavigateBack = onNavigateBack,
                actions = { PointsBadge(points = totalPoints) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdjustDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = strings.adjustContentDescription)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // ===== 每日得分统计（近7天）=====
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(strings.dailyStatsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val shortFmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                                val last7 = (6 downTo 0).map { d ->
                                    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }
                                    dayFmt.format(c.time) to shortFmt.format(c.time)
                                }
                                last7.forEach { (dateStr, shortDate) ->
                                    val dayRecords = records.filter { dayFmt.format(Date(it.date)) == dateStr }
                                    val earned = dayRecords.filter { it.points > 0 }.sumOf { it.points }
                                    val spent = dayRecords.filter { it.points < 0 }.sumOf { -it.points }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(shortDate, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("+${earned}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        if (spent > 0) Text("-${spent}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                        else Spacer(modifier = Modifier.weight(1f))
                                        Text(strings.netValue(earned - spent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(50.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ===== 任务积分排行（近7天）=====
                    item {
                        val taskMap = recentCompletions.groupBy { it.taskId }
                        val taskRanking = taskMap.map { (taskId, comps) ->
                            val task = tasks.find { it.id == taskId }
                            val title = task?.let { localizedPresetText(it.title, displayLanguage) } ?: strings.taskFallbackTitle(taskId)
                            val count = comps.size
                            // 使用任务实际积分（审核制下 pointsEarned 可能为 0，用任务定义积分更准确）
                            val ptsPerTime = task?.effectivePoints() ?: 0
                            val totalPts = count * ptsPerTime
                            Triple(title, count, totalPts)
                        }.sortedByDescending { it.third }.take(5)

                        if (taskRanking.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.taskRankingTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    taskRanking.forEachIndexed { idx, (title, count, pts) ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${idx + 1}.", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                            Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                            Text(strings.timesCount(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                                            Text("+${pts}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Text(strings.allRecordsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    items(records) { record ->
                        PointRecordCard(record = record)
                    }
                    if (records.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(strings.emptyRecords, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // 手动调整积分对话框
    if (showAdjustDialog) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text(strings.adjustDialogTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = adjustPoints,
                        onValueChange = { adjustPoints = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text(strings.adjustPointsLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adjustNote,
                        onValueChange = { adjustNote = it },
                        label = { Text(strings.adjustNoteLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pts = adjustPoints.toIntOrNull() ?: return@TextButton
                    scope.launch {
                        app.pointRepository.adjustPoints(pts, adjustNote)
                        showAdjustDialog = false
                        adjustPoints = ""
                        adjustNote = ""
                    }
                }) { Text(common.confirmButton) }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) { Text(common.cancelButton) }
            }
        )
    }
}

@Composable
private fun PointRecordCard(record: PointRecordEntity) {
    val strings = LocalStrings.current.parentPoints
    val typeLabel = when (record.type) {
        PointRecordType.TASK_COMPLETE -> strings.typeTaskComplete
        PointRecordType.BONUS -> strings.typeBonus
        PointRecordType.ADJUSTMENT -> strings.typeAdjustment
        PointRecordType.REWARD_EXCHANGE -> strings.typeRewardExchange
        PointRecordType.REVERSAL -> strings.typeReversal
    }
    val isPositive = record.points >= 0
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(typeLabel, style = MaterialTheme.typography.titleSmall)
                if (record.note.isNotEmpty()) {
                    Text(record.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateFormat.format(Date(record.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${if (isPositive) "+" else ""}${record.points}",
                style = MaterialTheme.typography.titleLarge,
                color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

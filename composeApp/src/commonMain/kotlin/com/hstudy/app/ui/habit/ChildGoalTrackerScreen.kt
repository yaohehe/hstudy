package com.hstudy.app.ui.habit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.*
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildGoalTrackerScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.childGoalTracker
    val app = AppGraph
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())

    // 连续打卡天数
    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { streak = app.pointRepository.getCurrentStreak() }

    val todayCompleted = completedIds.size
    val todayTotal = tasks.size
    val completionRate = if (todayTotal > 0) (todayCompleted.toFloat() / todayTotal * 100).toInt() else 0

    // 本周统计
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
    val weekStart = cal.timeInMillis
    val cal2 = Calendar.getInstance()
    cal2.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    cal2.set(Calendar.HOUR_OF_DAY, 23); cal2.set(Calendar.MINUTE, 59); cal2.set(Calendar.SECOND, 59)
    val weekEnd = cal2.timeInMillis

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 今日目标
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.completionRateTitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${completionRate}%", style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(strings.tasksCompleted(todayCompleted, todayTotal), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val animProgress by animateFloatAsState(
                        targetValue = if (todayTotal > 0) todayCompleted.toFloat() / todayTotal else 0f,
                        animationSpec = tween(800), label = "progress"
                    )
                    LinearProgressIndicator(
                        progress = animProgress,
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // 连续打卡
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GoalStatItem("🔥", "${streak}", strings.streakLabel)
                    GoalStatItem("✅", "${todayCompleted}", strings.todayDoneLabel)
                    GoalStatItem("📋", "${todayTotal}", strings.todayTotalLabel)
                }
            }

            // 每日目标建议
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(strings.dailyGoalsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val suggestions = listOf(
                        strings.suggestionAllTasks to (todayCompleted == todayTotal && todayTotal > 0),
                        strings.suggestionThreeBasic to (completedIds.size >= 3),
                        strings.suggestionPomodoro to false,
                        strings.suggestionReview to false
                    )

                    suggestions.forEach { (goal, achieved) ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (achieved) "✅" else "⬜", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                goal,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (achieved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 鼓励
            val encouragement = when {
                completionRate == 100 -> strings.encouragePerfect
                completionRate >= 70 -> strings.encourageHigh
                completionRate >= 30 -> strings.encourageMid
                else -> strings.encourageStart
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(encouragement, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GoalStatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

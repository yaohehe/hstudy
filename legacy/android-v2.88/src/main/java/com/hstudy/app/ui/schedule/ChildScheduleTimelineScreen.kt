package com.hstudy.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.DailyScheduleEntity
import com.hstudy.app.ui.common.AmbientBackground
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import java.util.Calendar

/**
 * 儿童端 - 今日时间线：按时间排列的日程卡片，已过的标灰、当前时段高亮
 */
@Composable
fun ChildScheduleTimelineScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val schedules by app.scheduleRepository.getEnabledSchedules().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())

    // 当前时间（分钟数）
    val cal = Calendar.getInstance()
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val todayDay = com.hstudy.app.data.repository.ScheduleRepository.getTodayDayOfWeek()

    // 过滤出今天重复的日程
    val todaySchedules = schedules.filter { s ->
        s.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(todayDay)
    }

    Scaffold(
        topBar = { HStudyTopBar(title = "📅 今日时间线", onNavigateBack = onNavigateBack) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AmbientBackground()
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    // 当前时间提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🕐", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("现在 ${String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("看看今天还有哪些安排吧～", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (todaySchedules.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🎈", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("今天没有日程安排", style = MaterialTheme.typography.titleMedium)
                                Text("轻松的一天，自由安排吧！", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        todaySchedules.forEach { schedule ->
                            val parts = schedule.time.split(":")
                            val schedMinutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
                                (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                            val linkedTask = tasks.find { it.id == schedule.linkedTaskId }

                            TimelineItem(
                                schedule = schedule,
                                linkedTaskTitle = linkedTask?.title,
                                state = when {
                                    schedMinutes < nowMinutes - 30 -> TimelineState.PAST
                                    schedMinutes <= nowMinutes + 30 -> TimelineState.CURRENT
                                    else -> TimelineState.UPCOMING
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class TimelineState { PAST, CURRENT, UPCOMING }

@Composable
private fun TimelineItem(
    schedule: DailyScheduleEntity,
    linkedTaskTitle: String?,
    state: TimelineState
) {
    val (dotColor, cardColor) = when (state) {
        TimelineState.PAST -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
        TimelineState.CURRENT -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
        TimelineState.UPCOMING -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.surface
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 时间轴
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(64.dp)
        ) {
            Text(schedule.time, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = dotColor)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.size(14.dp).background(dotColor, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 日程卡片
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (state == TimelineState.CURRENT) 4.dp else 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(schedule.emoji, fontSize = 26.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        if (state == TimelineState.CURRENT) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("👈 现在", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else if (state == TimelineState.PAST) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    if (linkedTaskTitle != null) {
                        Text("🔗 $linkedTaskTitle · 完成后记得打卡哦", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

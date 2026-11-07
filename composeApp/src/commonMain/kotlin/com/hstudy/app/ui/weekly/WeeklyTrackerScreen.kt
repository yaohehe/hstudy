package com.hstudy.app.ui.weekly

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val dayIcons = listOf("❤️", "⭐", "🖊️", "🖍️", "📐", "📖", "✈️")

@Composable
fun WeeklyTrackerScreen(
    onNavigateBack: () -> Unit,
    isParentView: Boolean,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)

    // 获取本周每天的日期
    val weekDates = remember { getWeekDates() }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 获取每天的打卡数据
    val dailyCompletions = weekDates.map { date ->
        val dateStr = dateFormat.format(date)
        val completedIds by app.pointRepository.getCompletedTaskIdsForDate(dateStr).collectAsState(initial = emptyList())
        dateStr to completedIds
    }

    val basicTasks = tasks.filter { it.category == TaskCategory.BASIC }
    val enhanceTasks = tasks.filter { it.category == TaskCategory.ENHANCEMENT }
    val habitTasks = tasks.filter { it.category == TaskCategory.HABIT }

    // 计算本周积分
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val weekStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_WEEK, 7)
    val weekEnd = cal.timeInMillis
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    val weeklyRecords = records.filter { it.date in weekStart until weekEnd }
    val weeklyPoints = weeklyRecords.sumOf { it.points }

    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val cellWidth = if (isTablet) 80.dp else 56.dp
    val labelWidth = if (isTablet) 100.dp else 72.dp

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = if (isParentView) "周报总览" else "本周打卡",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // 周积分汇总卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("本周获得积分", style = MaterialTheme.typography.labelMedium)
                            Text("+$weeklyPoints", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        Divider(modifier = Modifier.height(48.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("总积分余额", style = MaterialTheme.typography.labelMedium)
                            Text("⭐ $totalPoints", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ====== 基础学习任务表格 ======
                SectionTable(
                    title = "📝 基础学习任务",
                    tasks = basicTasks,
                    weekDates = weekDates,
                    dailyCompletions = dailyCompletions,
                    cellWidth = cellWidth,
                    labelWidth = labelWidth,
                    isTablet = isTablet
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ====== 提升学习任务表格 ======
                SectionTable(
                    title = "📈 提升学习任务",
                    tasks = enhanceTasks,
                    weekDates = weekDates,
                    dailyCompletions = dailyCompletions,
                    cellWidth = cellWidth,
                    labelWidth = labelWidth,
                    isTablet = isTablet
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ====== 习惯与态度奖励表格 ======
                SectionTable(
                    title = "🏆 习惯与态度奖励",
                    tasks = habitTasks,
                    weekDates = weekDates,
                    dailyCompletions = dailyCompletions,
                    cellWidth = cellWidth,
                    labelWidth = labelWidth,
                    isTablet = isTablet
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 每日积分汇总行
                DailyPointsSummary(
                    weekDates = weekDates,
                    dailyCompletions = dailyCompletions,
                    tasks = tasks,
                    cellWidth = cellWidth,
                    labelWidth = labelWidth
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTable(
    title: String,
    tasks: List<com.hstudy.app.data.local.entity.TaskEntity>,
    weekDates: List<Date>,
    dailyCompletions: List<Pair<String, List<Long>>>,
    cellWidth: androidx.compose.ui.unit.Dp,
    labelWidth: androidx.compose.ui.unit.Dp,
    isTablet: Boolean
) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

    val scrollState = rememberScrollState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 表头行
            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                // 空角落
                Box(modifier = Modifier.width(labelWidth).height(36.dp), contentAlignment = Alignment.Center) {}
                // 星期标题
                dayNames.forEachIndexed { index, day ->
                    Box(
                        modifier = Modifier.width(cellWidth).height(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayIcons[index], fontSize = 12.sp)
                            Text(day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Divider()

            // 每个任务一行
            tasks.forEach { task ->
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // 任务名
                    Box(
                        modifier = Modifier.width(labelWidth).height(40.dp).padding(end = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(task.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    // 每天的打卡状态
                    dailyCompletions.forEach { (_, completedIds) ->
                        Box(
                            modifier = Modifier.width(cellWidth).height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.id in completedIds) {
                                Text("✅", fontSize = 16.sp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DailyPointsSummary(
    weekDates: List<Date>,
    dailyCompletions: List<Pair<String, List<Long>>>,
    tasks: List<com.hstudy.app.data.local.entity.TaskEntity>,
    cellWidth: androidx.compose.ui.unit.Dp,
    labelWidth: androidx.compose.ui.unit.Dp
) {
    Text("📊 每日积分汇总", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

    val taskMap = tasks.associateBy { it.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 表头
            Row {
                Box(modifier = Modifier.width(labelWidth).height(36.dp), contentAlignment = Alignment.Center) {}
                dayNames.forEach { day ->
                    Box(modifier = Modifier.width(cellWidth).height(36.dp), contentAlignment = Alignment.Center) {
                        Text(day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 每日积分
            Row {
                Box(
                    modifier = Modifier.width(labelWidth).height(40.dp).padding(end = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("💰 积分", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                dailyCompletions.forEach { (_, completedIds) ->
                    val dayPoints = completedIds.sumOf { taskMap[it]?.points ?: 0 }
                    Box(
                        modifier = Modifier.width(cellWidth).height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayPoints > 0) {
                            Text(
                                "+$dayPoints",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text("-", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 获取本周一到周日的日期列表
 */
private fun getWeekDates(): List<Date> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    return (0 until 7).map { i ->
        if (i == 0) cal.time else {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.time
        }
    }
}

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.data.repository.ReportRepository
import com.hstudy.app.data.repository.TaskRepository
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val dayIcons = listOf("❤️", "⭐", "🖊️", "🖍️", "📐", "📖", "✈️")

/** Single week source of truth shared by the table dates, header total and daily summary. */
internal data class WeeklyTrackerAnchor(val weekStart: LocalDate)

@Composable
fun WeeklyTrackerScreen(
    onNavigateBack: () -> Unit,
    isParentView: Boolean,
    windowSizeClass: WindowSizeClass
) {
    WeeklyTrackerScreen(
        onNavigateBack = onNavigateBack,
        isParentView = isParentView,
        windowSizeClass = windowSizeClass,
        pointRepository = AppGraph.pointRepository,
        taskRepository = AppGraph.taskRepository,
        reportRepository = AppGraph.reportRepository,
        today = LocalDate.parse(PointRepository.getTodayString())
    )
}

@Composable
internal fun WeeklyTrackerScreen(
    onNavigateBack: () -> Unit,
    isParentView: Boolean,
    windowSizeClass: WindowSizeClass,
    pointRepository: PointRepository,
    taskRepository: TaskRepository,
    reportRepository: ReportRepository,
    today: LocalDate
) {
    val strings = LocalStrings.current.weeklyTracker
    val tasks by taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val totalPoints by pointRepository.getTotalPoints().collectAsState(initial = 0)

    val anchor = remember(today) {
        WeeklyTrackerAnchor(today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY))
    }
    val weekDates = remember(anchor) {
        (0 until 7).map { offset ->
            Date(anchor.weekStart.plus(offset, DateTimeUnit.DAY)
                .atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds())
        }
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dailyPointsByDate by reportRepository.weeklyPoints(anchor.weekStart)
        .collectAsState(initial = emptyMap())
    val approvedByDate by reportRepository.approvedCompletionsBetween(
        anchor.weekStart, anchor.weekStart.plus(6, DateTimeUnit.DAY)
    ).collectAsState(initial = emptyMap())
    val dailyCompletions = weekDates.map { date ->
        val dateStr = dateFormat.format(date)
        val localDate = LocalDate.parse(dateStr)
        dateStr to approvedByDate[localDate].orEmpty().toList()
    }

    val basicTasks = tasks.filter { it.category == TaskCategory.BASIC }
    val enhanceTasks = tasks.filter { it.category == TaskCategory.ENHANCEMENT }
    val habitTasks = tasks.filter { it.category == TaskCategory.HABIT }
    val weeklyPoints = dailyPointsByDate.values.sum()

    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val cellWidth = if (isTablet) 80.dp else 56.dp
    val labelWidth = if (isTablet) 100.dp else 72.dp

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = if (isParentView) strings.titleParent else strings.titleChild,
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
                            Text(strings.weeklyPointsLabel, style = MaterialTheme.typography.labelMedium)
                            Text("+$weeklyPoints", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("weekly-points-total"))
                        }
                        Divider(modifier = Modifier.height(48.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strings.totalBalanceLabel, style = MaterialTheme.typography.labelMedium)
                            Text("⭐ $totalPoints", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ====== 基础学习任务表格 ======
                SectionTable(
                    title = strings.basicsTitle,
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
                    title = strings.enhanceTitle,
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
                    title = strings.habitTitle,
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
                    dailyPointsByDate = dailyPointsByDate,
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
    val strings = LocalStrings.current.weeklyTracker
    val displayLanguage = LocalDisplayLanguage.current
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

    val scrollState = rememberScrollState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 表头行
            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                // 空角落
                Box(modifier = Modifier.width(labelWidth).height(36.dp), contentAlignment = Alignment.Center) {}
                // 星期标题
                strings.weekdayNames.forEachIndexed { index, day ->
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
                        Text(
                            localizedPresetText(task.title, displayLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
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
                    Text(strings.noTasks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DailyPointsSummary(
    weekDates: List<Date>,
    dailyPointsByDate: Map<LocalDate, Int>,
    cellWidth: androidx.compose.ui.unit.Dp,
    labelWidth: androidx.compose.ui.unit.Dp
) {
    val strings = LocalStrings.current.weeklyTracker
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    Text(strings.dailySummaryTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 表头
            Row {
                Box(modifier = Modifier.width(labelWidth).height(36.dp), contentAlignment = Alignment.Center) {}
                strings.weekdayNames.forEach { day ->
                    Box(modifier = Modifier.width(cellWidth).height(36.dp), contentAlignment = Alignment.Center) {
                        Text(day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 每日积分
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier.width(labelWidth).height(40.dp).padding(end = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(strings.pointsRowLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                weekDates.forEach { date ->
                    val dateStr = dateFormat.format(date)
                    val dayPoints = dailyPointsByDate[LocalDate.parse(dateStr)] ?: 0
                    Box(
                        modifier = Modifier.width(cellWidth).height(40.dp)
                            .testTag("weekly-points-$dateStr"),
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

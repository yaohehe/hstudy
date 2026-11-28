package com.hstudy.app.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.repository.CompletionRateReport
import com.hstudy.app.data.repository.CompletionRateSource
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.ResponsiveTwoColumn
import com.hstudy.app.ui.common.bonusNoteDisplayText
import com.hstudy.app.ui.common.wishNoteDisplayText
import com.hstudy.app.ui.theme.chartPalette
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

@Composable
fun ParentDashboardScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.parentDashboard
    val commonStrings = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    // D-08（v3.3.1）：账本 note 只剩业务键，本页「最近积分变动」是第三个消费 note 的面板
    // （指令 §3.3 只点了积分明细两页，这页漏了就会把 `wish_deposit:3` 直接念给家长看），
    // 所以同样回查 wish_goals 走 [wishNoteDisplayText] 派生。
    // D-12（v3.3.2）：bonus_* 三键同面板走 [bonusNoteDisplayText] 派生（键自含参数、无需回查）。
    val wishGoals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val wishNamesById = remember(wishGoals) { wishGoals.associate { it.id to it.name } }
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val totalFocusMin by app.focusRepository.getTotalFocusMinutes().collectAsState(initial = 0)
    // §3.4-报表2：待审积分预估（只读展示流，与 ParentApprovalScreen 同源查询，不新增写路径）
    val allApprovals by app.pointRepository.getAllApprovals().collectAsState(initial = emptyList())

    // 孩子今日学习表现评分所需数据
    val todayStr = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val today = LocalDate.parse(todayStr)
    val todayFocusMin by app.focusRepository.getTotalFocusMinutesForDate(todayStr).collectAsState(initial = 0)
    val todayStartMillis = remember(todayStr) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayReviewCount by app.mistakeRepository.getReviewCountSince(todayStartMillis).collectAsState(initial = 0)
    val todayCompletion by app.reportRepository.completionRateReport(today)
        .collectAsState(initial = CompletionRateReport(0, 0, 0, CompletionRateSource.LEGACY_ACTIVE_TASK_FALLBACK))

    // 本周 vs 上周对比所需：最近14天专注与复习数据
    val last14StartMillis = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }.timeInMillis }
    val focusData14 by app.focusRepository.getDailyFocusMinutesSince(last14StartMillis).collectAsState(initial = emptyList())
    val reviews14 by app.mistakeRepository.getReviewsSince(last14StartMillis).collectAsState(initial = emptyList())

    // 本周已发放收入与消耗分列；收入来自权威账本聚合，退款不算收入。
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val weekStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_WEEK, 7)
    val weekEnd = cal.timeInMillis
    val weeklyRecords = records.filter { it.date in weekStart until weekEnd }
    val weeklySpent = weeklyRecords.filter { it.points < 0 }.sumOf { -it.points }

    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val last7LocalDates = (6 downTo 0).map { daysAgo -> today.minus(daysAgo, DateTimeUnit.DAY) }
    val prev7LocalDates = (13 downTo 7).map { daysAgo -> today.minus(daysAgo, DateTimeUnit.DAY) }
    val awardedByDate by app.reportRepository.dailyAwardedPoints(prev7LocalDates.first(), last7LocalDates.last())
        .collectAsState(initial = emptyMap())
    val weekStartDate = LocalDate.parse(dayFormat.format(Date(weekStart)))
    val weeklyEarned = awardedByDate.filterKeys { it >= weekStartDate }.values.sum()
    val last7Days = last7LocalDates.map { it.toString() }
    val dailyEarned = last7LocalDates.map { awardedByDate[it] ?: 0 }
    // 已结算 vs 待审预估（指令 3.4-报表2）：待审单按 date 归属日聚合 requestedPoints
    val dailyPending = last7Days.map { dateStr ->
        allApprovals.filter { it.status == ApprovalStatus.PENDING && it.date == dateStr }.sumOf { it.requestedPoints }
    }
    val hasPending = dailyPending.any { it > 0 }

    // 分类统计
    val basicTasks = tasks.count { it.category == TaskCategory.BASIC }
    val enhanceTasks = tasks.count { it.category == TaskCategory.ENHANCEMENT }
    val habitTasks = tasks.count { it.category == TaskCategory.HABIT }

    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        topBar = { HStudyTopBar(title = strings.pageTitle, onNavigateBack = onNavigateBack) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // ===== 总积分大卡片 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(strings.totalPointsLabel, style = MaterialTheme.typography.titleMedium)
                            Text("${totalPoints}", style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // ===== 统计网格 =====
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashStatCard(strings.weeklyEarnedLabel, "+${weeklyEarned}", "⭐", Modifier.weight(1f))
                        DashStatCard(strings.weeklySpentLabel, "-${weeklySpent}", "💸", Modifier.weight(1f))
                        DashStatCard(strings.totalFocusLabel, "${totalFocusMin}m", "🍅", Modifier.weight(1f))
                        DashStatCard(strings.mistakesMasteredLabel, "${masteredCount}/${allMistakes.size}", "📚", Modifier.weight(1f))
                    }

                    // ===== 孩子今日学习表现（与家长同步的学习质量反馈）=====
                    val todayCheckinRate = todayCompletion.rate / 100f
                    val childScore = remember(todayCompletion.rate, todayFocusMin, todayReviewCount, displayLanguage) {
                        com.hstudy.app.ui.common.LearningScoreCalculator.calculate(todayCheckinRate, todayFocusMin, todayReviewCount, displayLanguage)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(childScore.gradeEmoji, style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.childPerformanceTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(strings.childPerformanceLine(childScore.grade, todayCompletion.completed, todayCompletion.total, todayFocusMin, todayReviewCount),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Text(strings.scoreValue(childScore.total, strings.scoreUnit), style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("💡 ${childScore.encouragement}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                LocalStrings.current.childGoalTracker.completionRateSourceLabel(
                                    todayCompletion.source == CompletionRateSource.DAILY_SNAPSHOT
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // ===== 孩子本周 vs 上周学习对比（让家长看见孩子的进步趋势）=====
                    val wdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val wLast7 = last7LocalDates.map { it.toString() }
                    val wPrev7 = prev7LocalDates.map { it.toString() }
                    val wFocusByDate = focusData14.associate { it.date to it.totalMinutes }
                    val wReviewByDate = reviews14.groupBy { wdf.format(Date(it.reviewedAt)) }.mapValues { it.value.size }
                    fun wPoints(dates: List<LocalDate>) = dates.sumOf { awardedByDate[it] ?: 0 }
                    val wThisFocus = wLast7.sumOf { wFocusByDate[it] ?: 0 }; val wLastFocus = wPrev7.sumOf { wFocusByDate[it] ?: 0 }
                    val wThisRev = wLast7.sumOf { wReviewByDate[it] ?: 0 }; val wLastRev = wPrev7.sumOf { wReviewByDate[it] ?: 0 }
                    val wThisPts = wPoints(last7LocalDates); val wLastPts = wPoints(prev7LocalDates)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val wVerdict = when {
                                wLastFocus + wLastRev + wLastPts == 0 -> strings.verdictNewStart
                                wThisFocus > wLastFocus || wThisPts > wLastPts -> strings.verdictImproved
                                wThisFocus == wLastFocus && wThisPts == wLastPts -> strings.verdictLevel
                                else -> strings.verdictDeclined
                            }
                            Text(strings.weekCompareTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(wVerdict, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            ParentCompareRow(strings.compareFocusLabel, strings.focusMinutesValue(wThisFocus, commonStrings.minuteLabel), wThisFocus - wLastFocus, commonStrings.minuteLabel)
                            ParentCompareRow(strings.compareReviewLabel, strings.mistakesReviewedValue(wThisRev), wThisRev - wLastRev, strings.reviewCountUnit)
                            ParentCompareRow(strings.comparePointsLabel, "$wThisPts", wThisPts - wLastPts, "")
                        }
                    }

                    // ===== 横屏双栏：左图表 + 右分析 =====
                    ResponsiveTwoColumn(
                        windowSizeClass = windowSizeClass,
                        leftWeight = 0.55f,
                        left = {
                            // 7日积分趋势折线图（指令 3.4-报表：Chart Palette 取色 + 已结算/待审预估双趋势区分）
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(strings.trendTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (dailyEarned.all { it == 0 } && dailyPending.all { it == 0 }) {
                                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                            Text(strings.noDataLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        val palette = chartPalette()
                                        val maxVal = (dailyEarned + dailyPending).max().toFloat().coerceAtLeast(1f)
                                        val lineColor = palette.line
                                        val dotColor = palette.dot
                                        val gridColor = palette.grid
                                        val pendingColor = palette.pending
                                        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                                            val chartW = size.width - 40f
                                            val chartH = size.height - 30f
                                            val startX = 20f
                                            val stepX = if (dailyEarned.size > 1) chartW / (dailyEarned.size - 1) else chartW
                                            for (i in 0..4) {
                                                val y = chartH * i / 4f
                                                drawLine(color = gridColor, start = Offset(startX, y), end = Offset(startX + chartW, y), strokeWidth = 1f)
                                            }
                                            val path = Path()
                                            dailyEarned.forEachIndexed { idx, value ->
                                                val x = startX + idx * stepX
                                                val y = chartH - (value / maxVal * chartH)
                                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                            }
                                            drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
                                            dailyEarned.forEachIndexed { idx, value ->
                                                val x = startX + idx * stepX
                                                val y = chartH - (value / maxVal * chartH)
                                                drawCircle(color = dotColor, radius = 5f, center = Offset(x, y))
                                            }
                                            // 待审预估：虚线弱化，明确"未入账"语义（与实线已结算趋势分离）
                                            if (hasPending) {
                                                val pendingPath = Path()
                                                dailyPending.forEachIndexed { idx, value ->
                                                    val x = startX + idx * stepX
                                                    val y = chartH - (value / maxVal * chartH)
                                                    if (idx == 0) pendingPath.moveTo(x, y) else pendingPath.lineTo(x, y)
                                                }
                                                drawPath(
                                                    pendingPath,
                                                    color = pendingColor,
                                                    style = Stroke(
                                                        width = 2f,
                                                        cap = StrokeCap.Round,
                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                                    )
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).background(lineColor, CircleShape))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.trendSettledLegend, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (hasPending) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(8.dp).background(pendingColor, CircleShape))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(strings.trendPendingLegend, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(last7Days.first().takeLast(5), style = MaterialTheme.typography.labelSmall)
                                            Text(last7Days.last().takeLast(5), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        },
                        right = {
                            // 任务分类分布
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(strings.categoryTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // 分类分布走 chartPalette 系列色（指令 3.4-报表1）
                                    val catPalette = chartPalette()
                                    CategoryBar(strings.categoryBasicLabel, basicTasks, tasks.size, catPalette.series[0])
                                    CategoryBar(strings.categoryEnhanceLabel, enhanceTasks, tasks.size, catPalette.series[1])
                                    CategoryBar(strings.categoryHabitLabel, habitTasks, tasks.size, catPalette.series[2])
                                }
                            }
                        }
                    )

                    // ===== 薄弱环节提示 =====
                    val weakAreas = mutableListOf<String>()
                    if (allMistakes.isNotEmpty() && masteredCount.toFloat() / allMistakes.size < 0.3f) {
                        weakAreas.add(strings.weakAreaMistakesNote(masteredCount, allMistakes.size))
                    }
                    if (totalFocusMin < 60) {
                        weakAreas.add(strings.weakAreaFocusNote(totalFocusMin))
                    }
                    if (tasks.isEmpty()) {
                        weakAreas.add(strings.weakAreaNoTasksNote)
                    }

                    if (weakAreas.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(strings.weakAreasTitle, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                weakAreas.forEach { area ->
                                    Text(area, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(strings.goodStatusTitle, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(strings.goodStatusNote, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // ===== 最近积分变动 =====
                    Text(strings.recentChangesTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val recentRecords = records.take(10)
                    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                    recentRecords.forEach { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                val typeEmoji = when(record.type) {
                                    PointRecordType.TASK_COMPLETE -> "✅"
                                    PointRecordType.BONUS -> "🎁"
                                    PointRecordType.ADJUSTMENT -> "🔧"
                                    PointRecordType.REWARD_EXCHANGE -> "🛒"
                                    PointRecordType.REVERSAL -> "↩️"
                                    // P2-01：PENALTY 穷举补全（emoji，不受 i18n 约束）
                                    PointRecordType.PENALTY -> "⚠️"
                                }
                                Text(typeEmoji, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    // D-08/D-12：心愿/bonus 业务键 → 派生文案；其余 note 原样；空 note → 兜底标签
                                    Text(wishNoteDisplayText(record, wishNamesById)
                                        ?: bonusNoteDisplayText(record)
                                        ?: record.note.ifEmpty { strings.changeFallbackLabel },
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(dateFormat.format(Date(record.date)), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${if (record.points >= 0) "+" else ""}${record.points}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    color = if (record.points >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DashStatCard(label: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 本周 vs 上周对比行：显示本周值与较上周的增减（↑绿/↓红/→持平） */
@Composable
private fun ParentCompareRow(label: String, value: String, delta: Int, unit: String) {
    val strings = LocalStrings.current.parentDashboard
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val (arrow, color) = when {
            delta > 0 -> "↑$delta$unit" to MaterialTheme.colorScheme.secondary
            delta < 0 -> "↓${-delta}$unit" to MaterialTheme.colorScheme.error
            else -> strings.deltaFlatLabel to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(arrow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CategoryBar(label: String, count: Int, total: Int, color: Color) {
    val ratio = if (total > 0) count.toFloat() / total else 0f
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.weight(1f).height(8.dp).clip(MaterialTheme.shapes.small),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${count}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
    }
}

package com.hstudy.app.ui.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.ResponsiveTwoColumn
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ParentDashboardScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val totalFocusMin by app.focusRepository.getTotalFocusMinutes().collectAsState(initial = 0)

    // 孩子今日学习表现评分所需数据
    val todayStr = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val todayFocusMin by app.focusRepository.getTotalFocusMinutesForDate(todayStr).collectAsState(initial = 0)
    val todayStartMillis = remember(todayStr) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayReviewCount by app.mistakeRepository.getReviewCountSince(todayStartMillis).collectAsState(initial = 0)
    val todayCompletedIds by app.pointRepository.getCompletedTaskIdsForDate(todayStr).collectAsState(initial = emptyList())

    // 本周 vs 上周对比所需：最近14天专注与复习数据
    val last14StartMillis = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }.timeInMillis }
    val focusData14 by app.focusRepository.getDailyFocusMinutesSince(last14StartMillis).collectAsState(initial = emptyList())
    val reviews14 by app.mistakeRepository.getReviewsSince(last14StartMillis).collectAsState(initial = emptyList())

    // 本周数据
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val weekStart = cal.timeInMillis
    cal.add(Calendar.DAY_OF_WEEK, 7)
    val weekEnd = cal.timeInMillis
    val weeklyRecords = records.filter { it.date in weekStart until weekEnd }
    val weeklyEarned = weeklyRecords.filter { it.points > 0 }.sumOf { it.points }
    val weeklySpent = weeklyRecords.filter { it.points < 0 }.sumOf { -it.points }

    // 最近7天每日积分获得
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val last7Days = (6 downTo 0).map { daysAgo ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        dayFormat.format(c.time)
    }
    val dailyEarned = last7Days.map { dateStr ->
        records.filter { r ->
            val rDate = dayFormat.format(Date(r.date))
            rDate == dateStr && r.points > 0
        }.sumOf { it.points }
    }

    // 分类统计
    val basicTasks = tasks.count { it.category == TaskCategory.BASIC }
    val enhanceTasks = tasks.count { it.category == TaskCategory.ENHANCEMENT }
    val habitTasks = tasks.count { it.category == TaskCategory.HABIT }

    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        topBar = { HStudyTopBar(title = "📊 数据看板", onNavigateBack = onNavigateBack) }
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
                            Text("当前总积分", style = MaterialTheme.typography.titleMedium)
                            Text("${totalPoints}", style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // ===== 统计网格 =====
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashStatCard("本周获得", "+${weeklyEarned}", "⭐", Modifier.weight(1f))
                        DashStatCard("本周消耗", "-${weeklySpent}", "💸", Modifier.weight(1f))
                        DashStatCard("累计专注", "${totalFocusMin}m", "🍅", Modifier.weight(1f))
                        DashStatCard("错题掌握", "${masteredCount}/${allMistakes.size}", "📚", Modifier.weight(1f))
                    }

                    // ===== 孩子今日学习表现（与家长同步的学习质量反馈）=====
                    val todayCheckinRate = if (tasks.isNotEmpty()) todayCompletedIds.size.toFloat() / tasks.size else 0f
                    val childScore = remember(todayCompletedIds.size, tasks.size, todayFocusMin, todayReviewCount) {
                        com.hstudy.app.ui.common.LearningScoreCalculator.calculate(todayCheckinRate, todayFocusMin, todayReviewCount)
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
                                    Text("孩子今日学习表现", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${childScore.grade} · 打卡${todayCompletedIds.size}/${tasks.size} · 专注${todayFocusMin}分 · 复习${todayReviewCount}道",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Text("${childScore.total}分", style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("💡 ${childScore.encouragement}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    // ===== 孩子本周 vs 上周学习对比（让家长看见孩子的进步趋势）=====
                    val wdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val wLast7 = (6 downTo 0).map { d -> wdf.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }.time) }
                    val wPrev7 = (13 downTo 7).map { d -> wdf.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }.time) }
                    val wFocusByDate = focusData14.associate { it.date to it.totalMinutes }
                    val wReviewByDate = reviews14.groupBy { wdf.format(Date(it.reviewedAt)) }.mapValues { it.value.size }
                    fun wPoints(dates: List<String>) = records.filter { it.points > 0 && wdf.format(Date(it.date)) in dates }.sumOf { it.points }
                    val wThisFocus = wLast7.sumOf { wFocusByDate[it] ?: 0 }; val wLastFocus = wPrev7.sumOf { wFocusByDate[it] ?: 0 }
                    val wThisRev = wLast7.sumOf { wReviewByDate[it] ?: 0 }; val wLastRev = wPrev7.sumOf { wReviewByDate[it] ?: 0 }
                    val wThisPts = wPoints(wLast7); val wLastPts = wPoints(wPrev7)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val wVerdict = when {
                                wLastFocus + wLastRev + wLastPts == 0 -> "🌱 新的开始，陪孩子一起加油！"
                                wThisFocus > wLastFocus || wThisPts > wLastPts -> "📈 孩子本周比上周进步了！"
                                wThisFocus == wLastFocus && wThisPts == wLastPts -> "➡️ 孩子本周和上周持平，稳定发挥。"
                                else -> "💪 孩子本周略有下降，多鼓励加把劲！"
                            }
                            Text("📅 孩子本周 vs 上周", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(wVerdict, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            ParentCompareRow("🍅 专注时长", "${wThisFocus}分", wThisFocus - wLastFocus, "分")
                            ParentCompareRow("📚 复习错题", "${wThisRev}道", wThisRev - wLastRev, "道")
                            ParentCompareRow("⭐ 获得积分", "$wThisPts", wThisPts - wLastPts, "")
                        }
                    }

                    // ===== 横屏双栏：左图表 + 右分析 =====
                    ResponsiveTwoColumn(
                        windowSizeClass = windowSizeClass,
                        leftWeight = 0.55f,
                        left = {
                            // 7日积分趋势折线图
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("📈 7日积分获取趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (dailyEarned.all { it == 0 }) {
                                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        val maxVal = dailyEarned.max().toFloat().coerceAtLeast(1f)
                                        val lineColor = MaterialTheme.colorScheme.primary
                                        val dotColor = MaterialTheme.colorScheme.tertiary
                                        val gridColor = MaterialTheme.colorScheme.surfaceVariant
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
                                    Text("📋 任务分类分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CategoryBar("📝 基础", basicTasks, tasks.size, MaterialTheme.colorScheme.primary)
                                    CategoryBar("📈 提升", enhanceTasks, tasks.size, MaterialTheme.colorScheme.tertiary)
                                    CategoryBar("🏆 习惯", habitTasks, tasks.size, MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    )

                    // ===== 薄弱环节提示 =====
                    val weakAreas = mutableListOf<String>()
                    if (allMistakes.isNotEmpty() && masteredCount.toFloat() / allMistakes.size < 0.3f) {
                        weakAreas.add("📚 错题掌握率较低 (${masteredCount}/${allMistakes.size})，建议加强复习")
                    }
                    if (totalFocusMin < 60) {
                        weakAreas.add("🍅 专注时长不足 (${totalFocusMin}分钟)，建议使用番茄钟")
                    }
                    if (tasks.isEmpty()) {
                        weakAreas.add("📝 暂无学习任务，请使用积分模板或手动添加")
                    }

                    if (weakAreas.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("⚠️ 薄弱项提示", style = MaterialTheme.typography.titleMedium,
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
                                Text("✅ 学习状态良好", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("继续保持当前的学习节奏！", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // ===== 最近积分变动 =====
                    Text("最近积分变动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                }
                                Text(typeEmoji, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(record.note.ifEmpty { "积分变动" }, style = MaterialTheme.typography.bodyMedium)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val (arrow, color) = when {
            delta > 0 -> "↑$delta$unit" to Color(0xFF4CAF50)
            delta < 0 -> "↓${-delta}$unit" to MaterialTheme.colorScheme.error
            else -> "→ 持平" to MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.weight(1f).height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("${count}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
    }
}

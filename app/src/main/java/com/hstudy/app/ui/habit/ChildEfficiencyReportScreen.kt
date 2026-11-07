package com.hstudy.app.ui.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
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
import com.hstudy.app.ui.common.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChildEfficiencyReportScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val totalFocusMin by app.focusRepository.getTotalFocusMinutes().collectAsState(initial = 0)
    // 错题数据（用于学科掌握度分析与学习建议）
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    // 专注会话（用于专注效率洞察：找出黄金专注时段）
    val allSessions by app.focusRepository.getAllSessions().collectAsState(initial = emptyList())

    // 最近14天专注数据
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -14) }
    val focusData by app.focusRepository.getDailyFocusMinutesSince(cal.timeInMillis)
        .collectAsState(initial = emptyList())

    // 最近14天打卡数据
    val completions by app.pointRepository.getCompletionsBetween(
        cal.timeInMillis, System.currentTimeMillis()
    ).collectAsState(initial = emptyList())

    // 按日期统计打卡数
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val completionByDate = completions.groupBy { dateFormat.format(Date(it.completedAt)) }
        .mapValues { it.value.size }

    // 最近14天复习记录（用于按日统计复习量）
    val reviewsSince by app.mistakeRepository.getReviewsSince(cal.timeInMillis).collectAsState(initial = emptyList())

    // 月度对比数据（本月 vs 上月，提供更长期的进步视野）
    val thisMonthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val lastMonthStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val monthCompletions by app.pointRepository.getCompletionsBetween(lastMonthStart, System.currentTimeMillis())
        .collectAsState(initial = emptyList())
    val monthReviews by app.mistakeRepository.getReviewsSince(lastMonthStart).collectAsState(initial = emptyList())
    val thisMonthFocus = remember(allSessions, thisMonthStart) { allSessions.filter { it.completedAt >= thisMonthStart }.sumOf { it.durationMinutes } }
    val lastMonthFocus = remember(allSessions, lastMonthStart, thisMonthStart) { allSessions.filter { it.completedAt in lastMonthStart until thisMonthStart }.sumOf { it.durationMinutes } }
    val thisMonthComp = remember(monthCompletions, thisMonthStart) { monthCompletions.count { it.completedAt >= thisMonthStart } }
    val lastMonthComp = remember(monthCompletions, lastMonthStart, thisMonthStart) { monthCompletions.count { it.completedAt in lastMonthStart until thisMonthStart } }
    val thisMonthRev = remember(monthReviews, thisMonthStart) { monthReviews.count { it.reviewedAt >= thisMonthStart } }
    val lastMonthRev = remember(monthReviews, lastMonthStart, thisMonthStart) { monthReviews.count { it.reviewedAt in lastMonthStart until thisMonthStart } }
    val reviewByDate = reviewsSince.groupBy { dateFormat.format(Date(it.reviewedAt)) }
        .mapValues { it.value.size }
    val focusByDate = focusData.associate { it.date to it.totalMinutes }

    // 近7天每日学习表现评分趋势
    val last7Dates = (6 downTo 0).map { daysAgo ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        dateFormat.format(c.time)
    }
    val scoreTrend = last7Dates.map { dateStr ->
        val rate = if (tasks.isNotEmpty()) (completionByDate[dateStr] ?: 0).toFloat() / tasks.size else 0f
        com.hstudy.app.ui.common.LearningScoreCalculator.calculate(
            rate, focusByDate[dateStr] ?: 0, reviewByDate[dateStr] ?: 0
        ).total
    }

    // 本周 vs 上周对比（周级别反思，看见持续进步）
    val prev7Dates = (13 downTo 7).map { daysAgo ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        dateFormat.format(c.time)
    }
    fun weekFocus(dates: List<String>) = dates.sumOf { focusByDate[it] ?: 0 }
    fun weekComp(dates: List<String>) = dates.sumOf { completionByDate[it] ?: 0 }
    fun weekRev(dates: List<String>) = dates.sumOf { reviewByDate[it] ?: 0 }
    fun weekAvgScore(dates: List<String>): Int {
        val scores = dates.map { d ->
            val rate = if (tasks.isNotEmpty()) (completionByDate[d] ?: 0).toFloat() / tasks.size else 0f
            com.hstudy.app.ui.common.LearningScoreCalculator.calculate(rate, focusByDate[d] ?: 0, reviewByDate[d] ?: 0).total
        }
        return if (scores.isEmpty()) 0 else scores.sum() / scores.size
    }
    val thisFocus = weekFocus(last7Dates); val lastFocus = weekFocus(prev7Dates)
    val thisComp = weekComp(last7Dates); val lastComp = weekComp(prev7Dates)
    val thisRev = weekRev(last7Dates); val lastRev = weekRev(prev7Dates)
    val thisAvgScore = weekAvgScore(last7Dates); val lastAvgScore = weekAvgScore(prev7Dates)

    // 专注效率分析：按小时统计专注分钟数，找出黄金专注时段
    val hourFormat = SimpleDateFormat("H", Locale.getDefault())
    val focusByHour = allSessions.groupBy { hourFormat.format(Date(it.completedAt)).toIntOrNull() ?: 0 }
        .mapValues { entry -> entry.value.sumOf { it.durationMinutes } }
    val bestHour = focusByHour.maxByOrNull { it.value }?.key
    val bestPeriodLabel = bestHour?.let { hourLabel(it) } ?: ""
    val avgSession = if (allSessions.isNotEmpty()) allSessions.sumOf { it.durationMinutes } / allSessions.size else 0

    Scaffold(
        topBar = { HStudyTopBar(title = "📊 学习效率报表", onNavigateBack = onNavigateBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 概览
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ReportStat("⭐", "${totalPoints}", "累计积分")
                    ReportStat("📚", "${totalFocusMin}m", "累计专注")
                    ReportStat("📝", "${completions.size}", "打卡次数")
                }
            }

            // ===== 本周 vs 上周对比（周级别反思，看见持续进步）=====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📅 本周 vs 上周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val verdict = when {
                        lastFocus + lastComp + lastRev == 0 -> "🌱 新的开始，继续加油！"
                        thisAvgScore > lastAvgScore || thisFocus > lastFocus -> "📈 比上周进步了，继续保持！"
                        thisAvgScore == lastAvgScore && thisFocus == lastFocus -> "➡️ 和上周持平，稳定发挥！"
                        else -> "💪 比上周略有下降，加把劲追回来！"
                    }
                    Text(verdict, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    WeekCompareRow("🍅 专注时长", "${thisFocus}分", thisFocus - lastFocus, "分")
                    WeekCompareRow("✏️ 打卡次数", "${thisComp}次", thisComp - lastComp, "次")
                    WeekCompareRow("📚 复习错题", "${thisRev}道", thisRev - lastRev, "道")
                    WeekCompareRow("⭐ 平均评分", "${thisAvgScore}分", thisAvgScore - lastAvgScore, "分")
                }
            }

            // ===== 本月 vs 上月对比（月度进步视野，看见长期成长）=====
            if (lastMonthFocus + lastMonthComp + lastMonthRev > 0 || thisMonthFocus + thisMonthComp + thisMonthRev > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🗓️ 本月 vs 上月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val mVerdict = when {
                            lastMonthFocus + lastMonthComp + lastMonthRev == 0 -> "🌱 本月是新的开始，继续加油！"
                            thisMonthFocus > lastMonthFocus || thisMonthComp > lastMonthComp -> "📈 本月比上月进步了，太棒了！"
                            thisMonthFocus == lastMonthFocus && thisMonthComp == lastMonthComp -> "➡️ 本月和上月持平，稳定发挥！"
                            else -> "💪 本月比上月略有下降，下个月加把劲！"
                        }
                        Text(mVerdict, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        WeekCompareRow("🍅 专注时长", "${thisMonthFocus}分", thisMonthFocus - lastMonthFocus, "分")
                        WeekCompareRow("✏️ 打卡次数", "${thisMonthComp}次", thisMonthComp - lastMonthComp, "次")
                        WeekCompareRow("📚 复习错题", "${thisMonthRev}道", thisMonthRev - lastMonthRev, "道")
                    }
                }
            }

            // ===== 学科掌握度分析（找出薄弱科目，明确复习重点）=====
            if (allMistakes.isNotEmpty()) {
                val subjectStats = com.hstudy.app.data.local.entity.MistakeSubject.entries.map { subj ->
                    val list = allMistakes.filter { it.subject == subj }
                    val mastered = list.count { it.isMastered }
                    Triple(subj, list.size, mastered)
                }.filter { it.second > 0 }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📊 学科掌握度分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("根据错题掌握情况，找出需要重点突破的科目", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        subjectStats.forEach { (subj, total, mastered) ->
                            val rate = mastered.toFloat() / total
                            val subjName = when (subj) {
                                com.hstudy.app.data.local.entity.MistakeSubject.MATH -> "🔢 数学"
                                com.hstudy.app.data.local.entity.MistakeSubject.CHINESE -> "📝 语文"
                                com.hstudy.app.data.local.entity.MistakeSubject.ENGLISH -> "🔤 英语"
                                com.hstudy.app.data.local.entity.MistakeSubject.SCIENCE -> "🔬 科学"
                                com.hstudy.app.data.local.entity.MistakeSubject.OTHER -> "📋 其他"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(subjName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                                LinearProgressIndicator(
                                    progress = rate,
                                    modifier = Modifier.weight(1f).height(10.dp),
                                    color = if (rate < 0.5f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$mastered/$total", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }

            // ===== 错因分析（找出错误类型分布，针对性改进学习方法）=====
            val typedMistakes = allMistakes.filter { it.errorType.isNotEmpty() }
            if (typedMistakes.isNotEmpty()) {
                val errorTypeStats = typedMistakes.groupBy { it.errorType }
                    .mapValues { it.value.size }
                    .toList().sortedByDescending { it.second }
                val topError = errorTypeStats.first()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔍 错因分析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("分析错误类型分布，找到根本原因针对性改进", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        errorTypeStats.forEach { (type, count) ->
                            val rate = count.toFloat() / typedMistakes.size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(type, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
                                LinearProgressIndicator(progress = rate, modifier = Modifier.weight(1f).height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                            }
                        }
                        val errorAdvice = when (topError.first) {
                            "粗心马虎" -> "💡 你最常因「粗心马虎」出错！建议：做完后回头检查一遍，圈出关键词，放慢审题速度。"
                            "概念不清" -> "💡 你最常因「概念不清」出错！建议：回归课本把基础概念弄懂，多问为什么，别急着刷题。"
                            "方法不会" -> "💡 你最常因「方法不会」出错！建议：总结同类题的解题套路，看例题学习标准方法。"
                            "审题错误" -> "💡 你最常因「审题错误」出错！建议：读题时圈出条件和问题，看清要求再动笔。"
                            "计算错误" -> "💡 你最常因「计算错误」出错！建议：草稿纸写工整，关键步骤验算一遍。"
                            else -> "💡 针对常见错误类型，制定专门的改进计划吧！"
                        }
                        Text(errorAdvice, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ===== 个性化学习方法建议（基于数据的可执行建议）=====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💡 学习方法建议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val suggestions = buildLearningSuggestions(allMistakes, totalFocusMin, completions.size, tasks.size, bestPeriodLabel)
                    suggestions.forEach { s ->
                        Text("• $s", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // ===== 学习表现7日趋势（看见自己的进步轨迹）=====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📈 学习表现7日趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (scoreTrend.all { it == 0 }) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("暂无数据，开始学习后这里会显示你的进步轨迹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val maxScore = 100f
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val chartW = size.width - 20f
                            val chartH = size.height - 24f
                            val barW = chartW / scoreTrend.size * 0.6f
                            val gap = chartW / scoreTrend.size
                            scoreTrend.forEachIndexed { index, score ->
                                val barH = score / maxScore * chartH
                                val x = 10f + index * gap + (gap - barW) / 2
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x, chartH - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(last7Dates.first().takeLast(5), style = MaterialTheme.typography.labelSmall)
                            val avgScore = scoreTrend.sum() / scoreTrend.size
                            Text("7日平均 $avgScore 分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(last7Dates.last().takeLast(5), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ===== 专注效率洞察（找出黄金专注时段，把难题安排在最佳时间）=====
            if (allSessions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⏰ 专注效率洞察", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ReportStat("🌟", bestPeriodLabel.ifEmpty { "-" }, "黄金专注时段")
                            ReportStat("⏱️", "${avgSession}m", "平均每次专注")
                            ReportStat("🔢", "${allSessions.size}", "专注次数")
                        }
                        if (bestPeriodLabel.isNotEmpty()) {
                            Text("💡 你在 $bestPeriodLabel 专注力最好，建议把难题和重要学习安排在这个时段。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ===== 专注投入分布（看清专注时间花在哪，合理分配各科精力）=====
            if (allSessions.isNotEmpty()) {
                val focusByTarget = allSessions.groupBy { it.taskTitle.ifEmpty { "自由学习" } }
                    .mapValues { it.value.sumOf { s -> s.durationMinutes } }
                    .entries.sortedByDescending { it.value }
                val totalFocus = focusByTarget.sumOf { it.value }.coerceAtLeast(1)
                val topTarget = focusByTarget.firstOrNull()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📊 专注投入分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("按专注目标统计时间投入，看看精力都花在了哪里", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        focusByTarget.take(6).forEach { (target, minutes) ->
                            val ratio = minutes.toFloat() / totalFocus
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(target, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp), maxLines = 1)
                                LinearProgressIndicator(
                                    progress = ratio,
                                    modifier = Modifier.weight(1f).height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${minutes}m", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                            }
                        }
                        if (topTarget != null) {
                            Text("💡 你在「${topTarget.key}」上投入最多（${topTarget.value}分钟），注意各科均衡分配哦。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 专注趋势折线图
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("📈 专注时长趋势 (14天)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (focusData.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val maxVal = focusData.maxOfOrNull { it.totalMinutes }?.toFloat()?.coerceAtLeast(1f) ?: 1f
                        val lineColor = MaterialTheme.colorScheme.primary
                        val dotColor = MaterialTheme.colorScheme.tertiary
                        val gridColor = MaterialTheme.colorScheme.surfaceVariant

                        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            val chartWidth = size.width - 40f
                            val chartHeight = size.height - 40f
                            val startX = 20f
                            val stepX = if (focusData.size > 1) chartWidth / (focusData.size - 1) else chartWidth

                            // 网格线
                            for (i in 0..4) {
                                val y = chartHeight * i / 4f
                                drawLine(color = gridColor, start = Offset(startX, y), end = Offset(startX + chartWidth, y), strokeWidth = 1f)
                            }

                            // 折线
                            val path = Path()
                            focusData.forEachIndexed { index, data ->
                                val x = startX + index * stepX
                                val y = chartHeight - (data.totalMinutes / maxVal * chartHeight)
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                            // 数据点
                            focusData.forEachIndexed { index, data ->
                                val x = startX + index * stepX
                                val y = chartHeight - (data.totalMinutes / maxVal * chartHeight)
                                drawCircle(color = dotColor, radius = 5f, center = Offset(x, y))
                            }
                        }

                        // X轴日期
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            focusData.firstOrNull()?.let { Text(it.date.takeLast(5), style = MaterialTheme.typography.labelSmall) }
                            focusData.lastOrNull()?.let { Text(it.date.takeLast(5), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            // 打卡完成率趋势
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("✅ 打卡完成率趋势 (14天)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    val totalTasks = tasks.size
                    if (completionByDate.isEmpty() || totalTasks == 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val dates = completionByDate.keys.sorted()
                        val rates = dates.map { (completionByDate[it] ?: 0).toFloat() / totalTasks * 100f }
                        val maxRate = 100f
                        val lineColor = MaterialTheme.colorScheme.secondary
                        val gridColor = MaterialTheme.colorScheme.surfaceVariant

                        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            val chartWidth = size.width - 40f
                            val chartHeight = size.height - 40f
                            val startX = 20f
                            val stepX = if (dates.size > 1) chartWidth / (dates.size - 1) else chartWidth

                            for (i in 0..4) {
                                val y = chartHeight * i / 4f
                                drawLine(color = gridColor, start = Offset(startX, y), end = Offset(startX + chartWidth, y), strokeWidth = 1f)
                            }

                            val path = Path()
                            rates.forEachIndexed { index, rate ->
                                val x = startX + index * stepX
                                val y = chartHeight - (rate / maxRate * chartHeight)
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

                            rates.forEachIndexed { index, rate ->
                                val x = startX + index * stepX
                                val y = chartHeight - (rate / maxRate * chartHeight)
                                drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0%", style = MaterialTheme.typography.labelSmall)
                            Text("100%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** 本周 vs 上周对比行：显示本周值与较上周的增减（↑绿/↓红/→持平） */
@Composable
private fun WeekCompareRow(label: String, value: String, delta: Int, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val (arrow, color) = when {
            delta > 0 -> "↑$delta$unit" to Color(0xFF4CAF50)
            delta < 0 -> "↓${-delta}$unit" to MaterialTheme.colorScheme.error
            else -> "→ 持平" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(arrow, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 将小时（0-23）转为友好的时段描述
 */
private fun hourLabel(hour: Int): String {
    val period = when (hour) {
        in 5..8 -> "早上"
        in 9..11 -> "上午"
        in 12..13 -> "中午"
        in 14..17 -> "下午"
        in 18..22 -> "晚上"
        else -> "深夜"
    }
    return "$period${hour}点"
}

/**
 * 根据学习数据生成个性化学习方法建议（可执行、有针对性）
 */
private fun buildLearningSuggestions(
    mistakes: List<com.hstudy.app.data.local.entity.MistakeEntity>,
    totalFocusMin: Int,
    checkinCount: Int,
    taskCount: Int,
    bestPeriodLabel: String = ""
): List<String> {
    val suggestions = mutableListOf<String>()

    // 1. 薄弱科目建议
    val subjectStats = com.hstudy.app.data.local.entity.MistakeSubject.entries.map { subj ->
        val list = mistakes.filter { it.subject == subj }
        val mastered = list.count { it.isMastered }
        val rate = if (list.isNotEmpty()) mastered.toFloat() / list.size else 1f
        Triple(subj, list.size, rate)
    }.filter { it.second >= 2 } // 至少2道错题才统计
    val weakest = subjectStats.minByOrNull { it.third }
    if (weakest != null && weakest.third < 0.6f) {
        val name = when (weakest.first) {
            com.hstudy.app.data.local.entity.MistakeSubject.MATH -> "数学"
            com.hstudy.app.data.local.entity.MistakeSubject.CHINESE -> "语文"
            com.hstudy.app.data.local.entity.MistakeSubject.ENGLISH -> "英语"
            com.hstudy.app.data.local.entity.MistakeSubject.SCIENCE -> "科学"
            com.hstudy.app.data.local.entity.MistakeSubject.OTHER -> "其他"
        }
        suggestions.add("${name}是你的薄弱科目（掌握率${(weakest.third * 100).toInt()}%），建议多花时间复习${name}错题。")
    }

    // 2. 间隔复习建议
    val dueCount = mistakes.count { com.hstudy.app.ui.mistake.SpacedRepetition.isDueForReview(it) }
    if (dueCount > 0) {
        suggestions.add("有 $dueCount 道错题到了复习时间，按遗忘曲线及时复习，记忆保持率可提升一倍以上。")
    }

    // 3. 未掌握错题建议
    val unmastered = mistakes.count { !it.isMastered }
    if (unmastered > 5) {
        suggestions.add("还有 $unmastered 道错题未掌握，建议每天攻克 2-3 道，逐步清零。")
    }

    // 4. 专注建议
    if (totalFocusMin < 60) {
        suggestions.add("试试用番茄钟专注学习（25分钟专注+5分钟休息），专注效率会更高。")
    } else {
        suggestions.add("累计专注 ${totalFocusMin} 分钟，保持专注是高效学习的关键，继续加油！")
    }

    // 5. 黄金专注时段建议
    if (bestPeriodLabel.isNotEmpty()) {
        suggestions.add("你在 $bestPeriodLabel 专注力最好，把难题和重要科目安排在这个时段，效率更高。")
    }

    // 6. 正面鼓励
    if (mistakes.isNotEmpty() && mistakes.count { it.isMastered } > mistakes.size / 2) {
        suggestions.add("大部分错题都已掌握，你的复习方法很有效，继续保持！")
    }

    if (suggestions.isEmpty()) {
        suggestions.add("开始记录错题并坚持复习，这里会为你生成个性化学习建议。")
    }
    return suggestions
}

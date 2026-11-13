package com.hstudy.app.ui.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
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
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.EfficiencyReportStrings
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.*
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildEfficiencyReportScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    // 生效语言：学习表现评分（W8 T2）与本页文案/学科与错因的注册表取词（W8 T3）都随它
    val displayLanguage = LocalDisplayLanguage.current
    val strings = LocalStrings.current.efficiencyReport
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
        cal.timeInMillis, com.hstudy.app.ui.dt.nowMillis()
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
    val monthCompletions by app.pointRepository.getCompletionsBetween(lastMonthStart, com.hstudy.app.ui.dt.nowMillis())
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
            rate, focusByDate[dateStr] ?: 0, reviewByDate[dateStr] ?: 0, displayLanguage
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
            com.hstudy.app.ui.common.LearningScoreCalculator.calculate(rate, focusByDate[d] ?: 0, reviewByDate[d] ?: 0, displayLanguage).total
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
    val bestPeriodLabel = bestHour?.let { hourLabel(it, strings) } ?: ""
    val avgSession = if (allSessions.isNotEmpty()) allSessions.sumOf { it.durationMinutes } / allSessions.size else 0

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) }
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
                    ReportStat("⭐", "${totalPoints}", strings.statPointsLabel)
                    ReportStat("📚", statMinutesText(totalFocusMin, strings, displayLanguage), strings.statFocusLabel)
                    ReportStat("📝", "${completions.size}", strings.statCheckinLabel)
                }
            }

            // ===== 本周 vs 上周对比（周级别反思，看见持续进步）=====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.weekTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val verdict = when {
                        lastFocus + lastComp + lastRev == 0 -> strings.weekVerdictNew
                        thisAvgScore > lastAvgScore || thisFocus > lastFocus -> strings.weekVerdictUp
                        thisAvgScore == lastAvgScore && thisFocus == lastFocus -> strings.weekVerdictFlat
                        else -> strings.weekVerdictDown
                    }
                    Text(verdict, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    WeekCompareRow(strings.focusMetricLabel, strings.focusValue(thisFocus), thisFocus - lastFocus, strings.focusUnit)
                    WeekCompareRow(strings.checkinMetricLabel, strings.checkinValue(thisComp), thisComp - lastComp, strings.checkinUnit)
                    WeekCompareRow(strings.reviewMetricLabel, strings.reviewValue(thisRev), thisRev - lastRev, strings.reviewUnit)
                    WeekCompareRow(strings.avgScoreLabel, strings.scoreValue(thisAvgScore), thisAvgScore - lastAvgScore, strings.scoreUnit)
                }
            }

            // ===== 本月 vs 上月对比（月度进步视野，看见长期成长）=====
            if (lastMonthFocus + lastMonthComp + lastMonthRev > 0 || thisMonthFocus + thisMonthComp + thisMonthRev > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.monthTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val mVerdict = when {
                            lastMonthFocus + lastMonthComp + lastMonthRev == 0 -> strings.monthVerdictNew
                            thisMonthFocus > lastMonthFocus || thisMonthComp > lastMonthComp -> strings.monthVerdictUp
                            thisMonthFocus == lastMonthFocus && thisMonthComp == lastMonthComp -> strings.monthVerdictFlat
                            else -> strings.monthVerdictDown
                        }
                        Text(mVerdict, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        WeekCompareRow(strings.focusMetricLabel, strings.focusValue(thisMonthFocus), thisMonthFocus - lastMonthFocus, strings.focusUnit)
                        WeekCompareRow(strings.checkinMetricLabel, strings.checkinValue(thisMonthComp), thisMonthComp - lastMonthComp, strings.checkinUnit)
                        WeekCompareRow(strings.reviewMetricLabel, strings.reviewValue(thisMonthRev), thisMonthRev - lastMonthRev, strings.reviewUnit)
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
                        Text(strings.subjectTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.subjectSubtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        subjectStats.forEach { (subj, total, mastered) ->
                            val rate = mastered.toFloat() / total
                            // 科目名是 canonical 存储值：emoji 留在页面，文字走注册表取词
                            val subjName = subjectLabel(subj, displayLanguage)
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
                        Text(strings.errorTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.errorSubtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        errorTypeStats.forEach { (type, count) ->
                            val rate = count.toFloat() / typedMistakes.size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 错因名是 canonical 存储值（与注册表错题类型同值），渲染时才取词
                                Text(localizedPresetText(type, displayLanguage), style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(80.dp))
                                LinearProgressIndicator(progress = rate, modifier = Modifier.weight(1f).height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                            }
                        }
                        // 分支键仍是 canonical 存储值；文案整句随语言（错因名已内嵌在各语种句子里）
                        val errorAdvice = when (topError.first) {
                            "粗心马虎" -> strings.errorAdviceCareless
                            "概念不清" -> strings.errorAdviceConcepts
                            "方法不会" -> strings.errorAdviceMethod
                            "审题错误" -> strings.errorAdviceReading
                            "计算错误" -> strings.errorAdviceCalculation
                            else -> strings.errorAdviceFallback
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
                    Text(strings.suggestionsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val suggestions = buildLearningSuggestions(
                        allMistakes, totalFocusMin, completions.size, tasks.size, bestPeriodLabel, strings, displayLanguage
                    )
                    suggestions.forEach { s ->
                        Text("• $s", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // ===== 学习表现7日趋势（看见自己的进步轨迹）=====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.scoreTrendTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (scoreTrend.all { it == 0 }) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(strings.scoreTrendEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(strings.scoreTrendAverage(avgScore), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(last7Dates.last().takeLast(5), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ===== 专注效率洞察（找出黄金专注时段，把难题安排在最佳时间）=====
            if (allSessions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.focusInsightTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ReportStat("🌟", bestPeriodLabel.ifEmpty { "-" }, strings.bestPeriodLabel)
                            ReportStat("⏱️", statMinutesText(avgSession, strings, displayLanguage), strings.avgSessionLabel)
                            ReportStat("🔢", "${allSessions.size}", strings.focusCountLabel)
                        }
                        if (bestPeriodLabel.isNotEmpty()) {
                            Text(strings.bestPeriodHint(bestPeriodLabel),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ===== 专注投入分布（看清专注时间花在哪，合理分配各科精力）=====
            if (allSessions.isNotEmpty()) {
                val focusByTarget = allSessions.groupBy { it.taskTitle.ifEmpty { strings.freeStudy } }
                    .mapValues { it.value.sumOf { s -> s.durationMinutes } }
                    .entries.sortedByDescending { it.value }
                val totalFocus = focusByTarget.sumOf { it.value }.coerceAtLeast(1)
                val topTarget = focusByTarget.firstOrNull()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.distributionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.distributionSubtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        focusByTarget.take(6).forEach { (target, minutes) ->
                            val ratio = minutes.toFloat() / totalFocus
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(localizedPresetText(target, displayLanguage), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp), maxLines = 1)
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
                            Text(strings.distributionTopHint(localizedPresetText(topTarget.key, displayLanguage), topTarget.value),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 专注趋势折线图
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(strings.focusTrendTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (focusData.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text(strings.noData, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(strings.checkinTrendTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    val totalTasks = tasks.size
                    if (completionByDate.isEmpty() || totalTasks == 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text(strings.noData, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * 统计卡的分钟值（W8 终审 #9）：中文态逐字节保留迁移前的 "150m"（v3.1.0 渲染不变，不许动）；
 * 英文态借 [EfficiencyReportStrings.focusValue] 的 "150 min" 词形，不另写单位字面量。
 */
private fun statMinutesText(minutes: Int, strings: EfficiencyReportStrings, lang: DisplayLanguage): String =
    if (lang == DisplayLanguage.EN) strings.focusValue(minutes) else "${minutes}m"

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
    val strings = LocalStrings.current.efficiencyReport
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val (arrow, color) = when {
            delta > 0 -> "↑$delta$unit" to Color(0xFF4CAF50)
            delta < 0 -> "↓${-delta}$unit" to MaterialTheme.colorScheme.error
            else -> strings.deltaFlat to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(arrow, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * 将小时（0-23）转为友好的时段描述（时段名与 "$period${hour}点" 模板都随语言）
 */
private fun hourLabel(hour: Int, strings: EfficiencyReportStrings): String {
    val period = when (hour) {
        in 5..8 -> strings.periodEarlyMorning
        in 9..11 -> strings.periodMorning
        in 12..13 -> strings.periodNoon
        in 14..17 -> strings.periodAfternoon
        in 18..22 -> strings.periodEvening
        else -> strings.periodLateNight
    }
    return strings.hourLabel(period, hour)
}

/** 学科 emoji 留在页面（Ruling 17 的装饰位例外：它是图标不是文字），科目名走注册表取词 */
private fun subjectEmoji(subject: com.hstudy.app.data.local.entity.MistakeSubject): String = when (subject) {
    com.hstudy.app.data.local.entity.MistakeSubject.MATH -> "🔢"
    com.hstudy.app.data.local.entity.MistakeSubject.CHINESE -> "📝"
    com.hstudy.app.data.local.entity.MistakeSubject.ENGLISH -> "🔤"
    com.hstudy.app.data.local.entity.MistakeSubject.SCIENCE -> "🔬"
    com.hstudy.app.data.local.entity.MistakeSubject.OTHER -> "📋"
}

/** 学科 canonical 存储值（原样保留在页面，渲染时由 [localizedPresetText] 按语言取词） */
private fun subjectCanonical(subject: com.hstudy.app.data.local.entity.MistakeSubject): String = when (subject) {
    com.hstudy.app.data.local.entity.MistakeSubject.MATH -> "数学"
    com.hstudy.app.data.local.entity.MistakeSubject.CHINESE -> "语文"
    com.hstudy.app.data.local.entity.MistakeSubject.ENGLISH -> "英语"
    com.hstudy.app.data.local.entity.MistakeSubject.SCIENCE -> "科学"
    com.hstudy.app.data.local.entity.MistakeSubject.OTHER -> "其他"
}

private fun subjectLabel(
    subject: com.hstudy.app.data.local.entity.MistakeSubject,
    lang: DisplayLanguage
): String = "${subjectEmoji(subject)} ${localizedPresetText(subjectCanonical(subject), lang)}"

/**
 * 根据学习数据生成个性化学习方法建议（可执行、有针对性）
 */
private fun buildLearningSuggestions(
    mistakes: List<com.hstudy.app.data.local.entity.MistakeEntity>,
    totalFocusMin: Int,
    checkinCount: Int,
    taskCount: Int,
    bestPeriodLabel: String = "",
    strings: EfficiencyReportStrings,
    lang: DisplayLanguage
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
        val name = localizedPresetText(subjectCanonical(weakest.first), lang)
        suggestions.add(strings.suggestionWeakSubject(name, (weakest.third * 100).toInt()))
    }

    // 2. 间隔复习建议
    val dueCount = mistakes.count { com.hstudy.app.ui.mistake.SpacedRepetition.isDueForReview(it) }
    if (dueCount > 0) {
        suggestions.add(strings.suggestionDueReview(dueCount))
    }

    // 3. 未掌握错题建议
    val unmastered = mistakes.count { !it.isMastered }
    if (unmastered > 5) {
        suggestions.add(strings.suggestionUnmastered(unmastered))
    }

    // 4. 专注建议
    if (totalFocusMin < 60) {
        suggestions.add(strings.suggestionPomodoro)
    } else {
        suggestions.add(strings.suggestionFocusGood(totalFocusMin))
    }

    // 5. 黄金专注时段建议
    if (bestPeriodLabel.isNotEmpty()) {
        suggestions.add(strings.suggestionBestPeriod(bestPeriodLabel))
    }

    // 6. 正面鼓励
    if (mistakes.isNotEmpty() && mistakes.count { it.isMastered } > mistakes.size / 2) {
        suggestions.add(strings.suggestionMastered)
    }

    if (suggestions.isEmpty()) {
        suggestions.add(strings.suggestionEmpty)
    }
    return suggestions
}

package com.hstudy.app.ui.mistake

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChildMistakeListScreen(
    onNavigateBack: () -> Unit,
    onAddMistake: () -> Unit,
    onViewDetail: (Long) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)
    val reviewReminderEnabled by app.userPreferences.reviewReminderEnabled.collectAsState(initial = false)
    val reviewReminderHour by app.userPreferences.reviewReminderHour.collectAsState(initial = 19)

    // 连续复习天数 streak（强化每日复习习惯，间隔复习效果取决于坚持）
    val allReviews by app.mistakeRepository.getReviewsSince(0L).collectAsState(initial = emptyList())
    val reviewStreak = remember(allReviews) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val reviewDates = allReviews.map { fmt.format(java.util.Date(it.reviewedAt)) }.toSet()
        val cal = java.util.Calendar.getInstance()
        var streak = 0
        // 今天还没复习时，从昨天算起（streak 仍延续）
        if (fmt.format(cal.time) !in reviewDates) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        while (fmt.format(cal.time) in reviewDates) {
            streak++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        streak
    }

    // 连续复习里程碑奖励（巩固每日复习习惯）
    val claimedReviewMilestones by app.userPreferences.claimedReviewStreakMilestones.collectAsState(initial = emptySet())
    val reviewStreakMilestones = remember { listOf(3 to 3, 7 to 8, 14 to 15, 30 to 30) }
    val claimableReviewMilestone = reviewStreakMilestones.firstOrNull { (days, _) -> reviewStreak >= days && days !in claimedReviewMilestones }

    var selectedSubject by remember { mutableStateOf<MistakeSubject?>(null) }
    var showOnlyUnmastered by remember { mutableStateOf(false) }
    var showOnlyDue by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 今日待复习（艾宾浩斯间隔复习到期）
    val dueForReview = remember(allMistakes) { allMistakes.filter { SpacedRepetition.isDueForReview(it) } }

    // 最薄弱科目（掌握率最低且至少有2道错题、未全部掌握，用于快速针对复习）
    val weakestSubject = remember(allMistakes) {
        MistakeSubject.entries.map { subj ->
            val list = allMistakes.filter { it.subject == subj }
            val mastered = list.count { it.isMastered }
            val rate = if (list.isNotEmpty()) mastered.toFloat() / list.size else 1f
            Triple(subj, list.size, rate)
        }.filter { it.second >= 2 && it.third < 1f }.minByOrNull { it.third }?.first
    }

    // 未来7天复习计划预览（提前看见每天有多少错题到期，便于规划复习节奏）
    val reviewForecast = remember(allMistakes) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val unmastered = allMistakes.filter { !it.isMastered }
        (0..6).map { dayOffset ->
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, dayOffset) }
            val dateStr = fmt.format(cal.time)
            val count = unmastered.count { m ->
                val nextDateStr = fmt.format(java.util.Date(SpacedRepetition.nextReviewAt(m)))
                if (dayOffset == 0) nextDateStr <= dateStr else nextDateStr == dateStr
            }
            count
        }
    }

    // 复习打卡日历（过去28天复习记录可视化，激励坚持不断链）
    val reviewCalendar = remember(allReviews) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val reviewDates = allReviews.map { fmt.format(java.util.Date(it.reviewedAt)) }.toSet()
        (27 downTo 0).map { daysAgo ->
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }
            fmt.format(cal.time) in reviewDates
        }
    }

    val filtered = allMistakes.filter { m ->
        (selectedSubject == null || m.subject == selectedSubject) &&
        (!showOnlyUnmastered || !m.isMastered) &&
        (!showOnlyDue || SpacedRepetition.isDueForReview(m)) &&
        (searchQuery.isBlank() ||
            m.title.contains(searchQuery, ignoreCase = true) ||
            m.content.contains(searchQuery, ignoreCase = true) ||
            m.wrongReason.contains(searchQuery, ignoreCase = true) ||
            m.correctMethod.contains(searchQuery, ignoreCase = true))
    }

    // 交互回调（双栏/单栏共用）
    val onWeakSubjectClick: (MistakeSubject) -> Unit = { subj ->
        selectedSubject = subj; showOnlyUnmastered = true; showOnlyDue = false
    }
    val onDueClick: () -> Unit = { showOnlyDue = true }
    val onToggleMastered: (MistakeEntity) -> Unit = { mistake ->
        scope.launch {
            app.mistakeRepository.setMastered(mistake.id, !mistake.isMastered)
            com.hstudy.app.ui.common.AchievementManager.evaluate(app)
        }
    }
    val onReview: (MistakeEntity) -> Unit = { mistake ->
        scope.launch {
            app.mistakeRepository.reviewMistake(mistake.id, points = 0, note = "间隔复习")
            app.pointApprovalRepository.submitApproval("MISTAKE_REVIEW", "复习错题：${mistake.title}", 3)
            app.userPreferences.boostPetMood(2)
            com.hstudy.app.ui.common.AchievementManager.evaluate(app)
            val updated = app.mistakeRepository.getMistakeById(mistake.id)
            val nextLabel = updated?.let { SpacedRepetition.nextReviewLabel(it) } ?: ""
            snackbarHostState.showSnackbar("📖 复习完成！+3分已提交家长审核 $nextLabel")
        }
    }

    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = "📚 错题本",
                onNavigateBack = onNavigateBack,
                actions = {
                    Text("已掌握 ${masteredCount}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp))
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMistake) {
                Icon(Icons.Filled.Add, contentDescription = "添加错题")
            }
        }
    ) { padding ->
        if (isWideScreen) {
            // ===== 宽屏/横屏：双栏布局，左侧状态面板可滚动，右侧筛选+错题列表，保证列表有充足可视空间 =====
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 88.dp)
                ) {
                    StatusCardsPanel(
                        app = app, scope = scope, snackbarHostState = snackbarHostState,
                        allMistakes = allMistakes, masteredCount = masteredCount,
                        reviewStreak = reviewStreak, claimableReviewMilestone = claimableReviewMilestone,
                        reviewCalendar = reviewCalendar, reviewForecast = reviewForecast,
                        weakestSubject = weakestSubject, dueForReview = dueForReview,
                        reviewReminderEnabled = reviewReminderEnabled, reviewReminderHour = reviewReminderHour,
                        onWeakSubjectClick = onWeakSubjectClick, onDueClick = onDueClick
                    )
                }
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    FilterBar(
                        searchQuery = searchQuery, onSearchChange = { searchQuery = it },
                        selectedSubject = selectedSubject, onSelectSubject = { selectedSubject = it },
                        showOnlyUnmastered = showOnlyUnmastered, onToggleUnmastered = { showOnlyUnmastered = !showOnlyUnmastered },
                        showOnlyDue = showOnlyDue, onToggleDue = { showOnlyDue = !showOnlyDue },
                        dueCount = dueForReview.size
                    )
                    MistakeListContent(
                        windowSizeClass = windowSizeClass, filtered = filtered,
                        onViewDetail = onViewDetail, onToggleMastered = onToggleMastered, onReview = onReview
                    )
                }
            }
        } else {
            // ===== 窄屏/竖屏：保持原有单栏布局 =====
            Column(modifier = Modifier.padding(padding)) {
                StatusCardsPanel(
                    app = app, scope = scope, snackbarHostState = snackbarHostState,
                    allMistakes = allMistakes, masteredCount = masteredCount,
                    reviewStreak = reviewStreak, claimableReviewMilestone = claimableReviewMilestone,
                    reviewCalendar = reviewCalendar, reviewForecast = reviewForecast,
                    weakestSubject = weakestSubject, dueForReview = dueForReview,
                    reviewReminderEnabled = reviewReminderEnabled, reviewReminderHour = reviewReminderHour,
                    onWeakSubjectClick = onWeakSubjectClick, onDueClick = onDueClick
                )
                FilterBar(
                    searchQuery = searchQuery, onSearchChange = { searchQuery = it },
                    selectedSubject = selectedSubject, onSelectSubject = { selectedSubject = it },
                    showOnlyUnmastered = showOnlyUnmastered, onToggleUnmastered = { showOnlyUnmastered = !showOnlyUnmastered },
                    showOnlyDue = showOnlyDue, onToggleDue = { showOnlyDue = !showOnlyDue },
                    dueCount = dueForReview.size
                )
                MistakeListContent(
                    windowSizeClass = windowSizeClass, filtered = filtered,
                    onViewDetail = onViewDetail, onToggleMastered = onToggleMastered, onReview = onReview
                )
            }
        }
    }
}

// ===== 状态卡片面板：连续复习streak、打卡日历、清零进度、7天计划、薄弱科目、待复习提醒、提醒开关 =====
@Composable
private fun StatusCardsPanel(
    app: HStudyApp,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    allMistakes: List<MistakeEntity>,
    masteredCount: Int,
    reviewStreak: Int,
    claimableReviewMilestone: Pair<Int, Int>?,
    reviewCalendar: List<Boolean>,
    reviewForecast: List<Int>,
    weakestSubject: MistakeSubject?,
    dueForReview: List<MistakeEntity>,
    reviewReminderEnabled: Boolean,
    reviewReminderHour: Int,
    onWeakSubjectClick: (MistakeSubject) -> Unit,
    onDueClick: () -> Unit
) {
    // ===== 连续复习 streak（强化每日复习习惯）=====
    if (allMistakes.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("连续复习 $reviewStreak 天", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(
                                    when {
                                        reviewStreak >= 7 -> "太棒了！坚持复习一周以上，记忆超牢固！"
                                        reviewStreak >= 3 -> "坚持得不错！每天复习，遗忘远离你！"
                                        reviewStreak > 0 -> "好的开始！今天也来复习巩固一下吧！"
                                        else -> "今天复习错题，开启连续复习 streak 吧！"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        // 连续复习里程碑奖励（坚持复习有惊喜）
                        if (claimableReviewMilestone != null) {
                            val (days, bonus) = claimableReviewMilestone!!
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.pointRepository.adjustPoints(bonus, "连续复习${days}天里程碑奖励")
                                        app.userPreferences.addReviewStreakMilestoneClaimed(days)
                                        com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                                        snackbarHostState.showSnackbar("🎉 连续复习${days}天达成！奖励 +${bonus} 分！")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("🎁 领取连续复习${days}天奖励 +${bonus}分") }
                        }
                    }
                }
            }

            // ===== 复习打卡日历（过去28天复习记录可视化，激励坚持不断链）=====
            if (allMistakes.isNotEmpty()) {
                val reviewedDays = reviewCalendar.count { it }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅 复习打卡日历", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("近28天复习 $reviewedDays 天", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (week in 0 until 4) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (day in 0 until 7) {
                                        val reviewed = reviewCalendar[week * 7 + day]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(16.dp)
                                                .background(
                                                    if (reviewed) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                    RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        Text("坚持每天复习，让格子都亮起来！", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ===== 错题清零进度 + 掌握里程碑激励 =====
            if (allMistakes.isNotEmpty()) {
                val masteryMilestones = listOf(5 to "错题新手", 15 to "错题进阶", 30 to "错题熟练", 50 to "错题克星", 100 to "错题大师")
                val total = allMistakes.size
                val progress = masteredCount.toFloat() / total
                val nextMilestone = masteryMilestones.firstOrNull { it.first > masteredCount }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆 错题清零进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            Text("$masteredCount/$total", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (nextMilestone != null) {
                            val need = nextMilestone.first - masteredCount
                            Text("再掌握 $need 道，即可解锁「${nextMilestone.second}」里程碑！",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Text("🎉 已解锁全部里程碑，你是真正的错题大师！",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // ===== 未来7天复习计划预览（提前看见复习节奏）=====
            if (allMistakes.isNotEmpty() && reviewForecast.sum() > 0) {
                val forecastLabels = listOf("今天", "明天", "后天", "3天后", "4天后", "5天后", "6天后")
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📆 未来7天复习计划", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            reviewForecast.forEachIndexed { index, count ->
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height((count * 5 + 4).dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(forecastLabels[index], style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 薄弱科目快速复习（把掌握度分析转化为行动）
            if (weakestSubject != null) {
                val weakName = when (weakestSubject) {
                    MistakeSubject.MATH -> "数学"; MistakeSubject.CHINESE -> "语文"
                    MistakeSubject.ENGLISH -> "英语"; MistakeSubject.SCIENCE -> "科学"; else -> "其他"
                }
                Card(
                    onClick = { onWeakSubjectClick(weakestSubject) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("重点攻克薄弱科目：$weakName", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("点击筛选${weakName}未掌握错题，针对性突破", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // 今日待复习提醒横幅（艾宾浩斯遗忘曲线：及时复习记得牢）
            if (dueForReview.isNotEmpty()) {
                Card(
                    onClick = onDueClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("今日有 ${dueForReview.size} 道错题待复习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("按遗忘曲线及时复习，记忆更牢固，点击查看", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // ===== 每日复习提醒开关（让间隔复习真正落实到日常）=====
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏰", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每日复习提醒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("每天定时提醒你复习错题，不忘复习", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = reviewReminderEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    app.userPreferences.setReviewReminder(enabled, reviewReminderHour)
                                    if (enabled) {
                                        ReviewReminderReceiver.schedule(app.applicationContext, reviewReminderHour)
                                        snackbarHostState.showSnackbar("✅ 已开启每日${reviewReminderHour}点复习提醒")
                                    } else {
                                        ReviewReminderReceiver.cancel(app.applicationContext)
                                        snackbarHostState.showSnackbar("已关闭复习提醒")
                                    }
                                }
                            }
                        )
                    }
                    if (reviewReminderEnabled) {
                        Text("提醒时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(17, 18, 19, 20, 21).forEach { h ->
                                FilterChip(
                                    selected = reviewReminderHour == h,
                                    onClick = {
                                        scope.launch {
                                            app.userPreferences.setReviewReminder(true, h)
                                            ReviewReminderReceiver.schedule(app.applicationContext, h)
                                        }
                                    },
                                    label = { Text("${h}点") }
                                )
                            }
                        }
                    }
                }
            }
}

// ===== 筛选栏：搜索框 + 科目筛选 + 未掌握/待复习筛选 =====
@Composable
private fun FilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedSubject: MistakeSubject?,
    onSelectSubject: (MistakeSubject?) -> Unit,
    showOnlyUnmastered: Boolean,
    onToggleUnmastered: () -> Unit,
    showOnlyDue: Boolean,
    onToggleDue: () -> Unit,
    dueCount: Int
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("搜索错题（题目/错因/解法）") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                }
            }
        },
        singleLine = true
    )
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(selected = selectedSubject == null, onClick = { onSelectSubject(null) }, label = { Text("全部") })
        FilterChip(selected = selectedSubject == MistakeSubject.MATH, onClick = { onSelectSubject(if (selectedSubject == MistakeSubject.MATH) null else MistakeSubject.MATH) }, label = { Text("数学") })
        FilterChip(selected = selectedSubject == MistakeSubject.CHINESE, onClick = { onSelectSubject(if (selectedSubject == MistakeSubject.CHINESE) null else MistakeSubject.CHINESE) }, label = { Text("语文") })
        FilterChip(selected = selectedSubject == MistakeSubject.ENGLISH, onClick = { onSelectSubject(if (selectedSubject == MistakeSubject.ENGLISH) null else MistakeSubject.ENGLISH) }, label = { Text("英语") })
    }
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilterChip(
            selected = showOnlyUnmastered,
            onClick = onToggleUnmastered,
            label = { Text("仅未掌握") },
            leadingIcon = if (showOnlyUnmastered) {{ Icon(Icons.Filled.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }} else null
        )
        Spacer(modifier = Modifier.width(6.dp))
        FilterChip(
            selected = showOnlyDue,
            onClick = onToggleDue,
            label = { Text("今日待复习${if (dueCount > 0) " $dueCount" else ""}") }
        )
    }
}

// ===== 错题列表内容：空状态 / LazyColumn =====
@Composable
private fun MistakeListContent(
    windowSizeClass: WindowSizeClass,
    filtered: List<MistakeEntity>,
    onViewDetail: (Long) -> Unit,
    onToggleMastered: (MistakeEntity) -> Unit,
    onReview: (MistakeEntity) -> Unit
) {
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📖", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("还没有错题记录，点击下方 + 添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(filtered, key = { it.id }) { mistake ->
                    MistakeCard(
                        mistake = mistake,
                        onClick = { onViewDetail(mistake.id) },
                        onToggleMastered = { onToggleMastered(mistake) },
                        onReview = { onReview(mistake) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun MistakeCard(
    mistake: MistakeEntity,
    onClick: () -> Unit,
    onToggleMastered: () -> Unit,
    onReview: () -> Unit
) {
    val isDue = SpacedRepetition.isDueForReview(mistake)
    val bgColor by animateColorAsState(
        when {
            mistake.isMastered -> MaterialTheme.colorScheme.secondaryContainer
            isDue -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "bg"
    )
    val subjectEmoji = when (mistake.subject) {
        MistakeSubject.MATH -> "🔢"
        MistakeSubject.CHINESE -> "📝"
        MistakeSubject.ENGLISH -> "🔤"
        MistakeSubject.SCIENCE -> "🔬"
        MistakeSubject.OTHER -> "📋"
    }
    val subjectName = when (mistake.subject) {
        MistakeSubject.MATH -> "数学"
        MistakeSubject.CHINESE -> "语文"
        MistakeSubject.ENGLISH -> "英语"
        MistakeSubject.SCIENCE -> "科学"
        MistakeSubject.OTHER -> "其他"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(subjectEmoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mistake.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (mistake.photoPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("📷", style = MaterialTheme.typography.labelSmall)
                    }
                    if (isDue) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🔔待复习", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subjectName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(SpacedRepetition.proficiencyLabel(mistake.reviewCount),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 下次复习时间（让间隔复习计划透明可见）
                    if (!mistake.isMastered) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📅 ${SpacedRepetition.nextReviewLabel(mistake)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                // 掌握进度条（复习次数/5，达5次即巩固，让复习进度可视化）
                if (!mistake.isMastered) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (mistake.reviewCount / 5f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            // 复习按钮（未掌握时显示）
            if (!mistake.isMastered) {
                FilledTonalButton(
                    onClick = onReview,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("复习+3", style = MaterialTheme.typography.labelMedium) }
                Spacer(modifier = Modifier.width(4.dp))
            }
            // 掌握切换按钮
            IconButton(onClick = onToggleMastered) {
                if (mistake.isMastered) {
                    Text("✅", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "标记掌握",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

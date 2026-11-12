package com.hstudy.app.ui.target

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
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

/** 预设每日学习目标（孩子从中挑选今天要完成的） */
private data class DailyTargetDef(val id: String, val emoji: String, val title: String)

private val dailyTargetDefs = listOf(
    DailyTargetDef("review_mistakes", "📚", "复习错题"),
    DailyTargetDef("focus_pomodoro", "🍅", "专注2个番茄钟"),
    DailyTargetDef("homework", "✏️", "完成作业"),
    DailyTargetDef("preview", "📖", "预习新课"),
    DailyTargetDef("reading", "📕", "阅读30分钟"),
    DailyTargetDef("english", "🔤", "英语听说训练"),
    DailyTargetDef("organize", "🗂️", "整理笔记"),
    DailyTargetDef("exercise", "🏃", "体育锻炼"),
)

/**
 * 每日学习目标（自我调节学习）：孩子主动设定今天的学习目标并追踪达成，
 * 培养"我要学"的主人翁意识与元认知能力。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildDailyTargetScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.childDailyTarget
    val displayLanguage = LocalDisplayLanguage.current
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = PointRepository.getTodayString()

    val targetDate by app.userPreferences.dailyTargetDate.collectAsState(initial = null)
    val selectedTargets by app.userPreferences.dailyTargets.collectAsState(initial = emptySet())
    val doneTargets by app.userPreferences.dailyTargetsDone.collectAsState(initial = emptySet())
    val bonusDate by app.userPreferences.dailyTargetBonusDate.collectAsState(initial = null)

    // 每日复盘状态
    val reflectionDate by app.userPreferences.dailyReflectionDate.collectAsState(initial = null)
    val reflectionStars by app.userPreferences.dailyReflectionStars.collectAsState(initial = 0)
    val reflectionTags by app.userPreferences.dailyReflectionTags.collectAsState(initial = emptySet())
    var pickStars by remember { mutableIntStateOf(0) }
    var pickTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    val reflectionSavedToday = reflectionDate == today

    // 智能推荐所需数据：根据薄弱项自动生成推荐目标
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val allSessions by app.focusRepository.getAllSessions().collectAsState(initial = emptyList())
    val recommendations = remember(allMistakes, allSessions, displayLanguage) {
        val weekAgo = com.hstudy.app.ui.dt.nowMillis() - 7L * 24 * 60 * 60 * 1000
        val recentFocus = allSessions.filter { it.completedAt >= weekAgo }.sumOf { it.durationMinutes }
        GoalRecommendationEngine.generate(allMistakes, recentFocus, displayLanguage)
    }

    // 是否为今天设定了目标
    val isSetToday = targetDate == today && selectedTargets.isNotEmpty()

    // 选目标模式下的临时选择
    var picking by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isSetToday || editMode) {
                        // ===== 设定目标模式 =====
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text(strings.setGoalsHint,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                        }

                        // ===== 🤖 智能推荐（根据薄弱项自动推荐今日目标）=====
                        if (recommendations.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(strings.recommendTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.recommendSubtitle, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    recommendations.take(4).forEach { rec ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(rec.emoji, style = MaterialTheme.typography.titleSmall)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rec.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                Text(rec.reason, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { picking = recommendations.take(4).map { it.targetId }.toSet() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(strings.adoptAllButton) }
                                }
                            }
                        }

                        Text(strings.pickGoalsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            dailyTargetDefs.forEach { def ->
                                FilterChip(
                                    selected = def.id in picking,
                                    onClick = {
                                        picking = if (def.id in picking) picking - def.id else picking + def.id
                                    },
                                    label = { Text("${def.emoji} ${localizedPresetText(def.title, displayLanguage)}") }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (picking.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar(strings.needOneGoal) }
                                } else {
                                    scope.launch {
                                        app.userPreferences.setDailyTargets(today, picking)
                                        editMode = false
                                        snackbarHostState.showSnackbar(strings.goalsSet)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = picking.isNotEmpty()
                        ) { Text(strings.setGoalsButton(picking.size)) }
                    } else {
                        // ===== 目标追踪模式 =====
                        val total = selectedTargets.size
                        val done = selectedTargets.count { it in doneTargets }
                        val progress = if (total > 0) done.toFloat() / total else 0f

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(strings.todayProgressTitle, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("$done/$total", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                if (done == total && total > 0) {
                                    Text(strings.allDonePraise,
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }

                        // 目标清单（可勾选完成）
                        dailyTargetDefs.filter { it.id in selectedTargets }.forEach { def ->
                            val isDone = def.id in doneTargets
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDone) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isDone,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                app.userPreferences.setDailyTargetDone(def.id, checked)
                                                val targetKey = "TARGET:${def.id}:$today"
                                                if (checked) {
                                                    // 完成单个目标 +2 分：走审批，此刻只登记申请，审批通过后才计分
                                                    app.pointRepository.checkIn(
                                                        bizKey = targetKey,
                                                        actionType = "DAILY_TARGET",
                                                        description = "完成每日目标：${def.title}",
                                                        points = 2,
                                                        date = today
                                                    )
                                                    // 检查是否全部达成→额外奖励 +10（每天只领一次）
                                                    val newDoneCount = doneTargets.size + 1
                                                    if (newDoneCount >= selectedTargets.size && bonusDate != today) {
                                                        app.pointRepository.checkIn(
                                                            bizKey = "TARGET_ALL:$today",
                                                            actionType = "DAILY_TARGET",
                                                            description = "每日学习目标全部达成奖励",
                                                            points = 10,
                                                            date = today
                                                        )
                                                        app.userPreferences.setDailyTargetBonusDate(today)
                                                        snackbarHostState.showSnackbar(strings.allDoneBonus)
                                                    } else {
                                                        snackbarHostState.showSnackbar(strings.goalDonePending(localizedPresetText(def.title, displayLanguage)))
                                                    }
                                                } else {
                                                    // 取消勾选 = 撤销该目标打卡：
                                                    // 未审批则取消申请，已审批则扣回已发积分，杜绝重复累计与积分残留
                                                    app.pointRepository.undoCheckIn(targetKey, date = today)
                                                    val newDoneCount = (doneTargets - def.id).size
                                                    if (newDoneCount < selectedTargets.size) {
                                                        // 已不再"全部达成"→ 同步撤销全部达成奖励（已发放则扣回）
                                                        app.pointRepository.undoCheckIn("TARGET_ALL:$today", date = today)
                                                        app.userPreferences.setDailyTargetBonusDate("")
                                                    }
                                                    snackbarHostState.showSnackbar(strings.goalUndone(localizedPresetText(def.title, displayLanguage)))
                                                }
                                            }
                                        }
                                    )
                                    Text(def.emoji, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        localizedPresetText(def.title, displayLanguage),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isDone) Text("✅", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { picking = selectedTargets; editMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(strings.resetGoalsButton) }

                        // ===== 每日复盘（自我调节学习的反思环节）=====
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(strings.reflectionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (reflectionSavedToday) {
                                    // 今天已复盘，展示结果
                                    Text(strings.selfRating(reflectionStars.coerceAtLeast(1)),
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (reflectionTags.isNotEmpty()) {
                                        Text(strings.reflectionTags(reflectionTags.map { localizedPresetText(it, displayLanguage) }),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(strings.reflectionDone,
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text(strings.reflectionPrompt, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    // 星级自评（点击星星打分）
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        (1..5).forEach { star ->
                                            TextButton(
                                                onClick = { pickStars = star },
                                                contentPadding = PaddingValues(2.dp)
                                            ) {
                                                Text(
                                                    if (star <= pickStars) "⭐" else "☆",
                                                    style = MaterialTheme.typography.headlineSmall
                                                )
                                            }
                                        }
                                        if (pickStars > 0) {
                                            Text(
                                                when (pickStars) { 1 -> strings.starLabel1; 2 -> strings.starLabel2; 3 -> strings.starLabel3; 4 -> strings.starLabel4; else -> strings.starLabel5 },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            )
                                        }
                                    }
                                    // 反思标签
                                    val tagOptions = listOf("今天很专注", "完成了难题", "复习了错题", "有点分心", "时间不够用", "明天会更好")
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        tagOptions.forEach { tag ->
                                            FilterChip(
                                                selected = tag in pickTags,
                                                onClick = { pickTags = if (tag in pickTags) pickTags - tag else pickTags + tag },
                                                label = { Text(localizedPresetText(tag, displayLanguage), style = MaterialTheme.typography.labelMedium) }
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (pickStars == 0) {
                                                scope.launch { snackbarHostState.showSnackbar(strings.rateFirst) }
                                            } else {
                                                scope.launch {
                                                    app.userPreferences.saveDailyReflection(today, pickStars, pickTags)
                                                    snackbarHostState.showSnackbar(strings.reflectionSaved)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = pickStars > 0
                                    ) { Text(strings.saveReflectionButton) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

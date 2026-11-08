package com.hstudy.app.ui.child

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.CanonicalChildHomeText
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.common.*
import com.hstudy.app.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun ChildHomeScreen(
    onNavigate: (String) -> Unit,
    onSwitchRole: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.childHome
    val taskListStrings = LocalStrings.current.taskList
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSyncing by remember { mutableStateOf(false) }

    // 接收家长设置：跨平台改用 Platform.fileSharer 文本选择器（替代 OpenDocument ActivityResult）
    val importControlLauncher: () -> Unit = {
        Platform.fileSharer.pickTextFile { text ->
            if (text != null) {
                scope.launch {
                    isSyncing = true
                    val result = com.hstudy.app.data.sync.FamilySyncHelper.importControlConfig(text)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("✅ ${result.getOrNull()}")
                    } else {
                        snackbarHostState.showSnackbar(strings.importFailedMessage(result.exceptionOrNull()?.message))
                    }
                    isSyncing = false
                }
            }
        }
    }
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    // 积分申请状态（孩子查看自己的审核进度）
    val pendingApprovalCount by app.pointApprovalRepository.getPendingCount().collectAsState(initial = 0)
    // 待审批积分：仅展示，不参与余额，审批通过后才计入
    val pendingApprovalPoints by app.pointRepository.getPendingApprovalPoints().collectAsState(initial = 0)
    val allApprovals by app.pointApprovalRepository.getAllApprovals().collectAsState(initial = emptyList())
    val recentApproved = allApprovals.filter { it.status == com.hstudy.app.data.local.entity.ApprovalStatus.APPROVED }.take(3)
    val recentRejected = allApprovals.filter { it.status == com.hstudy.app.data.local.entity.ApprovalStatus.REJECTED }.take(2)
    // 我的目标：心愿存钱罐 + 奖励商店
    val wishGoals by app.wishGoalRepository.getActiveGoals().collectAsState(initial = emptyList())
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    // 今日待复习错题（艾宾浩斯间隔复习）
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val dueMistakes = remember(allMistakes) { allMistakes.filter { com.hstudy.app.ui.mistake.SpacedRepetition.isDueForReview(it) } }
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())
    // 家长今日布置的任务
    val assignments by app.assignmentRepository.getTodayAssignments().collectAsState(initial = emptyList())

    // 每日学习表现评分所需：今日专注时长 + 今日复习次数
    val todayFocusMinutes by app.focusRepository.getTotalFocusMinutesForDate(today).collectAsState(initial = 0)
    val todayStartMillis = remember(today) {
        com.hstudy.app.ui.dt.Calendar.getInstance().apply {
            set(com.hstudy.app.ui.dt.Calendar.HOUR_OF_DAY, 0); set(com.hstudy.app.ui.dt.Calendar.MINUTE, 0)
            set(com.hstudy.app.ui.dt.Calendar.SECOND, 0); set(com.hstudy.app.ui.dt.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayReviewCount by app.mistakeRepository.getReviewCountSince(todayStartMillis).collectAsState(initial = 0)

    // 连续打卡天数
    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { streak = app.pointRepository.getCurrentStreak() }

    // 每日必须任务未完成自动扣分（每天只触发一次）
    LaunchedEffect(Unit) {
        val penalty = com.hstudy.app.data.repository.MandatoryPenaltyHelper.processPenalties(app)
        if (penalty > 0) {
            snackbarHostState.showSnackbar(strings.mandatoryPenaltyMessage(penalty))
        }
    }

    // 连续打卡里程碑奖励（天数 -> 奖励积分，约为同期打卡收入的10%额外奖励）
    val streakMilestones = remember { listOf(3 to 5, 7 to 15, 14 to 25, 21 to 40, 30 to 60) }
    val claimedMilestones by app.userPreferences.claimedStreakMilestones.collectAsState(initial = emptySet())
    // 当前可领取的里程碑（已达到天数且未领取）
    val claimableMilestone = streakMilestones.firstOrNull { (days, _) -> streak >= days && days !in claimedMilestones }

    // 每周挑战：本周打卡5天可领+5积分（每周只能领一次）
    var weekCheckinDays by remember { mutableIntStateOf(0) }
    var weekStartStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val (days, weekStr) = app.pointRepository.getThisWeekCheckinInfo()
        weekCheckinDays = days
        weekStartStr = weekStr
    }
    val claimedChallengeWeek by app.userPreferences.weeklyChallengeClaimedWeek.collectAsState(initial = null)
    val weeklyChallengeDone = weekCheckinDays >= 5
    val weeklyChallengeClaimable = weeklyChallengeDone && claimedChallengeWeek != weekStartStr
    val weeklyChallengeClaimed = claimedChallengeWeek == weekStartStr

    val todayCompleted = completedIds.size
    val todayTotal = tasks.size

    // 鼓励语
    val encouragement = when {
        todayTotal == 0 -> strings.encouragementNoTask
        todayCompleted == 0 -> strings.encouragementFreshStart
        todayCompleted == todayTotal -> strings.encouragementAllDone
        todayCompleted.toFloat() / todayTotal >= 0.7f -> strings.encouragementAlmostThere
        todayCompleted.toFloat() / todayTotal >= 0.3f -> strings.encouragementOnTrack
        else -> strings.encouragementKickoff
    }

    // 积分卡片呼吸动画
    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "breathScale"
    )

    val levelInfo = LevelSystem.getLevelInfo(totalPoints)

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = strings.pageTitle,
                actions = {
                    IconButton(onClick = onSwitchRole) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = strings.switchRoleContentDescription)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // 氛围漂浮背景（置于内容之下）
            AmbientBackground()
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ===== 积分 + 等级 + 学习树 综合卡片（暖色渐变焦点）=====
                Card(
                    modifier = Modifier.fillMaxWidth().scale(breathScale),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFE8CC),
                                    Color(0xFFFFD8A8),
                                    Color(0xFFFFC078)
                                )
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧：学习树
                            LearningTreeCanvas(level = levelInfo.level)
                            Spacer(modifier = Modifier.width(12.dp))
                            // 右侧：积分 + 等级
                            Column(modifier = Modifier.weight(1f)) {
                                AnimatedPointsDisplay(targetPoints = totalPoints)
                                LevelBadge(totalEarnedPoints = totalPoints)
                                if (streak > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StreakBadge(streakDays = streak)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 鼓励语
                Text(
                    encouragement,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 今日打卡进度 =====
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.todayCheckinTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${todayCompleted}/${todayTotal}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val animatedProgress by animateFloatAsState(
                            targetValue = if (todayTotal > 0) todayCompleted.toFloat() / todayTotal else 0f,
                            animationSpec = tween(800, easing = FastOutSlowInEasing), label = "progress"
                        )
                        LinearProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        if (todayCompleted == todayTotal && todayTotal > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(strings.todayAllDoneBanner, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 先难后易建议（吃青蛙原则：精力充沛时先攻克困难任务）=====
                val hardTasksNotDone = remember(tasks, completedIds) {
                    tasks.filter { it.difficulty >= 2 && it.id !in completedIds }
                }
                if (hardTasksNotDone.isNotEmpty() && todayCompleted < todayTotal) {
                    val hardest = hardTasksNotDone.maxByOrNull { it.difficulty }!!
                    val hardestStars = when (hardest.difficulty) { 3 -> "⭐⭐⭐"; 2 -> "⭐⭐"; else -> "⭐" }
                    val hardestName = when (hardest.difficulty) { 3 -> strings.difficultyChallengeLevel; 2 -> strings.difficultyAdvancedLevel; else -> strings.difficultyNormalLevel }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🐸", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.hardTaskFirstTitle(hardTasksNotDone.size), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(strings.tackleFirstHint(hardest.title, hardestStars, hardestName),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 今日学习表现评分（打卡+专注+复习综合反馈）=====
                val checkinRate = if (todayTotal > 0) todayCompleted.toFloat() / todayTotal else 0f
                val learningScore = remember(todayCompleted, todayTotal, todayFocusMinutes, todayReviewCount) {
                    com.hstudy.app.ui.common.LearningScoreCalculator.calculate(checkinRate, todayFocusMinutes, todayReviewCount)
                }
                val excellentBonusDate by app.userPreferences.dailyExcellentBonusDate.collectAsState(initial = "")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(learningScore.gradeEmoji, fontSize = 32.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.learningScoreTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${learningScore.grade} · ${learningScore.encouragement}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text("${learningScore.total}", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(strings.scoreUnit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        // 三维度得分条
                        ScoreDimensionBar(strings.scoreDimensionCheckin, learningScore.checkinScore, 40, MaterialTheme.colorScheme.primary)
                        ScoreDimensionBar(strings.scoreDimensionFocus, learningScore.focusScore, 30, MaterialTheme.colorScheme.tertiary)
                        ScoreDimensionBar(strings.scoreDimensionReview, learningScore.reviewScore, 30, MaterialTheme.colorScheme.secondary)
                        // 维度改进建议（把评分反馈转化为具体行动指引）
                        val weakTips = buildList {
                            if (learningScore.checkinScore < 20 && todayTotal > 0 && todayCompleted < todayTotal) add(strings.weakTipCheckin)
                            if (learningScore.focusScore < 15) add(strings.weakTipFocus)
                            if (learningScore.reviewScore < 15 && dueMistakes.isNotEmpty()) add(strings.weakTipReview)
                        }
                        if (weakTips.isNotEmpty() && learningScore.total < 85) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(strings.improvementSuggestionsHeading, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                weakTips.forEach { tip ->
                                    Text("· $tip", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                        // 优秀奖励：评分达85+可领一次性奖励（激励打卡+专注+复习全面卓越）
                        if (learningScore.total >= 85 && excellentBonusDate != today) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.pointRepository.adjustPoints(2, CanonicalChildHomeText.EXCELLENT_BONUS_NOTE)
                                        app.userPreferences.setDailyExcellentBonusDate(today)
                                        com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                                        snackbarHostState.showSnackbar(strings.excellentBonusMessage)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(strings.claimExcellentBonusButton) }
                        } else if (learningScore.total >= 85) {
                            Text(strings.excellentBonusClaimedNote,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 学习宠物状态预览（增强情感连接，提醒照料宠物）=====
                com.hstudy.app.ui.pet.PetPreviewCard(
                    onNavigateToPet = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildPet.route) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 今日待复习错题提醒（艾宾浩斯间隔复习，提升记忆保持率）=====
                if (dueMistakes.isNotEmpty()) {
                    Card(
                        onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildMistakeList.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📚", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.mistakesDueTitle(dueMistakes.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(strings.mistakesDueNote, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (todayReviewCount > 0 && allMistakes.isNotEmpty()) {
                    // 今日待复习错题已全部完成→正向反馈（强化完成每日复习的成就感）
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎉", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.mistakesAllDoneTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(strings.mistakesAllDoneNote, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 成就徽章 =====
                AchievementBadges(modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildAchievements.route) }) {
                    Text(strings.viewAllAchievementsButton)
                }

                // ===== 积分申请状态（孩子查看审核进度）=====
                if (pendingApprovalCount > 0 || recentApproved.isNotEmpty() || recentRejected.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pendingApprovalCount > 0) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.myApprovalRequestsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (pendingApprovalCount > 0) {
                                Text(strings.pendingApprovalSummary(pendingApprovalCount, pendingApprovalPoints),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            recentApproved.forEach { a ->
                                Text(strings.approvedRequestLine(a.description, a.requestedPoints), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            recentRejected.forEach { a ->
                                Text(strings.rejectedRequestLine(a.description), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 家长同步（接收家长设置 / 发送进度给家长）=====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.parentSyncTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { importControlLauncher() },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) { Text(strings.receiveParentSettingsButton, style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSyncing = true
                                        val result = com.hstudy.app.data.sync.FamilySyncHelper.exportProgressReport()
                                        if (result.isSuccess) {
                                            com.hstudy.app.data.sync.FamilySyncHelper.shareFile(
                                                result.getOrNull()!!, strings.shareProgressChooserTitle
                                            )
                                            snackbarHostState.showSnackbar(strings.progressExportedMessage)
                                        } else {
                                            snackbarHostState.showSnackbar(strings.exportFailedMessage)
                                        }
                                        isSyncing = false
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) { Text(strings.sendProgressButton, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (isSyncing) {
                            Text(strings.syncingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 连续打卡里程碑奖励：达到天数可领取额外积分 =====
                claimableMilestone?.let { (days, bonus) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 32.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.streakMilestoneTitle(days), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(strings.streakMilestoneBonusNote(bonus), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Button(onClick = {
                                scope.launch {
                                    app.pointRepository.adjustPoints(bonus, CanonicalChildHomeText.streakMilestoneNote(days))
                                    app.userPreferences.claimStreakMilestone(days)
                                    snackbarHostState.showSnackbar(strings.streakMilestoneClaimedMessage(bonus))
                                }
                            }) { Text(strings.claimBonusButton) }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 每周挑战：本周打卡5天领额外积分，每周刷新 =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏅", fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.weeklyChallengeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(strings.weeklyChallengeProgressNote(weekCheckinDays.coerceAtMost(5)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = (weekCheckinDays / 5f).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        when {
                            weeklyChallengeClaimable -> Button(onClick = {
                                scope.launch {
                                    app.pointRepository.adjustPoints(5, CanonicalChildHomeText.WEEKLY_CHALLENGE_NOTE)
                                    app.userPreferences.claimWeeklyChallenge(weekStartStr)
                                    snackbarHostState.showSnackbar(strings.weeklyChallengeClaimMessage)
                                }
                            }) { Text(strings.claimButton) }
                            weeklyChallengeClaimed -> Text(strings.weeklyChallengeClaimedNote, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            else -> Text(strings.weeklyChallengeInProgressNote, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 我的目标：让奋斗目标始终可见，激发孩子主动性 =====
                MyGoalCard(
                    totalPoints = totalPoints,
                    wishGoals = wishGoals,
                    rewards = rewards,
                    onNavigate = onNavigate
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================================
                // 🎯 4步学习引导流程（横屏双栏，竖屏单栏）
                // ==========================================================
                val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

                // 统一定义4步内容，避免重复
                val step1 = @Composable {
                    // 家长今日布置卡片（如有）
                    if (assignments.isNotEmpty()) {
                        Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.parentAssignmentsTitle, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            assignments.forEach { a ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (a.isCompleted) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(taskListStrings.pointsEarnedLabel(a.points), style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (a.parentNote.isNotEmpty()) {
                                                Text("💬 ${a.parentNote}", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (a.isCompleted) {
                                            Text("✅", style = MaterialTheme.typography.titleMedium)
                                        } else {
                                            Button(onClick = {
                                                scope.launch {
                                                    val assignDate = com.hstudy.app.data.repository.PointRepository.getTodayString()
                                                    // 完成布置任务同样走审批：此刻只登记申请，审批通过后才计分
                                                    app.assignmentRepository.completeAssignment(a.id) { pts, name ->
                                                        app.pointRepository.checkIn(
                                                            bizKey = "ASSIGN:${a.id}:$assignDate",
                                                            actionType = "ASSIGNMENT",
                                                            description = CanonicalChildHomeText.assignmentDescription(name),
                                                            points = pts,
                                                            date = assignDate
                                                        )
                                                    }
                                                    snackbarHostState.showSnackbar(strings.assignmentCompletedMessage(a.points))
                                                }
                                            }) {
                                                Text(strings.completeAssignmentButton)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    StepSection(
                        step = 1, title = strings.step1Title, subtitle = strings.step1Subtitle,
                        color = MaterialTheme.colorScheme.primary,
                        entries = listOf(
                            StepEntry("✏️", strings.entryStartCheckinTitle, strings.entryStartCheckinSubtitle, Screen.ChildTaskList.route),
                            StepEntry("🐾", strings.entryMyPetTitle, strings.entryMyPetSubtitle, Screen.ChildPet.route)
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step2 = @Composable {
                    StepSection(
                        step = 2, title = strings.step2Title, subtitle = strings.step2Subtitle,
                        color = MaterialTheme.colorScheme.tertiary,
                        entries = listOf(
                            StepEntry("🍅", strings.entryPomodoroTitle, strings.entryPomodoroSubtitle, Screen.ChildPomodoro.route),
                            StepEntry("📚", strings.entryMistakeNotebookTitle, strings.entryMistakeNotebookSubtitle, Screen.ChildMistakeList.route),
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step3 = @Composable {
                    StepSection(
                        step = 3, title = strings.step3Title, subtitle = strings.step3Subtitle,
                        color = MaterialTheme.colorScheme.secondary,
                        entries = listOf(
                            StepEntry("📋", strings.entryDailyPlanTitle, strings.entryDailyPlanSubtitle, Screen.ChildDailyPlan.route),
                            StepEntry("🎯", strings.entryDailyTargetTitle, strings.entryDailyTargetSubtitle, Screen.ChildDailyTarget.route),
                            StepEntry("📅", strings.entryScheduleTimelineTitle, strings.entryScheduleTimelineSubtitle, Screen.ChildScheduleTimeline.route),
                            StepEntry("🎯", strings.entryHabitChallengeTitle, strings.entryHabitChallengeSubtitle, Screen.ChildHabitChallenge.route),
                            StepEntry("📈", strings.entryGoalTrackerTitle, strings.entryGoalTrackerSubtitle, Screen.ChildGoalTracker.route),
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step4 = @Composable {
                    StepSection(
                        step = 4, title = strings.step4Title, subtitle = strings.step4Subtitle,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        entries = listOf(
                            StepEntry("🎰", strings.entryLuckyDrawTitle, strings.entryLuckyDrawSubtitle, Screen.ChildLuckyDraw.route),
                            StepEntry("📊", strings.entryEfficiencyReportTitle, strings.entryEfficiencyReportSubtitle, Screen.ChildEfficiencyReport.route),
                            StepEntry("⭐", strings.entryPointsDetailTitle, strings.entryPointsDetailSubtitle, Screen.ChildPoints.route),
                            StepEntry("🎁", strings.entryRewardStoreTitle, strings.entryRewardStoreSubtitle, Screen.ChildRewardStore.route),
                            StepEntry("🐷", strings.entryWishBankTitle, strings.entryWishBankSubtitle, Screen.ChildWishGoal.route),
                            StepEntry("📅", strings.entryWeeklyReportTitle, strings.entryWeeklyReportSubtitle, Screen.ChildWeeklyView.route),
                        ),
                        onNavigate = onNavigate
                    )
                }

                if (isExpanded) {
                    // 横屏：左右双栏
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            step1()
                            Spacer(modifier = Modifier.height(8.dp))
                            step2()
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            step3()
                            Spacer(modifier = Modifier.height(8.dp))
                            step4()
                        }
                    }
                } else {
                    // 竖屏：单栏顺序排列
                    step1()
                    Spacer(modifier = Modifier.height(8.dp))
                    step2()
                    Spacer(modifier = Modifier.height(8.dp))
                    step3()
                    Spacer(modifier = Modifier.height(8.dp))
                    step4()
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ============================================================
// 步骤区块组件
// ============================================================
private data class StepEntry(val emoji: String, val title: String, val subtitle: String, val route: String)

@Composable
private fun StepSection(
    step: Int,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    entries: List<StepEntry>,
    onNavigate: (String) -> Unit
) {
    Column {
        // 步骤标题
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 步骤编号圆形
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${step}", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 功能卡片 - 横向小卡片
        entries.forEach { entry ->
            Card(
                onClick = { onNavigate(entry.route) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(entry.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ============================================================
// 我的目标卡片：心愿存钱罐进度 + 下个可兑换奖励，让奋斗目标始终可见
// ============================================================
@Composable
private fun MyGoalCard(
    totalPoints: Int,
    wishGoals: List<com.hstudy.app.data.local.entity.WishGoalEntity>,
    rewards: List<com.hstudy.app.data.local.entity.RewardEntity>,
    onNavigate: (String) -> Unit
) {
    val strings = LocalStrings.current.childHome
    val mainWish = wishGoals.firstOrNull()
    val nextReward = rewards.firstOrNull { totalPoints < it.pointsCost }
    val affordableCount = rewards.count { totalPoints >= it.pointsCost }

    // 没有任何目标时不显示
    if (mainWish == null && nextReward == null && affordableCount == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.myGoalTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // 心愿存钱罐进度
            if (mainWish != null) {
                val progress = (mainWish.savedPoints.toFloat() / mainWish.targetPoints).coerceIn(0f, 1f)
                val remaining = (mainWish.targetPoints - mainWish.savedPoints).coerceAtLeast(0)
                Card(
                    onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildWishGoal.route) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mainWish.iconEmoji, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.wishGoalNameLabel(mainWish.name), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(strings.wishGoalProgressNote(mainWish.savedPoints, mainWish.targetPoints, remaining),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 下个可兑换的奖励 / 可兑换数量
            Card(
                onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildRewardStore.route) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (affordableCount > 0) {
                        Text(strings.affordableRewardsNote(affordableCount),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                    if (nextReward != null) {
                        val need = nextReward.pointsCost - totalPoints
                        val progress = (totalPoints.toFloat() / nextReward.pointsCost).coerceIn(0f, 1f)
                        Text(strings.saveTowardsRewardNote(need, nextReward.iconEmoji, nextReward.name),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else if (affordableCount == 0) {
                        Text(strings.earnPointsForRewardsNote,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 学习表现评分的单个维度得分条 */
@Composable
private fun ScoreDimensionBar(label: String, score: Int, maxScore: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = score.toFloat() / maxScore,
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("$score/$maxScore", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
    }
}

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }

    // 接收家长设置的文件选择器
    val importControlLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isSyncing = true
                val result = com.hstudy.app.data.sync.FamilySyncHelper.importControlConfig(context, uri)
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("✅ ${result.getOrNull()}")
                } else {
                    snackbarHostState.showSnackbar("导入失败：${result.exceptionOrNull()?.message}")
                }
                isSyncing = false
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
            snackbarHostState.showSnackbar("⚠️ 有每日必须任务未完成，已自动扣减 $penalty 分")
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
        todayTotal == 0 -> "还没有任务哦，请爸爸妈妈添加 📝"
        todayCompleted == 0 -> "新的一天，加油打卡吧！💪"
        todayCompleted == todayTotal -> "太棒了！今天全部完成！🎉"
        todayCompleted.toFloat() / todayTotal >= 0.7f -> "快完成啦，继续加油！🌟"
        todayCompleted.toFloat() / todayTotal >= 0.3f -> "不错不错，保持节奏！👍"
        else -> "开始今天的学习之旅吧！🚀"
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
                title = "🌟 我的学习",
                actions = {
                    IconButton(onClick = onSwitchRole) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "切换角色")
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
                            Text("📊 今日打卡", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                            Text("🎊 今日任务全部完成！", style = MaterialTheme.typography.titleSmall,
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
                    val hardestName = when (hardest.difficulty) { 3 -> "挑战"; 2 -> "进阶"; else -> "普通" }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🐸", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("先难后易 · 还有 ${hardTasksNotDone.size} 个困难任务", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("趁精力充沛，先攻克「${hardest.title}」($hardestStars$hardestName)，剩下的会更轻松！",
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
                                Text("今日学习表现", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${learningScore.grade} · ${learningScore.encouragement}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text("${learningScore.total}", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("分", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        // 三维度得分条
                        ScoreDimensionBar("✏️ 打卡", learningScore.checkinScore, 40, MaterialTheme.colorScheme.primary)
                        ScoreDimensionBar("🍅 专注", learningScore.focusScore, 30, MaterialTheme.colorScheme.tertiary)
                        ScoreDimensionBar("📚 复习", learningScore.reviewScore, 30, MaterialTheme.colorScheme.secondary)
                        // 维度改进建议（把评分反馈转化为具体行动指引）
                        val weakTips = buildList {
                            if (learningScore.checkinScore < 20 && todayTotal > 0 && todayCompleted < todayTotal) add("✏️ 还有任务没完成，加油打卡！")
                            if (learningScore.focusScore < 15) add("🍅 专注时间还不够，用番茄钟专注一会儿吧！")
                            if (learningScore.reviewScore < 15 && dueMistakes.isNotEmpty()) add("📚 还有错题待复习，去复习几道吧！")
                        }
                        if (weakTips.isNotEmpty() && learningScore.total < 85) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("💡 提升建议：", style = MaterialTheme.typography.labelSmall,
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
                                        app.pointRepository.adjustPoints(2, "今日学习表现优秀奖励")
                                        app.userPreferences.setDailyExcellentBonusDate(today)
                                        com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                                        snackbarHostState.showSnackbar("🌟 学习表现优秀！奖励 +2 分！")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("🌟 领取优秀奖励 +2分") }
                        } else if (learningScore.total >= 85) {
                            Text("✅ 今日优秀奖励已领取，明天继续加油！",
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
                                Text("今日有 ${dueMistakes.size} 道错题待复习", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("及时复习记得牢，复习还能赚积分哦～", style = MaterialTheme.typography.bodySmall,
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
                                Text("今日待复习错题已全部完成！", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("坚持间隔复习，记忆越来越牢固，真棒！", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 成就徽章 =====
                AchievementBadges(modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ChildAchievements.route) }) {
                    Text("🏅 查看全部成就中心 →")
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
                            Text("📝 我的积分申请", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (pendingApprovalCount > 0) {
                                Text("⏳ $pendingApprovalCount 个申请待家长审核（+$pendingApprovalPoints 分，通过后计入）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            recentApproved.forEach { a ->
                                Text("✅ ${a.description} +${a.requestedPoints}分", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            recentRejected.forEach { a ->
                                Text("❌ ${a.description}（未通过）", style = MaterialTheme.typography.labelSmall,
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
                        Text("👨‍👩‍👧 家长同步", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { importControlLauncher.launch(arrayOf("*/*")) },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) { Text("📥 接收家长设置", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSyncing = true
                                        val result = com.hstudy.app.data.sync.FamilySyncHelper.exportProgressReport(context)
                                        if (result.isSuccess) {
                                            com.hstudy.app.data.sync.FamilySyncHelper.shareFile(
                                                context, result.getOrNull()!!, "发送学习进度给家长"
                                            )
                                            snackbarHostState.showSnackbar("✅ 进度已导出，请发送给家长")
                                        } else {
                                            snackbarHostState.showSnackbar("导出失败")
                                        }
                                        isSyncing = false
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier.weight(1f)
                            ) { Text("📤 发送进度给家长", style = MaterialTheme.typography.labelSmall) }
                        }
                        if (isSyncing) {
                            Text("正在处理…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("连续打卡 $days 天啦！", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("达成里程碑，领取 +$bonus 积分奖励！", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Button(onClick = {
                                scope.launch {
                                    app.pointRepository.adjustPoints(bonus, "连续打卡${days}天里程碑奖励")
                                    app.userPreferences.claimStreakMilestone(days)
                                    snackbarHostState.showSnackbar("🎉 领取成功！+$bonus 积分")
                                }
                            }) { Text("🎁 领取") }
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
                            Text("每周挑战：本周打卡 5 天", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("进度 ${weekCheckinDays.coerceAtMost(5)}/5 天 · 达成领 +5 积分", style = MaterialTheme.typography.bodySmall,
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
                                    app.pointRepository.adjustPoints(5, "每周挑战奖励：本周打卡5天")
                                    app.userPreferences.claimWeeklyChallenge(weekStartStr)
                                    snackbarHostState.showSnackbar("🏅 挑战达成！+5 积分")
                                }
                            }) { Text("领取") }
                            weeklyChallengeClaimed -> Text("✅ 本周已领取", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            else -> Text("加油中…", style = MaterialTheme.typography.labelSmall,
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
                            Text("📣 家长今日布置", style = MaterialTheme.typography.titleSmall,
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
                                                Text("+${a.points}分", style = MaterialTheme.typography.labelMedium,
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
                                                            description = "完成布置任务: $name",
                                                            points = pts,
                                                            date = assignDate
                                                        )
                                                    }
                                                    snackbarHostState.showSnackbar("🎉 完成布置任务 +${a.points}分（待家长审核）！")
                                                }
                                            }) {
                                                Text("完成")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    StepSection(
                        step = 1, title = "每日打卡", subtitle = "完成今天的学习任务",
                        color = MaterialTheme.colorScheme.primary,
                        entries = listOf(
                            StepEntry("✏️", "开始打卡", "完成任务获得积分", Screen.ChildTaskList.route),
                            StepEntry("🐾", "我的学习宠物", "赚积分喂养它进化", Screen.ChildPet.route)
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step2 = @Composable {
                    StepSection(
                        step = 2, title = "过程管理", subtitle = "专注学习、记录错题、提交证明",
                        color = MaterialTheme.colorScheme.tertiary,
                        entries = listOf(
                            StepEntry("🍅", "番茄钟", "专注学习计时", Screen.ChildPomodoro.route),
                            StepEntry("📚", "错题本", "记录和复习错题", Screen.ChildMistakeList.route),
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step3 = @Composable {
                    StepSection(
                        step = 3, title = "计划规划", subtitle = "安排时间、培养习惯、设定目标",
                        color = MaterialTheme.colorScheme.secondary,
                        entries = listOf(
                            StepEntry("📋", "每日计划", "安排学习时间", Screen.ChildDailyPlan.route),
                            StepEntry("🎯", "每日学习目标", "自己定目标主动学", Screen.ChildDailyTarget.route),
                            StepEntry("📅", "今日时间线", "查看今天日程安排", Screen.ChildScheduleTimeline.route),
                            StepEntry("🎯", "习惯挑战", "21天习惯养成", Screen.ChildHabitChallenge.route),
                            StepEntry("📈", "目标追踪", "学习进度与目标", Screen.ChildGoalTracker.route),
                        ),
                        onNavigate = onNavigate
                    )
                }
                val step4 = @Composable {
                    StepSection(
                        step = 4, title = "数据反馈", subtitle = "查看学习分析和积分详情",
                        color = MaterialTheme.colorScheme.inversePrimary,
                        entries = listOf(
                            StepEntry("🎰", "幸运抽奖", "每日抽奖赢积分", Screen.ChildLuckyDraw.route),
                            StepEntry("📊", "效率报表", "学习数据分析", Screen.ChildEfficiencyReport.route),
                            StepEntry("⭐", "积分明细", "查看积分记录", Screen.ChildPoints.route),
                            StepEntry("🎁", "奖励商店", "用积分兑换奖励", Screen.ChildRewardStore.route),
                            StepEntry("🐷", "心愿存钱罐", "攒积分实现大心愿", Screen.ChildWishGoal.route),
                            StepEntry("📅", "周报查看", "每周打卡汇总", Screen.ChildWeeklyView.route),
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
                Text("我的目标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            Text("心愿：${mainWish.name}", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text("已存 ${mainWish.savedPoints}/${mainWish.targetPoints} 分，还差 $remaining 分达成心愿！",
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
                        Text("🎉 现在可以兑换 $affordableCount 个奖励啦，去商店看看吧！",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                    if (nextReward != null) {
                        val need = nextReward.pointsCost - totalPoints
                        val progress = (totalPoints.toFloat() / nextReward.pointsCost).coerceIn(0f, 1f)
                        Text("再攒 $need 分就能兑换「${nextReward.iconEmoji} ${nextReward.name}」",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else if (affordableCount == 0) {
                        Text("完成任务赚积分，就能兑换奖励啦！加油💪",
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

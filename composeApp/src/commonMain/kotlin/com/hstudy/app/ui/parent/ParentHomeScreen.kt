package com.hstudy.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun ParentHomeScreen(
    onNavigate: (String) -> Unit,
    onSwitchRole: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.parentHome
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())
    val pendingProofCount by app.habitRepository.getPendingProofCount().collectAsState(initial = 0)
    val pendingApprovalCount by app.pointApprovalRepository.getPendingCount().collectAsState(initial = 0)
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)
    // 配置状态（快速配置助手用）
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val wishGoals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val schedules by app.scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())
    // 设备同步状态（配对后持续同步可见化）
    val lastSyncDate by app.userPreferences.lastSyncDate.collectAsState(initial = "")
    val syncReminderEnabled by app.userPreferences.syncReminderEnabled.collectAsState(initial = true)

    // 连续打卡天数
    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { streak = app.pointRepository.getCurrentStreak() }

    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val todayCompleted = completedIds.size
    val todayTotal = tasks.size
    val completionRate = if (todayTotal > 0) (todayCompleted * 100 / todayTotal) else 0
    val unmasteredMistakes = allMistakes.size - masteredCount
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = strings.pageTitle,
                actions = {
                    PointsBadge(points = totalPoints)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onSwitchRole) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = strings.switchRoleContentDescription)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // ===== 孩子今日概览卡片（冷色渐变，区分家长端）=====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFDCEBFF),
                                    Color(0xFFC3DCFF),
                                    Color(0xFFAECFFF)
                                )
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(strings.childTodayOverviewTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OverviewStat(strings.overviewCheckin, "${todayCompleted}/${todayTotal}")
                                OverviewStat(strings.overviewCompletionRate, "${completionRate}%")
                                OverviewStat(strings.overviewStreak, strings.streakDaysValue(streak))
                                OverviewStat(strings.overviewPendingProofs, "${pendingProofCount}")
                            }

                        if (completionRate == 100 && todayTotal > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(strings.todayAllDoneBanner, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }

                        if (pendingProofCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onNavigate(Screen.ParentProofReview.route) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.pendingProofsButton(pendingProofCount))
                            }
                        }

                        if (unmasteredMistakes > 5) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(strings.unmasteredMistakesNote(unmasteredMistakes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 快速配置助手：一眼看清配置状态，缺什么一键补什么 =====
                SetupAssistantCard(
                    taskCount = tasks.size,
                    rewardCount = rewards.size,
                    wishCount = wishGoals.size,
                    scheduleCount = schedules.size,
                    onLoadRewards = {
                        scope.launch {
                            app.rewardRepository.initDefaultRewards()
                            snackbarHostState.showSnackbar(strings.presetRewardsLoadedMessage)
                        }
                    },
                    onNavigate = onNavigate
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 设备同步状态（配对后持续同步可见化，超期时主动提醒）=====
                if (syncReminderEnabled && lastSyncDate.isNotBlank()) {
                    val syncDaysAgo = remember(lastSyncDate) {
                        try {
                            val fmt = com.hstudy.app.ui.dt.SimpleDateFormat("yyyy-MM-dd", com.hstudy.app.ui.dt.Locale.getDefault())
                            ((com.hstudy.app.ui.dt.nowMillis() - fmt.parse(lastSyncDate)!!.time) / 86400000L).toInt()
                        } catch (_: Exception) { 0 }
                    }
                    if (syncDaysAgo > 7) {
                        Card(
                            onClick = { onNavigate(com.hstudy.app.ui.navigation.Screen.ParentSettings.route) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⏰", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.syncOverdueTitle(syncDaysAgo),
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(strings.syncOverdueNote,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ===== 功能菜单 =====
                val items = listOf(
                    MenuAction(strings.menuTaskManageTitle, strings.menuTaskManageSubtitle, Icons.Filled.Task, Screen.ParentTaskManage.route),
                    MenuAction(strings.menuAssignTaskTitle, strings.menuAssignTaskSubtitle, Icons.Filled.Assignment, Screen.ParentAssignTask.route),
                    MenuAction(strings.menuTemplateTitle, strings.menuTemplateSubtitle, Icons.Filled.AutoAwesome, Screen.ParentTaskTemplate.route),
                    MenuAction(strings.menuPointsTitle, strings.menuPointsSubtitle, Icons.Filled.Stars, Screen.ParentPoints.route),
                    MenuAction(strings.menuRewardStoreTitle, strings.menuRewardStoreSubtitle, Icons.Filled.CardGiftcard, Screen.ParentRewardManage.route),
                    MenuAction(strings.menuWishGoalTitle, strings.menuWishGoalSubtitle, Icons.Filled.Star, Screen.ParentWishGoalManage.route),
                    MenuAction(strings.menuScheduleTitle, strings.menuScheduleSubtitle, Icons.Filled.Schedule, Screen.ParentScheduleManage.route),
                    MenuAction(strings.menuProofTitle, strings.menuProofSubtitle, Icons.Filled.Verified, Screen.ParentProofReview.route, pendingProofCount),
                    MenuAction(strings.menuApprovalTitle, strings.menuApprovalSubtitle, Icons.Filled.Approval, Screen.ParentApproval.route, pendingApprovalCount),
                    MenuAction(strings.menuMistakeAnalysisTitle, strings.menuMistakeAnalysisSubtitle, Icons.Filled.MenuBook, Screen.ParentMistakeReview.route),
                    MenuAction(strings.menuCoachingTitle, strings.menuCoachingSubtitle, Icons.Filled.Psychology, Screen.ParentTutoringAdvice.route),
                    MenuAction(strings.menuDashboardTitle, strings.menuDashboardSubtitle, Icons.Filled.Dashboard, Screen.ParentDashboard.route),
                    MenuAction(strings.menuGrowthReportTitle, strings.menuGrowthReportSubtitle, Icons.Filled.EmojiEvents, Screen.ParentGrowthReport.route),
                    MenuAction(strings.menuWeeklyReportTitle, strings.menuWeeklyReportSubtitle, Icons.Filled.CalendarViewWeek, Screen.ParentWeeklyReport.route),
                    MenuAction(strings.menuSettingsTitle, strings.menuSettingsSubtitle, Icons.Filled.Settings, Screen.ParentSettings.route),
                )

                if (isTablet) {
                    val columns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) 3 else 2
                    val rows = items.chunked(columns)
                    rows.forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            rowItems.forEach { item ->
                                HomeMenuCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    badge = item.badge,
                                    icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
                                    onClick = { onNavigate(item.route) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items.forEach { item ->
                        HomeMenuCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            badge = item.badge,
                            icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(36.dp)) },
                            onClick = { onNavigate(item.route) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class MenuAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
    val badge: Int = 0
)

@Composable
private fun OverviewStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * 快速配置助手：展示任务/奖励/心愿/日程的配置状态，缺什么一键补什么，帮家长快速完成配置
 */
@Composable
private fun SetupAssistantCard(
    taskCount: Int,
    rewardCount: Int,
    wishCount: Int,
    scheduleCount: Int,
    onLoadRewards: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val strings = LocalStrings.current.parentHome
    val allConfigured = taskCount > 0 && rewardCount > 0 && wishCount > 0 && scheduleCount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.setupAssistantTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (allConfigured) {
                Text(strings.setupAllDoneNote,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            } else {
                Text(strings.setupIntroNote,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)

                SetupRow(ok = taskCount > 0, okText = strings.setupTasksConfigured(taskCount), missingText = strings.setupTasksMissing,
                    actionLabel = strings.setupTasksAction, onClick = { onNavigate(Screen.ParentTaskManage.route) })

                if (rewardCount > 0) {
                    SetupRow(ok = true, okText = strings.setupRewardsConfigured(rewardCount), missingText = "", actionLabel = "", onClick = {})
                } else {
                    SetupRow(ok = false, okText = "", missingText = strings.setupRewardsMissing, actionLabel = strings.setupRewardsAction, onClick = onLoadRewards)
                }

                SetupRow(ok = wishCount > 0, okText = strings.setupWishConfigured(wishCount), missingText = strings.setupWishMissing,
                    actionLabel = strings.setupWishAction, onClick = { onNavigate(Screen.ParentWishGoalManage.route) })

                SetupRow(ok = scheduleCount > 0, okText = strings.setupScheduleConfigured(scheduleCount), missingText = strings.setupScheduleMissing,
                    actionLabel = strings.setupScheduleAction, onClick = { onNavigate(Screen.ParentScheduleManage.route) })
            }
        }
    }
}

@Composable
private fun SetupRow(ok: Boolean, okText: String, missingText: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (ok) okText else missingText,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        if (!ok && actionLabel.isNotEmpty()) {
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HomeMenuCard(
    title: String,
    subtitle: String,
    badge: Int = 0,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (badge > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${badge}", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

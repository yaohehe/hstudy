package com.hstudy.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.mistake.SpacedRepetition
import com.hstudy.app.ui.navigation.Screen
import kotlinx.coroutines.launch

/**
 * 家长首页（指令 §3.3.5：家长驾驶舱，三层信息架构，告别超长列表）。
 *
 * v3.3 W4 重构（RA7：只动视觉层与布局结构，业务回调/导航/写库入口全部保留）：
 * - **第 1 层 紧急待办区**：待审积分或待审证明 >0 时置顶警示卡（counts 走本页既有
 *   pointApprovalRepository.getPendingCount / habitRepository.getPendingProofCount 流），
 *   "立即处理"直达审批页；证明待审仍保留直达证明页的既有按钮（15 卡功能 1:1 映射，不删）。
 * - **第 2 层 Daily Pulse**：删除硬编码浅蓝渐变色值（旧 Color 十六进制三件套），改 ElevatedCard +
 *   2x2 宫格：今日打卡率（completedIds/tasks）、连续天数（getCurrentStreak）、
 *   今日专注分钟（focusRepository.getTotalFocusMinutesForDate，模板复用 ParentDashboardStrings）、
 *   待复习错题（allMistakes × SpacedRepetition.isDueForReview，全部既有 API，不新增查询）。
 * - **第 3 层 分组功能台**：原 15 张纵向菜单卡 → 日常任务与激励 2x2 + 学情追踪与辅导 2x2 +
 *   高级与设置网格（含指令未点名的心愿目标/积分审核/周报总览，导航目标一个不少）。
 * - **v3.3.1 D-04（指令 §5.1）**：高级与设置删除 horizontalScroll 单行长滑，改断点自适应纵向网格
 *   （Compact/Medium 两列、Expanded 四列，随主页面纵向滚动）；Daily Pulse 同步 Expanded 4×1。
 *   只动布局层：功能入口 1:1、pendingApprovalCount 徽章语义、写库/导航回调零改动。
 */
@Composable
fun ParentHomeScreen(
    onNavigate: (String) -> Unit,
    onSwitchRole: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.parentHome
    val common = LocalStrings.current.common
    val dashboardCopy = LocalStrings.current.parentDashboard
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())
    val pendingProofCount by app.habitRepository.getPendingProofCount().collectAsState(initial = 0)
    val pendingApprovalCount by app.pointApprovalRepository.getPendingCount().collectAsState(initial = 0)
    val allMistakes by app.mistakeRepository.getAllMistakes().collectAsState(initial = emptyList())
    val masteredCount by app.mistakeRepository.getMasteredCount().collectAsState(initial = 0)
    // Daily Pulse 专注时长：既有 FocusRepository 当日分钟流（ParentDashboard 同源，不新增查询）
    val todayFocusMinutes by app.focusRepository.getTotalFocusMinutesForDate(today).collectAsState(initial = 0)
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

    val todayCompleted = completedIds.size
    val todayTotal = tasks.size
    val completionRate = if (todayTotal > 0) (todayCompleted * 100 / todayTotal) else 0
    val unmasteredMistakes = allMistakes.size - masteredCount
    // 待复习错题：与 ChildHome 同一到期判定（纯内存过滤既有 allMistakes，不新增 DB 查询）
    val dueMistakeCount = remember(allMistakes) { allMistakes.count { SpacedRepetition.isDueForReview(it) } }
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

                // ===== 第 1 层：紧急待办处理区（指令 §3.3.5-1）=====
                if (pendingApprovalCount > 0 || pendingProofCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(strings.urgentBarTitle, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(
                                        when {
                                            pendingApprovalCount > 0 && pendingProofCount > 0 ->
                                                strings.urgentTodoBoth(pendingApprovalCount, pendingProofCount)
                                            pendingApprovalCount > 0 -> strings.urgentTodoApprovals(pendingApprovalCount)
                                            else -> strings.urgentTodoProofs(pendingProofCount)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                // 一键直达审批页（既有路由，审批闭环入口不变）
                                Button(
                                    onClick = { onNavigate(Screen.ParentApproval.route) },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) { Text(strings.urgentProcessButton) }
                            }
                            // 证明待审保留既有直达入口（pendingProofsButton 原文案不动，功能 1:1）
                            if (pendingProofCount > 0) {
                                OutlinedButton(
                                    onClick = { onNavigate(Screen.ParentProofReview.route) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(strings.pendingProofsButton(pendingProofCount))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 第 2 层：今日学情概览 Daily Pulse（指令 §3.3.5-2，硬编码渐变已删除）=====
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.childTodayOverviewTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        // D-04 顺带上轮 P2-3：宫格列数随断点自适应——Expanded 展开 4×1，
                        // Compact/Medium 保持原 2×2（chunked(2) 的行序与旧两条 Row 逐格一致）。
                        val pulseColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) 4 else 2
                        listOf(
                            Triple(strings.overviewCompletionRate, "$completionRate%", "$todayCompleted/$todayTotal"),
                            Triple(strings.overviewStreak, strings.streakDaysValue(streak), null),
                            // 分钟模板跨表复用 ParentDashboardStrings.focusMinutesValue（单位进实参位）
                            Triple(strings.overviewFocusDuration, dashboardCopy.focusMinutesValue(todayFocusMinutes, common.minuteLabel), null),
                            Triple(strings.overviewMistakesDue, "$dueMistakeCount", null),
                        ).chunked(pulseColumns).forEach { rowCells ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                rowCells.forEach { (label, value, note) ->
                                    PulseStatCell(
                                        label = label,
                                        value = value,
                                        note = note,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        if (completionRate == 100 && todayTotal > 0) {
                            Text(strings.todayAllDoneBanner, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (unmasteredMistakes > 5) {
                            Text(strings.unmasteredMistakesNote(unmasteredMistakes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            onClick = { onNavigate(Screen.ParentSettings.route) },
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

                // ===== 第 3 层：分组功能控制台（指令 §3.3.5-3；15 个旧菜单卡 1:1 映射，全部保留）=====

                // 分组 1：日常任务与激励（2x2）
                GroupHeader(strings.groupDailyTitle)
                ControlTileRow(
                    listOf(
                        ControlSpec(strings.menuTaskManageTitle, strings.menuTaskManageSubtitle, Icons.Filled.Task, Screen.ParentTaskManage.route, 0),
                        ControlSpec(strings.menuAssignTaskTitle, strings.menuAssignTaskSubtitle, Icons.Filled.Assignment, Screen.ParentAssignTask.route, 0),
                    ),
                    onNavigate = onNavigate
                )
                ControlTileRow(
                    listOf(
                        ControlSpec(strings.menuPointsTitle, strings.menuPointsSubtitle, Icons.Filled.Stars, Screen.ParentPoints.route, 0),
                        ControlSpec(strings.menuRewardStoreTitle, strings.menuRewardStoreSubtitle, Icons.Filled.CardGiftcard, Screen.ParentRewardManage.route, 0),
                    ),
                    onNavigate = onNavigate
                )

                // 分组 2：学情追踪与辅导（2x2；"作息管理"= 每日日程入口）
                GroupHeader(strings.groupStudyTitle)
                ControlTileRow(
                    listOf(
                        ControlSpec(strings.menuMistakeAnalysisTitle, strings.menuMistakeAnalysisSubtitle, Icons.Filled.MenuBook, Screen.ParentMistakeReview.route, 0),
                        ControlSpec(strings.menuGrowthReportTitle, strings.menuGrowthReportSubtitle, Icons.Filled.EmojiEvents, Screen.ParentGrowthReport.route, 0),
                    ),
                    onNavigate = onNavigate
                )
                ControlTileRow(
                    listOf(
                        ControlSpec(strings.menuCoachingTitle, strings.menuCoachingSubtitle, Icons.Filled.Psychology, Screen.ParentTutoringAdvice.route, 0),
                        ControlSpec(strings.menuScheduleTitle, strings.menuScheduleSubtitle, Icons.Filled.Schedule, Screen.ParentScheduleManage.route, 0),
                    ),
                    onNavigate = onNavigate
                )

                // 分组 3：高级与设置（指令未点名的 心愿目标/积分审核/周报总览 也归入此条，不删功能）
                GroupHeader(strings.groupAdvancedTitle)
                val stripItems = listOf(
                    ControlSpec(strings.menuApprovalTitle, strings.menuApprovalSubtitle, Icons.Filled.Approval, Screen.ParentApproval.route, pendingApprovalCount),
                    ControlSpec(strings.menuProofTitle, strings.menuProofSubtitle, Icons.Filled.Verified, Screen.ParentProofReview.route, pendingProofCount),
                    ControlSpec(strings.menuWishGoalTitle, strings.menuWishGoalSubtitle, Icons.Filled.Star, Screen.ParentWishGoalManage.route, 0),
                    ControlSpec(strings.menuTemplateTitle, strings.menuTemplateSubtitle, Icons.Filled.AutoAwesome, Screen.ParentTaskTemplate.route, 0),
                    ControlSpec(strings.menuDashboardTitle, strings.menuDashboardSubtitle, Icons.Filled.Dashboard, Screen.ParentDashboard.route, 0),
                    ControlSpec(strings.menuWeeklyReportTitle, strings.menuWeeklyReportSubtitle, Icons.Filled.CalendarViewWeek, Screen.ParentWeeklyReport.route, 0),
                    ControlSpec(strings.menuSettingsTitle, strings.menuSettingsSubtitle, Icons.Filled.Settings, Screen.ParentSettings.route, 0),
                )
                // D-04（指令 §5.1）：删除 horizontalScroll 单行长滑——Compact/Medium 走与上方
                // 分组同型的纵向两列网格（chunked(2)，随本页纵向滚动），Expanded 走 4 列规整网格；
                // 末行不满时由占位 Spacer 保列宽，功能入口与 pendingApprovalCount 徽章 1:1 不变。
                val stripColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) 4 else 2
                stripItems.chunked(stripColumns).forEach { rowSpecs ->
                    ControlTileRow(rowSpecs, onNavigate = onNavigate, columns = stripColumns)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** 分组功能台的渲染模型（仅进 Text/Icon/onClick，不落库、不参与判等——ParentHomeStrings KDoc 同口径） */
private data class ControlSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val badge: Int
)

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * 分组功能台的一行小卡（weight 均分列宽）。
 *
 * D-04（指令 §5.1）：`columns` 显式给出该网格的列数，末行不满时用**透明占位 Spacer**
 * 补足列宽（与真实卡同 weight），保证 7 项在 Expanded 4 列/窄屏 2 列下都对齐成规整栅格。
 */
@Composable
private fun ControlTileRow(
    specs: List<ControlSpec>,
    onNavigate: (String) -> Unit,
    columns: Int = specs.size
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        specs.forEach { spec ->
            ControlTile(spec, onNavigate = onNavigate, modifier = Modifier.weight(1f))
        }
        repeat(columns - specs.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 功能台小卡（指令 §3.3.5-2 卡片标准）：OutlinedCard + 28dp 图标置于 primary 浅色圆形容器，
 * 待办角标沿用旧 Badge 语义（证明/审核计数）。
 */
@Composable
private fun ControlTile(spec: ControlSpec, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = { onNavigate(spec.route) },
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(spec.icon, contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (spec.badge > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("${spec.badge}", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(spec.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Daily Pulse 宫格单元：surfaceContainerHigh 小面板（数值 displayLarge 梯 headlineMedium + 注 + 标签） */
@Composable
private fun PulseStatCell(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

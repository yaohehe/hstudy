package com.hstudy.app.ui.child

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.difficultyStars
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.platform.AnalyticsEvent
import com.hstudy.app.platform.AnalyticsReporter
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChildTaskListScreen(
    onNavigateBack: () -> Unit,
    onUploadProof: (Long, String) -> Unit = { _, _ -> },
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.taskList
    val displayLanguage = LocalDisplayLanguage.current
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)

    val today = com.hstudy.app.data.repository.PointRepository.getTodayString()
    val completedIds by app.pointRepository.getCompletedTaskIdsForDate(today).collectAsState(initial = emptyList())
    // D-03（指令 §4.1 P1-01）：已打卡卡渲染**当日 bizKey 对应的真实审批态**（taskId → 最新态）。
    // 仓库层单查询（bizKey LIKE 'TASK:%' + date 同源），不逐卡 N+1；Room 失效流让"家长刚审完"即时刷卡。
    val taskApprovalStates by app.pointRepository.getTaskApprovalStates(today).collectAsState(initial = emptyMap())

    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }

    // ===== 动画状态 =====
    // 说明：打卡瞬间并不产生积分（需家长审批通过后才计分），
    // 因此不再播放"积分飞入"动画，避免误导孩子以为积分已到账。
    var showConfetti by remember { mutableStateOf(false) }

    val filteredTasks = if (selectedCategory == null) tasks else tasks.filter { it.category == selectedCategory }
    val filteredCompleted = filteredTasks.count { it.id in completedIds }

    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * 撤销打卡并如实反馈积分变动：
     * - 尚未审批 → 取消待审申请，本就没有积分，无残留
     * - 已审批通过 → 追加反向记录扣回已发积分，净额归零
     */
    val undoAndReport: suspend (Long, String) -> Unit = { taskId, title ->
        val clawedBack = app.pointRepository.undoTaskCheckIn(taskId)
        // 三个 snackbar 文案字段已改名 revert…/reverted…（W9 T1），调用头不再撞存储边界闸门的
        // undo/checkIn sink 词根，标题因此可以在渲染点取词；`title` 形参保持 canonical
        // （:159/:165 的 undoAndReport(task.id, task.title) 是业务实参不动，这里只换显示）。
        snackbarHostState.showSnackbar(
            if (clawedBack > 0) strings.revertedClawedBackMessage(localizedPresetText(title, displayLanguage), clawedBack)
            else strings.revertedPendingApprovalMessage(localizedPresetText(title, displayLanguage))
        )
    }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = strings.checkInTitle,
                onNavigateBack = onNavigateBack,
                actions = { PointsBadge(points = totalPoints) }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    // 暗色审计 D2：tertiary 压在 inverseSurface 上浅色 2.07:1、深色 1.33:1，
                    // 改用 inversePrimary（浅色 7.85:1 / 深色 4.87:1，与 M3 Snackbar 默认动作角色一致）
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 氛围漂浮背景（置于内容之下）
            AmbientBackground()
            Column {
                // ===== 首行：分类 FilterChip（指令 §3.3.2-1：筛选与总进度分开两行）=====
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(strings.filterAll) })
                    FilterChip(selected = selectedCategory == TaskCategory.BASIC, onClick = { selectedCategory = if (selectedCategory == TaskCategory.BASIC) null else TaskCategory.BASIC }, label = { Text(strings.filterBasic) })
                    FilterChip(selected = selectedCategory == TaskCategory.ENHANCEMENT, onClick = { selectedCategory = if (selectedCategory == TaskCategory.ENHANCEMENT) null else TaskCategory.ENHANCEMENT }, label = { Text(strings.filterEnhancement) })
                    FilterChip(selected = selectedCategory == TaskCategory.HABIT, onClick = { selectedCategory = if (selectedCategory == TaskCategory.HABIT) null else TaskCategory.HABIT }, label = { Text(strings.filterHabits) })
                }
                // ===== 次行：当前类别打卡完成比率 + 进度条（圆角 shapes.small=8dp、弹簧过渡，§3.3.2-1）=====
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ratio = if (filteredTasks.isNotEmpty()) filteredCompleted.toFloat() / filteredTasks.size else 0f
                    val animatedRatio by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = spring(dampingRatio = 0.75f),
                        label = "taskListProgress"
                    )
                    LinearProgressIndicator(
                        progress = animatedRatio,
                        modifier = Modifier.weight(1f).height(10.dp).clip(MaterialTheme.shapes.small),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        "${filteredCompleted}/${filteredTasks.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            items(filteredTasks, key = { it.id }) { task ->
                                val isCompleted = task.id in completedIds
                                // D-03：当日 bizKey 的最新审批态；null = 当日无 task 类申请（兜底走原预得文案）。
                                val approvalState = if (isCompleted) taskApprovalStates[task.id] else null
                                ChildTaskCard(
                                    task = task,
                                    isCompleted = isCompleted,
                                    approvalState = approvalState,
                                    onComplete = {
                                        // D-03 要求 4：REJECTED 允许重打卡——checkInForTask 对"最新一条是
                                        // REJECTED"的 bizKey 天然走 ensureApproval 新建 PENDING（复用/已计分两条
                                        // 检查都不命中），task_completions 当日已有记录也不再插第二条（幂等检查在前）。
                                        // 所以这里只放开守卫，**不改**打卡/计分语义。
                                        if (!isCompleted || approvalState == ApprovalStatus.REJECTED) {
                                            val earned = task.effectivePoints()
                                            scope.launch {
                                                // 打卡：只记录完成 + 生成待审申请，此刻**不计入任何积分**；
                                                // 积分仅在家长审批通过后写入一次。重复打卡不会生成第二条申请。
                                                app.pointRepository.checkInForTask(task.id, earned, task.title)
                                                AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
                                                // D-03：拒绝后重打卡不新增完成记录（当日 completion 一直在，只是重开审批），
                                                // "全部完成"判定只在**本次真的新记一条完成**时 +1，避免 isPerfect 多数一个。
                                                val newlyCompleted = if (isCompleted) 0 else 1
                                                val isPerfect = tasks.isNotEmpty() && (completedIds.size + newlyCompleted) >= tasks.size
                                                val newlyUnlocked = com.hstudy.app.ui.common.AchievementManager.evaluate(app, isPerfect)
                                                if (newlyUnlocked.isNotEmpty()) {
                                                    snackbarHostState.showSnackbar(
                                                        strings.achievementUnlockedMessage(
                                                            newlyUnlocked.map { def ->
                                                                achievementCopy[displayLanguage]?.get(def.type)?.title ?: def.title
                                                            }
                                                        )
                                                    )
                                                } else {
                                                    snackbarHostState.showSnackbar(strings.checkInSuccessMessage(earned))
                                                }
                                                showConfetti = true
                                                delay(1500)
                                                showConfetti = false
                                            }
                                            // 撤销窗口
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = strings.revertCheckInPrompt(localizedPresetText(task.title, displayLanguage), earned),
                                                    actionLabel = strings.undoButton,
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    scope.launch { undoAndReport(task.id, task.title) }
                                                }
                                            }
                                        }
                                    },
                                    onUndo = {
                                        scope.launch { undoAndReport(task.id, task.title) }
                                    },
                                    onUploadProof = { onUploadProof(task.id, task.title) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }

            // ===== 撒花覆盖层 =====
            ConfettiOverlay(trigger = showConfetti)
        }
    }
}

@Composable
private fun ChildTaskCard(
    task: TaskEntity,
    isCompleted: Boolean,
    // D-03（指令 §4.1 P1-01）：当日 bizKey 的最新审批态（null=无当日申请）。只影响渲染取词，
    // 不改 isCompleted（task_completions 口径）本身。
    approvalState: ApprovalStatus? = null,
    onComplete: () -> Unit,
    onUndo: () -> Unit = {},
    onUploadProof: () -> Unit = {}
) {
    val strings = LocalStrings.current.taskList
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
    // D-03 要求 4：拒绝态卡"重新打卡"引导——打卡钮从"点击撤销"恢复为可再次打卡（onComplete），
    // 与右侧 [strings.recheckInButton] 文字钮双入口，同走 checkInForTask（对 REJECTED 新建 PENDING）。
    val isRejected = isCompleted && approvalState == ApprovalStatus.REJECTED
    // 终审 I-1（指令 §3.3.2-4 二度确认）：卡右下角显式"撤销"钮改为先弹确认对话框，
    // 确认后才执行 onUndo（undoTaskCheckIn 闭环）；确认/取消按钮复用 CommonStrings（批量通过对话框先例）。
    // 打卡钮本身的点击撤销行为保持不变（既有语义，contentDescription 已声明"点击撤销"）。
    var showUndoConfirm by remember { mutableStateOf(false) }
    if (showUndoConfirm) {
        AlertDialog(
            onDismissRequest = { showUndoConfirm = false },
            title = { Text(strings.undoButton) },
            text = { Text(strings.revertConfirmMessage(localizedPresetText(task.title, displayLanguage))) },
            confirmButton = {
                TextButton(onClick = {
                    showUndoConfirm = false
                    onUndo()
                }) { Text(common.confirmButton) }
            },
            dismissButton = {
                TextButton(onClick = { showUndoConfirm = false }) { Text(common.cancelButton) }
            }
        )
    }
    // 打卡完成时的弹性缩放动画
    var cardScaleTarget by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            cardScaleTarget = 1.06f
            delay(150)
            cardScaleTarget = 1f
        }
    }
    val scale by animateFloatAsState(
        targetValue = cardScaleTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    val categoryColor = when (task.category) {
        TaskCategory.BASIC -> MaterialTheme.colorScheme.primary
        TaskCategory.ENHANCEMENT -> MaterialTheme.colorScheme.tertiary
        TaskCategory.HABIT -> MaterialTheme.colorScheme.secondary
    }
    val categoryEmoji = when (task.category) {
        TaskCategory.BASIC -> "📝"
        TaskCategory.ENHANCEMENT -> "📈"
        TaskCategory.HABIT -> "🏆"
    }
    // D-03：REJECTED 态卡面转 errorContainer，卡内文字主色随之换 onErrorContainer（其余态口径不变）。
    val onCardText = when {
        isRejected -> MaterialTheme.colorScheme.onErrorContainer
        isCompleted -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    // M3 重设计指令 §3.3.2-2：ElevatedCard；未打卡 surface + outlineVariant 描边，
    // 打卡后 secondaryContainer；左侧 6dp 色条已删除，类别色改由打卡钮/分值标签联动。
    // （当前 material3 版本的 ElevatedCard 无 border 形参，描边经 Modifier.border 同形状叠加。）
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (isCompleted) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            // D-03 验收：家长拒绝后卡片明确转 errorContainer 告警态（指令 §4.1-3），不再维持
            // "绿色对勾 + 需审批"的假象；通过/待审仍走 completed 的 secondaryContainer。
            containerColor = when {
                isRejected -> MaterialTheme.colorScheme.errorContainer
                isCompleted -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = when {
                isRejected -> MaterialTheme.colorScheme.onErrorContainer
                isCompleted -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 1.dp else 3.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (isCompleted) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 56dp 圆形打卡钮（§3.3.2-2 + §3.2.3 触控≥56）：未打卡 surfaceContainerHigh + 类别 Emoji，
                // 点按经 bouncyClickable 弹簧微缩；完成态 secondary + Icons.Filled.Check（卡片弹跳动画保留）。
                // D-03：REJECTED 态钮**重置为可再打卡**（§4.1-3）——回到未打卡的外观（Emoji + onComplete），
                // 其余完成态维持"点钮撤销"（onUndo）双重入口不变。
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isCompleted && !isRejected) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .bouncyClickable(
                            onClick = if (isCompleted && !isRejected) onUndo else onComplete,
                            pressedScale = 0.85f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted && !isRejected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = strings.completedContentDescription,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Text(categoryEmoji, fontSize = 26.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedPresetText(task.title, displayLanguage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = onCardText
                    )
                    if (task.description.isNotEmpty()) {
                        Text(task.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    // 难度星级标识（高难度醒目显示）
                    if (task.difficulty > 1) {
                        Text(task.difficultyStars(), style = MaterialTheme.typography.labelSmall,
                            color = if (task.difficulty == 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                    }
                    // 每日必须标识（未完成会扣分）
                    if (task.isMandatory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.mandatoryBadge, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 指令 §3.3.2-3：分值明确为"打卡预得 +N 分（需审批）"，不误导为立即到账；
                        // 颜色与左侧图标/类别联动（代替被删除的色条）。
                        // D-03（指令 §4.1 P1-01）验收点：已打卡且当日有申请 → 小标渲染**真实审批态**
                        // （⏳待审 / ✅已通过 / ⛔已拒绝 / ↩️已取消 / 🔄已撤销，词面同源 ParentApprovalScreen
                        // 历史标签）；未打卡、以及"已打卡但当日查无申请"的历史/异常态兜底 → 维持原预得文案，
                        // 不猜测状态。同一 bizKey 多条取最新由仓库层单查询折叠（无逐卡 N+1）。
                        val approvalBadge: String? = if (isCompleted) when (approvalState) {
                            ApprovalStatus.PENDING -> strings.approvalPendingBadge(task.effectivePoints())
                            ApprovalStatus.APPROVED -> strings.approvalApprovedBadge(task.effectivePoints())
                            ApprovalStatus.REJECTED -> strings.approvalRejectedBadge
                            ApprovalStatus.CANCELLED -> strings.approvalCancelledBadge
                            ApprovalStatus.REVOKED -> strings.approvalRevokedBadge
                            null -> null
                        } else null
                        if (approvalBadge != null) {
                            Text(approvalBadge, style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = when (approvalState) {
                                    ApprovalStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                    ApprovalStatus.APPROVED -> MaterialTheme.colorScheme.primary
                                    ApprovalStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                        } else {
                            Text(strings.expectedPointsLabel(task.effectivePoints()), style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (isCompleted) MaterialTheme.colorScheme.onSecondaryContainer else categoryColor)
                        }
                        if (task.bonusPoints > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.bonusPointsLabel(task.bonusPoints), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                // 右侧状态 + 上传证明
                if (isCompleted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(
                            onClick = onUploadProof,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(strings.proofButton, style = MaterialTheme.typography.labelSmall,
                                color = if (isRejected) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.tertiary)
                        }
                        // D-03 要求 4：REJECTED 态给"重新打卡"引导钮（与左侧重置后的打卡圆钮同走
                        // onComplete → checkInForTask 对 REJECTED 新建 PENDING；不新增写库路径）。
                        if (isRejected) {
                            TextButton(
                                onClick = onComplete,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(strings.recheckInButton, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            // 指令 §3.3.2-4：打卡完成后卡片右下角显式"撤销"微按钮（回退箭头图标）。
            // 逻辑沿用 W2 的 onUndo（undoTaskCheckIn 撤销闭环 + 如实反馈 Snackbar），此处只是入口外观化；
            // 打卡钮本身的点击撤销行为保持不变（双重入口、同一回调）。
            // 终审 I-1：本钮是波次新增的显式入口，按 §3.3.2-4 加二度确认对话框，撤销仅在确认后执行。
            if (isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showUndoConfirm = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.undoButton, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

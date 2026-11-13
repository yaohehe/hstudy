package com.hstudy.app.ui.child

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.difficultyStars
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.achievementCopy
import com.hstudy.app.i18n.localizedPresetText
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
                    actionColor = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 氛围漂浮背景（置于内容之下）
            AmbientBackground()
            Column {
                // 分类筛选 + 今日统计
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(strings.filterAll) })
                    FilterChip(selected = selectedCategory == TaskCategory.BASIC, onClick = { selectedCategory = if (selectedCategory == TaskCategory.BASIC) null else TaskCategory.BASIC }, label = { Text(strings.filterBasic) })
                    FilterChip(selected = selectedCategory == TaskCategory.ENHANCEMENT, onClick = { selectedCategory = if (selectedCategory == TaskCategory.ENHANCEMENT) null else TaskCategory.ENHANCEMENT }, label = { Text(strings.filterEnhancement) })
                    FilterChip(selected = selectedCategory == TaskCategory.HABIT, onClick = { selectedCategory = if (selectedCategory == TaskCategory.HABIT) null else TaskCategory.HABIT }, label = { Text(strings.filterHabits) })
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${filteredCompleted}/${filteredTasks.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            items(filteredTasks, key = { it.id }) { task ->
                                val isCompleted = task.id in completedIds
                                ChildTaskCard(
                                    task = task,
                                    isCompleted = isCompleted,
                                    onComplete = {
                                        if (!isCompleted) {
                                            val earned = task.effectivePoints()
                                            scope.launch {
                                                // 打卡：只记录完成 + 生成待审申请，此刻**不计入任何积分**；
                                                // 积分仅在家长审批通过后写入一次。重复打卡不会生成第二条申请。
                                                app.pointRepository.checkInForTask(task.id, earned, task.title)
                                                val isPerfect = tasks.isNotEmpty() && (completedIds.size + 1) >= tasks.size
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
    onComplete: () -> Unit,
    onUndo: () -> Unit = {},
    onUploadProof: () -> Unit = {}
) {
    val strings = LocalStrings.current.taskList
    val displayLanguage = LocalDisplayLanguage.current
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

    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 1.dp else 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧类别色条
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(88.dp)
                    .background(
                        color = if (isCompleted) MaterialTheme.colorScheme.secondary else categoryColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
            )

            Row(
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 打卡圆形按钮（更大更有触感）
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = if (isCompleted) MaterialTheme.colorScheme.secondary else categoryColor.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .bouncyClickable(
                            onClick = if (isCompleted) onUndo else onComplete,
                            pressedScale = 0.85f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = strings.completedContentDescription,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Text(categoryEmoji, fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedPresetText(task.title, displayLanguage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
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
                        Text(strings.pointsEarnedLabel(task.effectivePoints()), style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = categoryColor)
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
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

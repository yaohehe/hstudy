package com.hstudy.app.ui.wish

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.WishGoalEntity
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

/**
 * 儿童端 - 心愿存钱罐：展示进行中的心愿目标，存入积分，达成时庆祝
 */
@Composable
fun ChildWishGoalScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val goals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())

    val activeGoals = goals.filter { it.isActive }
    val completedGoals = goals.filter { !it.isActive }

    // 最近日均收入（用于预估心愿达成天数）
    var dailyAvg by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) { dailyAvg = app.pointRepository.getRecentDailyAvgEarned() }

    // 存入对话框目标
    var depositGoal by remember { mutableStateOf<WishGoalEntity?>(null) }
    // 庆祝动画
    var showConfetti by remember { mutableStateOf(false) }
    var celebrationMsg by remember { mutableStateOf("") }
    var showCelebration by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HStudyTopBar(title = "🐷 心愿存钱罐", onNavigateBack = onNavigateBack, actions = { PointsBadge(points = totalPoints) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AmbientBackground()
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 说明卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 什么是心愿存钱罐？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("设定一个大目标，把积分一点点存进去，攒够就能兑换啦！学会耐心等待，收获更大的惊喜～",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (activeGoals.isEmpty() && completedGoals.isEmpty()) {
                        // 空状态
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🐷", fontSize = 56.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("还没有心愿目标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("请爸爸妈妈帮你设定一个心愿吧！", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 进行中的心愿
                    activeGoals.forEachIndexed { index, goal ->
                        WishGoalCard(
                            goal = goal,
                            isPrimary = index == 0,
                            currentPoints = totalPoints,
                            dailyAvg = dailyAvg,
                            onDeposit = { depositGoal = goal }
                        )
                    }

                    // 已达成的心愿
                    if (completedGoals.isNotEmpty()) {
                        Text("🎉 已达成的心愿", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        completedGoals.forEach { goal ->
                            CompletedGoalCard(goal)
                        }
                    }
                }
            }
        }
    }

    // 存入积分对话框
    depositGoal?.let { goal ->
        DepositDialog(
            goal = goal,
            currentPoints = totalPoints,
            onDismiss = { depositGoal = null },
            onConfirm = { amount ->
                scope.launch {
                    val success = app.wishGoalRepository.depositPoints(goal.id, amount)
                    depositGoal = null
                    if (success) {
                        // 检查是否达成
                        val updated = app.wishGoalRepository.getGoalById(goal.id)
                        if (updated != null && !updated.isActive) {
                            celebrationMsg = "🎉 心愿达成！\n${goal.name}"
                            showConfetti = true
                            showCelebration = true
                        } else {
                            snackbarHostState.showSnackbar("✅ 已存入 $amount 积分到「${goal.name}」")
                        }
                    } else {
                        snackbarHostState.showSnackbar("积分不足，无法存入")
                    }
                }
            }
        )
    }

    // 撒花 + 庆祝
    ConfettiOverlay(trigger = showConfetti)
    CelebrationPopup(
        show = showCelebration,
        message = celebrationMsg,
        onDismiss = { showCelebration = false; showConfetti = false }
    )
}

@Composable
private fun WishGoalCard(
    goal: WishGoalEntity,
    isPrimary: Boolean,
    currentPoints: Int,
    dailyAvg: Double,
    onDeposit: () -> Unit
) {
    val progress = (goal.savedPoints.toFloat() / goal.targetPoints).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = tween(800), label = "wishProgress"
    )
    val percent = (progress * 100).toInt()
    val remaining = (goal.targetPoints - goal.savedPoints).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 圆形图标
                Box(
                    modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(goal.iconEmoji, fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isPrimary) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("👑主目标", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (goal.description.isNotEmpty()) {
                        Text(goal.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }

            // 进度条 + 里程碑刻度（25/50/75%，把大目标拆成小里程碑）
            Box(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${goal.savedPoints} / ${goal.targetPoints} 分", style = MaterialTheme.typography.labelMedium)
                Text("$percent%", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("还差 $remaining 分达成心愿", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 里程碑激励（阶段性鼓励，让攒积分过程更有达成感）
            val milestoneMsg = when {
                percent >= 75 -> "🔥 已完成75%，胜利在望，冲刺！"
                percent >= 50 -> "🌟 已经过半啦！继续保持，心愿越来越近！"
                percent >= 25 -> "💪 已完成25%，好的开始是成功的一半！"
                percent > 0 -> "🌱 已迈出第一步，加油攒积分！"
                else -> ""
            }
            if (milestoneMsg.isNotEmpty() && remaining > 0) {
                Text(milestoneMsg, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
            }

            // 预计达成天数（根据最近日均收入估算，让目标更具体可期）
            if (remaining > 0 && dailyAvg > 0) {
                val estDays = kotlin.math.ceil(remaining / dailyAvg).toInt()
                Text("📅 照最近的速度，大约再攒 $estDays 天就能实现啦！",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary)
            } else if (remaining > 0) {
                Text("📅 开始赚积分后，这里会告诉你大概几天能实现～",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 存入按钮
            Button(
                onClick = onDeposit,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentPoints > 0
            ) {
                Text("💰 存入积分（当前可用 $currentPoints 分）")
            }
        }
    }
}

@Composable
private fun CompletedGoalCard(goal: WishGoalEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(goal.iconEmoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text("已达成 · 共存入 ${goal.savedPoints} 分", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("🎊", fontSize = 24.sp)
        }
    }
}

@Composable
private fun DepositDialog(
    goal: WishGoalEntity,
    currentPoints: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val remaining = (goal.targetPoints - goal.savedPoints).coerceAtLeast(0)
    val amount = amountText.toIntOrNull() ?: 0
    val valid = amount in 1..currentPoints

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💰 存入积分到「${goal.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前可用积分：$currentPoints 分\n还差 $remaining 分达成心愿",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("存入积分数量") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 快捷金额
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, remaining).distinct().filter { it > 0 }.take(4).forEach { quick ->
                        val capped = quick.coerceAtMost(currentPoints)
                        if (capped > 0) {
                            FilterChip(
                                selected = amountText == capped.toString(),
                                onClick = { amountText = capped.toString() },
                                label = { Text(if (quick == remaining) "全部差额" else "$capped") }
                            )
                        }
                    }
                }
                if (amountText.isNotEmpty() && !valid) {
                    Text("积分不足或数量无效", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (valid) onConfirm(amount) }, enabled = valid) { Text("存入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

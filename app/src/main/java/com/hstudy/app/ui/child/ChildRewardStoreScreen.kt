package com.hstudy.app.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun ChildRewardStoreScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())

    var showExchangeDialog by remember { mutableStateOf<RewardEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // ===== 庆祝动画状态 =====
    var showConfetti by remember { mutableStateOf(false) }
    var celebrationMsg by remember { mutableStateOf("") }
    var showCelebration by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HStudyTopBar(title = "奖励商店", onNavigateBack = onNavigateBack, actions = { PointsBadge(points = totalPoints) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // 氛围漂浮背景（置于内容之下）
            AmbientBackground()
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // 激励横幅：告诉孩子现在能兑换几个、距离下一个目标还差多少，激发主动性
                    if (rewards.isNotEmpty()) {
                        item {
                            val affordableCount = rewards.count { totalPoints >= it.pointsCost }
                            val nextGoal = rewards.firstOrNull { totalPoints < it.pointsCost }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        if (affordableCount > 0) "🎉 你有 $totalPoints 分，现在可以兑换 $affordableCount 个奖励啦！"
                                        else "💪 你有 $totalPoints 分，完成任务赚积分来兑换吧！",
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                    )
                                    if (nextGoal != null) {
                                        val need = nextGoal.pointsCost - totalPoints
                                        Text("再攒 $need 分就能兑换「${nextGoal.iconEmoji} ${nextGoal.name}」啦～",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val progress = (totalPoints.toFloat() / nextGoal.pointsCost).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier.fillMaxWidth().height(8.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 按类别分组展示
                    val categoryOrder = listOf("娱乐", "美食", "出行", "特权", "实物", "社交", "其他")
                    val categoryEmoji = mapOf(
                        "娱乐" to "🎮", "美食" to "🍔", "出行" to "🚀",
                        "特权" to "👑", "实物" to "🎁", "社交" to "👫", "其他" to "✨"
                    )
                    val grouped = rewards.groupBy { it.category }

                    categoryOrder.forEach { cat ->
                        val catRewards = grouped[cat]
                        if (!catRewards.isNullOrEmpty()) {
                            // 分类标题
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                ) {
                                    Text(categoryEmoji[cat] ?: "✨", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${cat}奖励", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("(${catRewards.size})", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isTablet) {
                                // 平板：网格显示
                                val rows = catRewards.chunked(2)
                                items(rows) { rowRewards ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rowRewards.forEach { reward ->
                                            RewardStoreCard(
                                                reward = reward,
                                                currentPoints = totalPoints,
                                                onExchange = { showExchangeDialog = reward },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowRewards.size < 2) {
                                            repeat(2 - rowRewards.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            } else {
                                // 手机：横向滚动卡片
                                item {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(catRewards, key = { it.id }) { reward ->
                                            RewardStoreCard(
                                                reward = reward,
                                                currentPoints = totalPoints,
                                                onExchange = { showExchangeDialog = reward },
                                                modifier = Modifier.width(160.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 兑换历史
                    if (exchanges.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("我的兑换记录", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(exchanges.take(10)) { exchange ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎁", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exchange.note.ifEmpty { "已兑换" }, style = MaterialTheme.typography.bodyMedium)
                                        Text("-${exchange.pointsSpent}分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    if (rewards.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🎁", fontSize = 48.sp)
                                    Text("奖励商店还是空的", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("请爸爸妈妈到「奖励商店管理」里点一下“一键载入预设奖励”，就能出现好多奖品啦！",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 兑换确认对话框
    showExchangeDialog?.let { reward ->
        AlertDialog(
            onDismissRequest = { showExchangeDialog = null },
            title = { Text("确认兑换") },
            text = {
                Column {
                    Text("${reward.iconEmoji} ${reward.name}", style = MaterialTheme.typography.titleLarge)
                    if (reward.description.isNotEmpty()) {
                        Text(reward.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("需要 ${reward.pointsCost} 积分", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("当前余额：$totalPoints 积分", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (totalPoints >= reward.pointsCost) {
                            scope.launch {
                                app.rewardRepository.exchangeReward(reward.id, reward.pointsCost, reward.name)
                                app.pointRepository.spendPoints(reward.pointsCost, "兑换：${reward.name}")
                                // 触发庆祝动画
                                showConfetti = true
                                celebrationMsg = "🎉 ${reward.iconEmoji} ${reward.name}"
                                showCelebration = true
                                snackbarHostState.showSnackbar("🎉 兑换成功！${reward.name}")
                            }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("积分不足哦，继续加油！💪") }
                        }
                        showExchangeDialog = null
                    },
                    enabled = totalPoints >= reward.pointsCost
                ) { Text("确认兑换") }
            },
            dismissButton = {
                TextButton(onClick = { showExchangeDialog = null }) { Text("取消") }
            }
        )
    }

    // ===== 撒花 + 庆祝弹出 =====
    ConfettiOverlay(trigger = showConfetti)
    CelebrationPopup(
        show = showCelebration,
        message = celebrationMsg,
        onDismiss = {
            showCelebration = false
            showConfetti = false
        }
    )
}

@Composable
private fun RewardStoreCard(
    reward: RewardEntity,
    currentPoints: Int,
    onExchange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canAfford = currentPoints >= reward.pointsCost

    Card(
        modifier = modifier.bouncyClickable(onClick = { if (canAfford) onExchange() }, pressedScale = 0.96f),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (canAfford) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 圆形图标底座
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (canAfford) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(reward.iconEmoji, fontSize = 32.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(reward.name, style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1)
            if (reward.description.isNotEmpty()) {
                Text(reward.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("⭐ ${reward.pointsCost} 积分", style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onExchange,
                enabled = canAfford,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (canAfford) "兑换" else "还差 ${reward.pointsCost - currentPoints} 分")
            }
        }
    }
}

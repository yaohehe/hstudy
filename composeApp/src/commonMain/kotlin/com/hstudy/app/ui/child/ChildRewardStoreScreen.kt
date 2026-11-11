package com.hstudy.app.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun ChildRewardStoreScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val exchanges by app.rewardRepository.getAllExchanges().collectAsState(initial = emptyList())

    var showExchangeDialog by remember { mutableStateOf<RewardEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val strings = LocalStrings.current.childRewardStore
    val parentHome = LocalStrings.current.parentHome
    val common = LocalStrings.current.common
    val childHome = LocalStrings.current.childHome
    val displayLanguage = LocalDisplayLanguage.current

    // ===== 庆祝动画状态 =====
    var showConfetti by remember { mutableStateOf(false) }
    var celebrationMsg by remember { mutableStateOf("") }
    var showCelebration by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HStudyTopBar(title = childHome.entryRewardStoreTitle, onNavigateBack = onNavigateBack, actions = { PointsBadge(points = totalPoints) })
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
                                        if (affordableCount > 0) strings.bannerAffordable(totalPoints, affordableCount)
                                        else strings.bannerKeepEarning(totalPoints),
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                    )
                                    if (nextGoal != null) {
                                        val need = nextGoal.pointsCost - totalPoints
                                        Text(strings.nextGoalMessage(need, nextGoal.iconEmoji, localizedPresetText(nextGoal.name, displayLanguage)),
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
                                    Text(strings.categoryTitle(localizedPresetText(cat, displayLanguage)), style = MaterialTheme.typography.titleMedium,
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
                            Text(strings.historyTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(exchanges.take(10)) { exchange ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎁", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exchange.note.ifEmpty { strings.exchangedFallback }, style = MaterialTheme.typography.bodyMedium)
                                        Text(strings.spentValue(exchange.pointsSpent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                                    Text(parentHome.setupRewardsMissing, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(strings.emptyHint,
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
            title = { Text(strings.confirmExchange) },
            text = {
                Column {
                    Text("${reward.iconEmoji} ${localizedPresetText(reward.name, displayLanguage)}", style = MaterialTheme.typography.titleLarge)
                    if (reward.description.isNotEmpty()) {
                        Text(localizedPresetText(reward.description, displayLanguage), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(strings.costNeeded(reward.pointsCost), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(strings.currentBalance(totalPoints), style = MaterialTheme.typography.bodyMedium)
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
                                celebrationMsg = "🎉 ${reward.iconEmoji} ${localizedPresetText(reward.name, displayLanguage)}"
                                showCelebration = true
                                snackbarHostState.showSnackbar(strings.redeemSuccess(localizedPresetText(reward.name, displayLanguage)))
                            }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(strings.insufficientPoints) }
                        }
                        showExchangeDialog = null
                    },
                    enabled = totalPoints >= reward.pointsCost
                ) { Text(strings.confirmExchange) }
            },
            dismissButton = {
                TextButton(onClick = { showExchangeDialog = null }) { Text(common.cancelButton) }
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
    val strings = LocalStrings.current.childRewardStore
    val displayLanguage = LocalDisplayLanguage.current

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
            Text(localizedPresetText(reward.name, displayLanguage), style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1)
            if (reward.description.isNotEmpty()) {
                Text(localizedPresetText(reward.description, displayLanguage), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(strings.priceLabel(reward.pointsCost), style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onExchange,
                enabled = canAfford,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (canAfford) strings.redeemButton else strings.shortBy(reward.pointsCost - currentPoints))
            }
        }
    }
}

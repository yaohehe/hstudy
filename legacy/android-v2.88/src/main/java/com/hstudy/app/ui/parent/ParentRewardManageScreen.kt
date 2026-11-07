package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentRewardManageScreen(
    onNavigateBack: () -> Unit,
    onEditReward: (Long) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { HStudyTopBar(title = "奖励商店管理", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditReward(0L) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加奖励")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(rewards, key = { it.id }) { reward ->
                        RewardManageCard(reward = reward, onEdit = { onEditReward(reward.id) }, onDelete = {
                            scope.launch { app.rewardRepository.deleteReward(reward.id) }
                        })
                    }
                    if (rewards.isEmpty()) {
                        item {
                            // 空状态引导：一键载入 27 个预设奖励，家长无需逐个添加
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("🎁", style = MaterialTheme.typography.displaySmall)
                                    Text("奖励商店还是空的", style = MaterialTheme.typography.titleMedium)
                                    Text("点击下方按钮一键载入 27 个常见奖励（看电影、零食、玩耍、满足心愿等），之后再逐个调整积分即可。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = {
                                        scope.launch {
                                            app.rewardRepository.initDefaultRewards()
                                            snackbarHostState.showSnackbar("✅ 已载入预设奖励")
                                        }
                                    }) { Text("✨ 一键载入预设奖励") }
                                    Text("或点击右下角 + 自行添加", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RewardManageCard(
    reward: RewardEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(reward.iconEmoji, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(reward.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(reward.category, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (reward.description.isNotEmpty()) {
                    Text(reward.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${reward.pointsCost} 积分", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

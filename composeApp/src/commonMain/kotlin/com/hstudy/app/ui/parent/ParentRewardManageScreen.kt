package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

@Composable
fun ParentRewardManageScreen(
    onNavigateBack: () -> Unit,
    onEditReward: (Long) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val rewards by app.rewardRepository.getAllActiveRewards().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    val strings = LocalStrings.current.parentRewardManage
    val parentHome = LocalStrings.current.parentHome

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditReward(0L) }) {
                Icon(Icons.Filled.Add, contentDescription = strings.addContentDescription)
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
                                    Text(parentHome.setupRewardsMissing, style = MaterialTheme.typography.titleMedium)
                                    Text(strings.emptyHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = {
                                        scope.launch {
                                            app.rewardRepository.initDefaultRewards()
                                            snackbarHostState.showSnackbar(parentHome.presetRewardsLoadedMessage)
                                        }
                                    }) { Text(strings.loadPresetsButton) }
                                    Text(strings.addOwnHint, style = MaterialTheme.typography.labelSmall,
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
    val strings = LocalStrings.current.parentRewardManage
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(reward.iconEmoji, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localizedPresetText(reward.name, displayLanguage), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(localizedPresetText(reward.category, displayLanguage), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (reward.description.isNotEmpty()) {
                    Text(localizedPresetText(reward.description, displayLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(strings.costValue(reward.pointsCost), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = strings.editContentDescription) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

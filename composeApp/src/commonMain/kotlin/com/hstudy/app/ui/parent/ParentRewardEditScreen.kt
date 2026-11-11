package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun ParentRewardEditScreen(
    rewardId: Long,
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pointsCost by remember { mutableStateOf("300") }
    var iconEmoji by remember { mutableStateOf("🎁") }
    var category by remember { mutableStateOf("其他") }
    var categoryExpanded by remember { mutableStateOf(false) }

    val strings = LocalStrings.current.parentRewardEdit
    val displayLanguage = LocalDisplayLanguage.current

    LaunchedEffect(rewardId) {
        if (rewardId > 0) {
            val reward = app.rewardRepository.getRewardById(rewardId)
            reward?.let {
                name = it.name
                description = it.description
                pointsCost = it.pointsCost.toString()
                iconEmoji = it.iconEmoji
                category = it.category
            }
        }
    }

    val emojiOptions = listOf("📚", "🎬", "🌳", "🍕", "🧸", "⭐", "🧹", "🎮", "🎁", "🏆", "🎵", "✈️", "📺", "🍦", "🚲", "🌙", "👑", "📱", "🏠", "🎨")
    val categoryOptions = listOf("娱乐", "美食", "出行", "特权", "实物", "社交", "其他")

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = if (rewardId == 0L) strings.newTitle else strings.editTitle,
                onNavigateBack = onNavigateBack,
                actions = {
                    TextButton(onClick = {
                        if (name.isBlank()) return@TextButton
                        scope.launch {
                            val reward = RewardEntity(
                                id = rewardId,
                                name = name,
                                description = description,
                                pointsCost = pointsCost.toIntOrNull() ?: 300,
                                iconEmoji = iconEmoji,
                                category = category
                            )
                            if (rewardId == 0L) app.rewardRepository.addReward(reward)
                            else app.rewardRepository.updateReward(reward)
                            onNavigateBack()
                        }
                    }) {
                        Text(strings.saveButton, style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // 新建奖励时提示：预设奖励已在列表中，此处仅添加自定义新奖励
                if (rewardId == 0L) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(strings.presetHint,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.nameLabel) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(strings.descriptionLabel) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = pointsCost, onValueChange = { pointsCost = it.filter { c -> c.isDigit() } }, label = { Text(strings.costLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(16.dp))

                // 分类选择（下拉）
                Text(strings.categoryHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = localizedPresetText(category, displayLanguage),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.categoryPickerLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categoryOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(localizedPresetText(opt, displayLanguage)) },
                                onClick = {
                                    category = opt
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(strings.iconHeading, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojiOptions.forEach { emoji ->
                        FilterChip(
                            selected = iconEmoji == emoji,
                            onClick = { iconEmoji = emoji },
                            label = { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (rewardId > 0L) {
                    Button(
                        onClick = { scope.launch { app.rewardRepository.deleteReward(rewardId); onNavigateBack() } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.deleteButton) }
                }
            }
        }
    }
}

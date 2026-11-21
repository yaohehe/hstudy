package com.hstudy.app.ui.wish

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.WishGoalEntity
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

/**
 * 家长端 - 心愿目标管理：创建/编辑/删除心愿存钱罐目标
 *
 * v3.3.1 D-08（指令 §3.3-3）：删除走二次确认。旧实现是点一下删除按钮直接裸删，
 * 孩子已经存进该心愿的积分随 `DELETE FROM wish_goals` 静默蒸发、账本零流水。
 * 现在：`savedPoints > 0` 的进行中心愿在弹窗里明确承诺"删除后自动退还至孩子积分余额"，
 * 退还本身由 [com.hstudy.app.data.repository.WishGoalRepository.deleteGoal] 落一笔正向流水完成。
 * 提示条件（`isActive && savedPoints > 0`）与仓库里退款的条件同源 —— 不承诺不会发生的退还。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentWishGoalManageScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val strings = LocalStrings.current.parentWishGoal
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val goals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())

    // 编辑对话框（null=关闭，0=新建，>0=编辑该id）
    var editingGoalId by remember { mutableStateOf<Long?>(null) }
    // D-08：待删除的心愿（null=弹窗关闭）
    var pendingRemoveGoal by remember { mutableStateOf<WishGoalEntity?>(null) }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingGoalId = 0L }) {
                Icon(Icons.Filled.Add, contentDescription = strings.newWishContentDescription)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Text(strings.hint,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }

                    if (goals.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("🐷", fontSize = 48.sp)
                                Text(strings.emptyTitle, style = MaterialTheme.typography.titleMedium)
                                Text(strings.emptyHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = {
                                    scope.launch {
                                        val samples = listOf(
                                            com.hstudy.app.data.local.entity.WishGoalEntity(name = "一个小玩具", description = "存够积分换一个喜欢的小玩具", targetPoints = 800, iconEmoji = "🧸"),
                                            com.hstudy.app.data.local.entity.WishGoalEntity(name = "一本新书", description = "去书店选一本喜欢的书", targetPoints = 300, iconEmoji = "📖"),
                                            com.hstudy.app.data.local.entity.WishGoalEntity(name = "一台新平板", description = "存很久的大目标，加油！", targetPoints = 5000, iconEmoji = "📱")
                                        )
                                        samples.forEach { app.wishGoalRepository.addGoal(it) }
                                        snackbarHostState.showSnackbar(strings.samplesLoaded)
                                    }
                                }) { Text(strings.loadSamplesButton) }
                                Text(strings.addOwnHint, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    goals.forEach { goal ->
                        GoalManageCard(
                            goal = goal,
                            onEdit = { editingGoalId = goal.id },
                            // D-08：不再裸删，先进确认弹窗（已存积分要如实告知会退还）
                            onDelete = { pendingRemoveGoal = goal }
                        )
                    }
                }
            }
        }
    }

    // 编辑/新建对话框
    editingGoalId?.let { id ->
        GoalEditDialog(
            goalId = id,
            onDismiss = { editingGoalId = null },
            onSaved = {
                editingGoalId = null
                scope.launch { snackbarHostState.showSnackbar(strings.savedMessage) }
            }
        )
    }

    // D-08：删除确认弹窗（进行中的心愿若已有已存积分，明确告知会自动退还至孩子积分余额）
    pendingRemoveGoal?.let { goal ->
        val refundable = goal.isActive && goal.savedPoints > 0
        AlertDialog(
            onDismissRequest = { pendingRemoveGoal = null },
            title = { Text(strings.removeDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.removeConfirmMessage(localizedPresetText(goal.name, displayLanguage)))
                    if (refundable) {
                        Text(
                            strings.removeRefundNotice(goal.savedPoints),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoveGoal = null
                    scope.launch {
                        // 退还本身在仓库里完成（deleteGoal → 正向 REWARD_EXCHANGE 流水），页面只负责删
                        app.wishGoalRepository.deleteGoal(goal.id)
                        snackbarHostState.showSnackbar(
                            strings.removedMessage(localizedPresetText(goal.name, displayLanguage))
                        )
                    }
                }) { Text(common.confirmButton) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveGoal = null }) { Text(common.cancelButton) }
            }
        )
    }
}

@Composable
private fun GoalManageCard(
    goal: WishGoalEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current.parentWishGoal
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
    val progress = (goal.savedPoints.toFloat() / goal.targetPoints).coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.iconEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(localizedPresetText(goal.name, displayLanguage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (goal.isActive) strings.activeLine(goal.savedPoints, goal.targetPoints)
                        else strings.achievedBadge,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) { Text("✏️") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription, tint = MaterialTheme.colorScheme.error)
                }
            }
            if (goal.isActive) {
                // 指令 3.4-心愿2：宽轨药丸进度（与儿童端同形）
                val animatedGoal by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.75f),
                    label = "wishManageProgress"
                )
                LinearProgressIndicator(
                    progress = animatedGoal,
                    modifier = Modifier.fillMaxWidth().height(12.dp)
                        .clip(MaterialTheme.shapes.small),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditDialog(
    goalId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val app = AppGraph
    val strings = LocalStrings.current.parentWishGoal
    val common = LocalStrings.current.common
    val taskEdit = LocalStrings.current.parentTaskEdit
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetPoints by remember { mutableStateOf("1000") }
    var iconEmoji by remember { mutableStateOf("🎯") }

    LaunchedEffect(goalId) {
        if (goalId > 0) {
            app.wishGoalRepository.getGoalById(goalId)?.let {
                name = it.name
                description = it.description
                targetPoints = it.targetPoints.toString()
                iconEmoji = it.iconEmoji
            }
        }
    }

    val emojiOptions = listOf("🎯", "📱", "✈️", "🚲", "🎮", "🧸", "👟", "📚", "🎨", "🎹", "⚽", "🍭", "🏆", "💎", "🎁", "🌟")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goalId == 0L) strings.dialogTitleNew else strings.dialogTitleEdit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text(strings.nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text(strings.descLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetPoints, onValueChange = { targetPoints = it.filter { c -> c.isDigit() } },
                    label = { Text(strings.targetPointsLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(strings.iconPickerLabel, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojiOptions.forEach { emoji ->
                        FilterChip(selected = iconEmoji == emoji, onClick = { iconEmoji = emoji },
                            label = { Text(emoji, style = MaterialTheme.typography.titleMedium) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                scope.launch {
                    val target = targetPoints.toIntOrNull() ?: 1000
                    if (goalId == 0L) {
                        app.wishGoalRepository.addGoal(
                            WishGoalEntity(name = name, description = description, targetPoints = target, iconEmoji = iconEmoji)
                        )
                    } else {
                        val existing = app.wishGoalRepository.getGoalById(goalId)
                        if (existing != null) {
                            app.wishGoalRepository.updateGoal(
                                existing.copy(name = name, description = description, targetPoints = target, iconEmoji = iconEmoji)
                            )
                        }
                    }
                    onSaved()
                }
            }) { Text(taskEdit.saveButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(common.cancelButton) } }
    )
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.WishGoalEntity
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch

/**
 * 家长端 - 心愿目标管理：创建/编辑/删除心愿存钱罐目标
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentWishGoalManageScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val goals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())

    // 编辑对话框（null=关闭，0=新建，>0=编辑该id）
    var editingGoalId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { HStudyTopBar(title = "🐷 心愿目标管理", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingGoalId = 0L }) {
                Icon(Icons.Filled.Add, contentDescription = "新建心愿")
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
                        Text("💡 为孩子设定一个大目标（如一台新平板、一次旅行），孩子用积分逐步存入，攒够即可兑换，培养延迟满足能力。",
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
                                Text("还没有心愿目标", style = MaterialTheme.typography.titleMedium)
                                Text("点击下方按钮一键设置示例心愿（小玩具/新平板/迪士尼之旅），给孩子一个存积分的目标，再按需调整。",
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
                                        snackbarHostState.showSnackbar("✅ 已设置示例心愿")
                                    }
                                }) { Text("✨ 一键设置示例心愿") }
                                Text("或点击右下角 + 自行创建", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    goals.forEach { goal ->
                        GoalManageCard(
                            goal = goal,
                            onEdit = { editingGoalId = goal.id },
                            onDelete = {
                                scope.launch {
                                    app.wishGoalRepository.deleteGoal(goal.id)
                                    snackbarHostState.showSnackbar("已删除「${goal.name}」")
                                }
                            }
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
                scope.launch { snackbarHostState.showSnackbar("✅ 已保存") }
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
    val progress = (goal.savedPoints.toFloat() / goal.targetPoints).coerceIn(0f, 1f)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.iconEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (goal.isActive) "进行中 · ${goal.savedPoints}/${goal.targetPoints}分"
                        else "🎊 已达成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) { Text("✏️") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (goal.isActive) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
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
        title = { Text(if (goalId == 0L) "新建心愿目标" else "编辑心愿目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("心愿名称（如：一台新平板）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("描述（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetPoints, onValueChange = { targetPoints = it.filter { c -> c.isDigit() } },
                    label = { Text("目标积分") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("选择图标", style = MaterialTheme.typography.labelMedium)
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
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

package com.hstudy.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.effectivePoints
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.child.pointRecordSection
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.common.wishNoteDisplayText
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

/**
 * 家长积分管理页（指令 §3.3.4：透明信用账本，家长端同构）。
 *
 * v3.3 W4：顶部新增**资产看板**（surfaceContainerHigh：displayLarge 余额 + 当前可用积分副标 +
 * ⏳ 待家长审核行（tertiaryContainer，审核后计入注）+ 🌟 累计获得行）；全部积分记录按
 * 今天/昨天/本周更早/历史记录分组；记录卡左侧加类型徽章，REVERSAL/PENALTY 附小标签。
 * 看板/分组/标签词面跨表复用 [ChildPointsStrings]（同一中文值一个英文出处；家长端
 * ParentPointsStrings 只保留本页独有词面），分组函数复用孩子端 [pointRecordSection] 保证两端口径一致。
 */
@Composable
fun ParentPointsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    // 待家长审批的积分：尚未计入余额，看板单独分列（与余额概念严格区分）
    val pendingPoints by app.pointRepository.getPendingApprovalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())
    val tasks by app.taskRepository.getAllActiveTasks().collectAsState(initial = emptyList())
    // D-08：心愿存入/退回流水的 note 只有业务键，心愿名回查 wish_goals 后派生（词面沿用 childPoints 槽）
    val wishGoals by app.wishGoalRepository.getAllGoals().collectAsState(initial = emptyList())
    val wishNamesById = remember(wishGoals) { wishGoals.associate { it.id to it.name } }

    // 近7天打卡记录（用于任务积分排行）
    val sevenDaysAgoMillis = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis }
    val recentCompletions by app.pointRepository.getCompletionsBetween(sevenDaysAgoMillis, com.hstudy.app.ui.dt.nowMillis()).collectAsState(initial = emptyList())

    var showAdjustDialog by remember { mutableStateOf(false) }
    var adjustPoints by remember { mutableStateOf("") }
    var adjustNote by remember { mutableStateOf("") }

    val strings = LocalStrings.current.parentPoints
    val pointsDetail = LocalStrings.current.childPoints
    val common = LocalStrings.current.common
    val parentHome = LocalStrings.current.parentHome
    val displayLanguage = LocalDisplayLanguage.current

    // 累计获得 = 账本正分合计（只读展示，不参与余额判等；与孩子端口径一致）
    val totalEarned = remember(records) { records.filter { it.points > 0 }.sumOf { it.points } }
    // 时间轴分桶：LazyColumn 的 LazyListScope 内容不是 composable 上下文，remember 必须在页面作用域算好
    val grouped = remember(records) { records.groupBy { pointRecordSection(it.date) } }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = parentHome.menuPointsTitle,
                onNavigateBack = onNavigateBack,
                actions = { PointsBadge(points = totalPoints) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdjustDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = strings.adjustContentDescription)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // ===== 资产看板（指令 §3.3.4-1，余额与待审严格分列）=====
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(pointsDetail.balanceTitle, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pointsDetail.balanceValue(totalPoints), style = MaterialTheme.typography.displayLarge)
                                Text(pointsDetail.balanceSubtitle, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (pendingPoints > 0) {
                                    Text(
                                        pointsDetail.pendingNote(pendingPoints),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    pointsDetail.totalEarnedValue(totalEarned),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // ===== 每日得分统计（近7天）=====
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(strings.dailyStatsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val shortFmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                                val last7 = (6 downTo 0).map { d ->
                                    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }
                                    dayFmt.format(c.time) to shortFmt.format(c.time)
                                }
                                last7.forEach { (dateStr, shortDate) ->
                                    val dayRecords = records.filter { dayFmt.format(Date(it.date)) == dateStr }
                                    val earned = dayRecords.filter { it.points > 0 }.sumOf { it.points }
                                    val spent = dayRecords.filter { it.points < 0 }.sumOf { -it.points }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(shortDate, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("+${earned}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        if (spent > 0) Text("-${spent}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                        else Spacer(modifier = Modifier.weight(1f))
                                        Text(strings.netValue(earned - spent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(50.dp))
                                    }
                                }
                            }
                        }
                    }

                    // ===== 任务积分排行（近7天）=====
                    item {
                        val taskMap = recentCompletions.groupBy { it.taskId }
                        val taskRanking = taskMap.map { (taskId, comps) ->
                            val task = tasks.find { it.id == taskId }
                            val title = task?.let { localizedPresetText(it.title, displayLanguage) } ?: strings.taskFallbackTitle(taskId)
                            val count = comps.size
                            // 使用任务实际积分（审核制下 pointsEarned 可能为 0，用任务定义积分更准确）
                            val ptsPerTime = task?.effectivePoints() ?: 0
                            val totalPts = count * ptsPerTime
                            Triple(title, count, totalPts)
                        }.sortedByDescending { it.third }.take(5)

                        if (taskRanking.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.taskRankingTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    taskRanking.forEachIndexed { idx, (title, count, pts) ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${idx + 1}.", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                            Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                            Text(strings.timesCount(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                                            Text("+${pts}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Text(strings.allRecordsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    // ===== 时间轴分组（指令 §3.3.4-2）：今天/昨天/本周更早/历史记录，两端同口径 =====
                    listOf(
                        pointsDetail.sectionToday, pointsDetail.sectionYesterday,
                        pointsDetail.sectionWeekEarlier, pointsDetail.sectionHistory
                    ).mapIndexed { sectionIndex, title -> sectionIndex to title }.forEach { (sectionIndex, title) ->
                        val sectionRecords = grouped[sectionIndex].orEmpty()
                        if (sectionRecords.isNotEmpty()) {
                            item(key = "section-$sectionIndex") {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )
                            }
                            items(sectionRecords) { record ->
                                PointRecordCard(record = record, wishNamesById = wishNamesById)
                            }
                        }
                    }
                    if (records.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(strings.emptyRecords, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // 手动调整积分对话框
    if (showAdjustDialog) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text(strings.adjustDialogTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = adjustPoints,
                        onValueChange = { adjustPoints = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text(strings.adjustPointsLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adjustNote,
                        onValueChange = { adjustNote = it },
                        label = { Text(strings.adjustNoteLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pts = adjustPoints.toIntOrNull() ?: return@TextButton
                    scope.launch {
                        app.pointRepository.adjustPoints(pts, adjustNote)
                        showAdjustDialog = false
                        adjustPoints = ""
                        adjustNote = ""
                    }
                }) { Text(common.confirmButton) }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) { Text(common.cancelButton) }
            }
        )
    }
}

/**
 * 记录卡（指令 §3.3.4-3）：左圆角类型徽章 / 中类型+备注+时间 / 右大号带符号分值。
 * 数值语义与孩子端一致：获得=secondary 绿、兑换=primary 橙、撤销/PENALTY=error 警示并附小标签
 * （[打卡撤销] 词面跨表复用 ChildPointsStrings.tagCheckInUndo；[未完成扣分] 沿用 W2 typePenalty）。
 *
 * D-08：备注行的心愿业务键按 [wishNoteDisplayText] 派生成"存入心愿/心愿退回 + 心愿名"，
 * 类型行仍如实是账本类型（REWARD_EXCHANGE = 兑换消耗），旧记录的 note 原样显示、不改写。
 */
@Composable
private fun PointRecordCard(
    record: PointRecordEntity,
    wishNamesById: Map<Long, String>
) {
    val strings = LocalStrings.current.parentPoints
    val pointsDetail = LocalStrings.current.childPoints
    val wishNoteText = wishNoteDisplayText(record, wishNamesById)
    val typeLabel = when (record.type) {
        PointRecordType.TASK_COMPLETE -> strings.typeTaskComplete
        PointRecordType.BONUS -> strings.typeBonus
        PointRecordType.ADJUSTMENT -> strings.typeAdjustment
        PointRecordType.REWARD_EXCHANGE -> strings.typeRewardExchange
        PointRecordType.REVERSAL -> strings.typeReversal
        PointRecordType.PENALTY -> strings.typePenalty
    }
    val icon = when (record.type) {
        PointRecordType.TASK_COMPLETE -> "✅"
        PointRecordType.BONUS -> "🎁"
        PointRecordType.ADJUSTMENT -> "✏️"
        PointRecordType.REWARD_EXCHANGE -> "🛒"
        PointRecordType.REVERSAL -> "↩️"
        PointRecordType.PENALTY -> "⚠️"
    }
    val badgeContainer = when (record.type) {
        PointRecordType.TASK_COMPLETE, PointRecordType.BONUS -> MaterialTheme.colorScheme.secondaryContainer
        PointRecordType.REWARD_EXCHANGE -> MaterialTheme.colorScheme.primaryContainer
        PointRecordType.REVERSAL, PointRecordType.PENALTY -> MaterialTheme.colorScheme.errorContainer
        PointRecordType.ADJUSTMENT -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val valueColor = when {
        // D-08：REWARD_EXCHANGE 第一次可能有正向记录（删除心愿的原路退还）→ 按符号走绿；
        // 存量消耗记录全是负数，橙色不变。
        record.type == PointRecordType.REWARD_EXCHANGE ->
            if (record.points >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        record.type == PointRecordType.REVERSAL || record.type == PointRecordType.PENALTY -> MaterialTheme.colorScheme.error
        record.points >= 0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val isPositive = record.points >= 0
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // P2-01：系统扣分（PENALTY）标签用警示色，与家长奖惩的 ADJUSTMENT 区分
                Text(typeLabel, style = MaterialTheme.typography.titleSmall,
                    color = if (record.type == PointRecordType.PENALTY)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (record.note.isNotEmpty() || wishNoteText != null) {
                    // D-08：业务键 → 派生文案；其余 note 原样（心愿名不再从库里读中文）
                    Text(wishNoteText ?: record.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateFormat.format(Date(record.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isPositive) "+" else ""}${record.points}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
                when (record.type) {
                    PointRecordType.REVERSAL -> Text(
                        "[${pointsDetail.tagCheckInUndo}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    PointRecordType.PENALTY -> Text(
                        // [未完成扣分] 标签词面复用孩子端无 emoji 前缀的 typePenalty（W2 登记值），不截字符 hack
                        "[${pointsDetail.typePenalty}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
            }
        }
    }
}

package com.hstudy.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.ParentApprovalStrings
import com.hstudy.app.platform.AnalyticsEvent
import com.hstudy.app.platform.AnalyticsReporter
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

/**
 * 家长积分审核页：显示孩子的积分申请列表，家长可逐条通过/拒绝或批量通过。
 * 审核通过后实际执行加分。
 *
 * v3.3 W4（指令 §3.3.3）M3 重构：
 * - Tab 下方新增**待审资产看板卡**（surfaceContainerLow / 16dp large 形）：待审总笔数 + 待发放总积分
 *   + "全部通过 (+M分)" 填充按钮 → 批量确认对话框 → 既有 `approveAllAndAward()` 幂等批量路径；
 * - `ApprovalCard` 按指令改三段式：头部业务类型 SuggestionChip（actionType → typeChip* 映射）、
 *   主体内容+时间+加粗大号分值、底部 FilledTonalButton(通过)/OutlinedButton(拒绝) 6:4、间距 12dp；
 * - 历史区保留 W2 P1-02 的**按状态如实展示**（statusIcon/scoreText 语义不动），徽章容器换成
 *   M3 tonal 四色（绿/橙/灰/红，RA8：徽章文案仍走 Strings）。
 * 埋点约束：APPROVAL_ACTION 埋点三个调用点（单条通过 / 拒绝 / 全部通过）位置随各自的
 * onClick 处理器迁移，**条数恒为 3**（analytics_call_sites.py 闸门复核）。
 */
@Composable
fun ParentApprovalScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allApprovals by app.pointApprovalRepository.getAllApprovals().collectAsState(initial = emptyList())
    val pendingApprovals = allApprovals.filter { it.status == ApprovalStatus.PENDING }
    // 终审 M-3：历史列表保留全量非 PENDING（截断下移到状态筛选之后，见 filteredHistoryApprovals）
    val historyApprovals = allApprovals.filter { it.status != ApprovalStatus.PENDING }

    var selectedTab by remember { mutableIntStateOf(0) }

    val strings = LocalStrings.current.parentApproval
    val common = LocalStrings.current.common
    // P1-02 历史筛选钮「全部」复用 [TaskListStrings.filterAll]（同一中文值一个英文出处）；
    // 「已通过/已拒绝」复用本表 statusApproved/statusRejected，「已撤销」用新字段 historyFilterUndone。
    val taskList = LocalStrings.current.taskList
    // P1-02 历史二级筛选：0=全部 / 1=已通过 / 2=已拒绝 / 3=已撤销（覆盖 REVOKED+CANCELLED 两种孩子撤销终态）
    var historyFilter by remember { mutableIntStateOf(0) }
    // 终审 M-3：先按状态筛选**全量**历史，再 take(20) 截断——原实现先截断后筛选，
    // 20 条之外的旧记录会被截掉，导致"已拒绝/已撤销"芯片显示"暂无审核记录"的假空态。
    val filteredHistoryApprovals = historyApprovals.filter { approval ->
        when (historyFilter) {
            1 -> approval.status == ApprovalStatus.APPROVED
            2 -> approval.status == ApprovalStatus.REJECTED
            3 -> approval.status == ApprovalStatus.REVOKED || approval.status == ApprovalStatus.CANCELLED
            else -> true
        }
    }.take(20)

    // W4 §3.3.3-1 看板数据：笔数与待发放总分都直接来自 pendingApprovals（不新增查询）
    val pendingCount = pendingApprovals.size
    val pendingTotalPoints = pendingApprovals.sumOf { it.requestedPoints }
    var showApproveAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = strings.title,
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 选项卡
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text(strings.pendingTab(pendingApprovals.size)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text(strings.historyTab) })
            }

            if (selectedTab == 0) {
                // 待审核列表
                if (pendingApprovals.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", style = MaterialTheme.typography.displaySmall)
                            Text(strings.emptyPendingTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(strings.emptyPendingNote, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ===== 待审资产看板卡（指令 §3.3.3-1）=====
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(strings.pendingCountLabel(pendingCount),
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(strings.pendingPointsLabel(pendingTotalPoints),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary)
                                    Button(
                                        onClick = { showApproveAllDialog = true },
                                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Filled.Approval, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.approveAllPointsButton(pendingTotalPoints))
                                    }
                                }
                            }
                        }
                        items(pendingApprovals, key = { it.id }) { approval ->
                            ApprovalCard(
                                approval = approval,
                                onApprove = {
                                    scope.launch {
                                        // 幂等发放：仅 PENDING 可计分，重复通过不会重复加分
                                        val awarded = app.pointRepository.approveAndAward(approval.id)
                                        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.APPROVAL_ACTION))
                                        snackbarHostState.showSnackbar(
                                            if (awarded > 0) strings.approvedMessage(awarded)
                                            else strings.alreadyProcessedMessage
                                        )
                                    }
                                },
                                onReject = {
                                    scope.launch {
                                        app.pointRepository.rejectApproval(approval.id)
                                        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.APPROVAL_ACTION))
                                        snackbarHostState.showSnackbar(strings.rejectedMessage)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // 历史记录：P1-02 二级筛选钮 + 按状态如实展示图标/分值（§四 状态机 / §五 家长端口径）
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = historyFilter == 0, onClick = { historyFilter = 0 },
                        label = { Text(taskList.filterAll) })
                    FilterChip(selected = historyFilter == 1, onClick = { historyFilter = 1 },
                        label = { Text(strings.statusApproved) })
                    FilterChip(selected = historyFilter == 2, onClick = { historyFilter = 2 },
                        label = { Text(strings.statusRejected) })
                    FilterChip(selected = historyFilter == 3, onClick = { historyFilter = 3 },
                        label = { Text(strings.historyFilterUndone) })
                }
                if (filteredHistoryApprovals.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(strings.emptyHistory, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistoryApprovals, key = { it.id }) { approval ->
                            // P1-02：状态图标逐态展示（emoji 不受 i18n 约束，RA8）
                            val statusIcon = when (approval.status) {
                                ApprovalStatus.APPROVED -> "✅"
                                ApprovalStatus.REJECTED -> "⛔"
                                ApprovalStatus.CANCELLED -> "↩️"
                                ApprovalStatus.REVOKED -> "🔄"
                                ApprovalStatus.PENDING -> "⏳"
                            }
                            // P1-02：分值语义与状态机一致——CANCELLED 从未计分、REVOKED 已扣回、
                            // REJECTED 不计分、PENDING 未入账，不再对非 APPROVED 一律红色 "+N"。
                            // （语义不动，W4 只把状态徽章换成 M3 tonal 容器，指令 §3.3.3-3 四色）
                            val (scoreText, scoreColor) = when (approval.status) {
                                ApprovalStatus.APPROVED ->
                                    "+${approval.requestedPoints}" to MaterialTheme.colorScheme.primary
                                ApprovalStatus.REVOKED ->
                                    strings.scoreRevoked(approval.requestedPoints) to MaterialTheme.colorScheme.error
                                ApprovalStatus.CANCELLED ->
                                    strings.scoreCancelled to MaterialTheme.colorScheme.outline
                                ApprovalStatus.REJECTED ->
                                    strings.scoreRejected to MaterialTheme.colorScheme.error
                                ApprovalStatus.PENDING ->
                                    strings.scorePending(approval.requestedPoints) to MaterialTheme.colorScheme.tertiary
                            }
                            val (badgeContainer, badgeContent) = when (approval.status) {
                                ApprovalStatus.APPROVED ->
                                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                                ApprovalStatus.REVOKED ->
                                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                ApprovalStatus.CANCELLED ->
                                    MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                                ApprovalStatus.REJECTED ->
                                    MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                ApprovalStatus.PENDING ->
                                    MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // M3 tonal 状态徽章（绿/橙/灰/红），文案仍走 statusLabel（Strings，RA8）
                                    Row(
                                        modifier = Modifier
                                            .background(badgeContainer, MaterialTheme.shapes.extraSmall)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(statusIcon, style = MaterialTheme.typography.labelSmall,
                                            color = badgeContent)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(statusLabel(approval.status, strings),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeContent)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(approval.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            formatTime(approval.requestedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        scoreText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 批量通过确认对话框（指令 §3.3.3-1：确认后走既有 approveAllAndAward 幂等批量路径）=====
    if (showApproveAllDialog) {
        AlertDialog(
            onDismissRequest = { showApproveAllDialog = false },
            title = { Text(strings.approveAllButton) },
            text = { Text(strings.approveAllDialogMessage(pendingCount, pendingTotalPoints)) },
            confirmButton = {
                TextButton(onClick = {
                    showApproveAllDialog = false
                    scope.launch {
                        // 批量通过：逐条走幂等发放，同一业务只会真正计分一次
                        val awarded = app.pointRepository.approveAllAndAward()
                        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.APPROVAL_ACTION))
                        snackbarHostState.showSnackbar(strings.approveAllMessage(awarded))
                    }
                }) { Text(common.confirmButton) }
            },
            dismissButton = {
                TextButton(onClick = { showApproveAllDialog = false }) { Text(common.cancelButton) }
            }
        )
    }
}

/**
 * 待审卡片（指令 §3.3.3-2 三段式）：头部类型 chip / 主体内容+时间+加粗大号分值 / 底部 6:4 双按钮。
 * 容器 surfaceContainerLow + 16dp（MaterialTheme.shapes.large），按钮间隔 12dp。
 */
@Composable
private fun ApprovalCard(
    approval: PointApprovalEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val strings = LocalStrings.current.parentApproval
    val taskList = LocalStrings.current.taskList

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ===== 头部：业务类型 chip（emoji 不受 i18n 约束，RA8）=====
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeEmoji = when (approval.actionType) {
                    "TASK_COMPLETE" -> "✏️"
                    "MISTAKE_REVIEW" -> "📖"
                    "FOCUS_COMPLETE" -> "🍅"
                    "VARIATION" -> "💡"
                    else -> "⭐"
                }
                Text(typeEmoji, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                SuggestionChip(
                    onClick = { },
                    label = { Text(typeChipLabel(approval.actionType, strings), style = MaterialTheme.typography.labelMedium) }
                )
            }
            // ===== 主体：内容 + 提交时间 + 加粗大号申请分值 =====
            Text(approval.description, style = MaterialTheme.typography.bodyLarge)
            Text(formatTime(approval.requestedAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                taskList.pointsEarnedLabel(approval.requestedPoints),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // ===== 底部：通过/拒绝 6:4，间隔 12dp（触控高度 ≥48dp）=====
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onApprove,
                    modifier = Modifier.weight(6f).defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.approveButton)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(4f).defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.rejectButton)
                }
            }
        }
    }
}

/** actionType → 业务类型 chip 词面（七类业务全覆盖，未知类型走通用词；映射与提交侧 actionType 字面量一致） */
private fun typeChipLabel(actionType: String, strings: ParentApprovalStrings): String = when (actionType) {
    "TASK_COMPLETE" -> strings.typeChipTask
    "HABIT_CHECKIN" -> strings.typeChipHabit
    "ASSIGNMENT" -> strings.typeChipAssignment
    "DAILY_TARGET" -> strings.typeChipTarget
    "MISTAKE_REVIEW" -> strings.typeChipMistake
    "FOCUS_COMPLETE" -> strings.typeChipFocus
    "VARIATION" -> strings.typeChipVariation
    else -> strings.typeChipOther
}

/** 审核状态文案（含撤销/作废，保证生命周期展示一致） */
private fun statusLabel(status: ApprovalStatus, strings: ParentApprovalStrings): String = when (status) {
    ApprovalStatus.PENDING -> strings.statusPending
    ApprovalStatus.APPROVED -> strings.statusApproved
    ApprovalStatus.REJECTED -> strings.statusRejected
    ApprovalStatus.CANCELLED -> strings.statusCancelled
    ApprovalStatus.REVOKED -> strings.statusRevoked
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

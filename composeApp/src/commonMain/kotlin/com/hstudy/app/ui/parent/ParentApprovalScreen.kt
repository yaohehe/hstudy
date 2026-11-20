package com.hstudy.app.ui.parent

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
    val historyApprovals = allApprovals.filter { it.status != ApprovalStatus.PENDING }.take(20)

    var selectedTab by remember { mutableIntStateOf(0) }

    val strings = LocalStrings.current.parentApproval
    // P1-02 历史筛选钮「全部」复用 [TaskListStrings.filterAll]（同一中文值一个英文出处）；
    // 「已通过/已拒绝」复用本表 statusApproved/statusRejected，「已撤销」用新字段 historyFilterUndone。
    val taskList = LocalStrings.current.taskList
    // P1-02 历史二级筛选：0=全部 / 1=已通过 / 2=已拒绝 / 3=已撤销（覆盖 REVOKED+CANCELLED 两种孩子撤销终态）
    var historyFilter by remember { mutableIntStateOf(0) }
    val filteredHistoryApprovals = historyApprovals.filter { approval ->
        when (historyFilter) {
            1 -> approval.status == ApprovalStatus.APPROVED
            2 -> approval.status == ApprovalStatus.REJECTED
            3 -> approval.status == ApprovalStatus.REVOKED || approval.status == ApprovalStatus.CANCELLED
            else -> true
        }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = strings.title,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (pendingApprovals.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                // 批量通过：逐条走幂等发放，同一业务只会真正计分一次
                                val awarded = app.pointRepository.approveAllAndAward()
                                AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.APPROVAL_ACTION))
                                snackbarHostState.showSnackbar(strings.approveAllMessage(awarded))
                            }
                        }) { Text(strings.approveAllButton) }
                    }
                }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(statusIcon, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(approval.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                        "${formatTime(approval.requestedAt)} · ${statusLabel(approval.status, strings)}",
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
}

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(approval.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(formatTime(approval.requestedAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text(taskList.pointsEarnedLabel(approval.requestedPoints), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.approveButton)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.rejectButton)
                }
            }
        }
    }
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

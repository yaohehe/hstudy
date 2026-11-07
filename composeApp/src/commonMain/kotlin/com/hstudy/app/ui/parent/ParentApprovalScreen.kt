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

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = "📋 积分审核",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (pendingApprovals.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                // 批量通过：逐条走幂等发放，同一业务只会真正计分一次
                                val awarded = app.pointRepository.approveAllAndAward()
                                snackbarHostState.showSnackbar("✅ 已全部通过，共发放 $awarded 分")
                            }
                        }) { Text("全部通过") }
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
                    text = { Text("待审核${if (pendingApprovals.isNotEmpty()) " ${pendingApprovals.size}" else ""}") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("历史记录") })
            }

            if (selectedTab == 0) {
                // 待审核列表
                if (pendingApprovals.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", style = MaterialTheme.typography.displaySmall)
                            Text("暂无待审核的积分申请", style = MaterialTheme.typography.bodyLarge)
                            Text("孩子完成学习行为后会在这里提交积分申请", style = MaterialTheme.typography.bodySmall,
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
                                        snackbarHostState.showSnackbar(
                                            if (awarded > 0) "✅ 已通过：+${awarded}分"
                                            else "该申请已处理，未重复加分"
                                        )
                                    }
                                },
                                onReject = {
                                    scope.launch {
                                        app.pointRepository.rejectApproval(approval.id)
                                        snackbarHostState.showSnackbar("已拒绝该申请")
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // 历史记录
                if (historyApprovals.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无审核记录", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyApprovals, key = { it.id }) { approval ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (approval.status == ApprovalStatus.APPROVED) "✅" else "❌",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(approval.description, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                        "${formatTime(approval.requestedAt)} · ${statusLabel(approval.status)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "+${approval.requestedPoints}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (approval.status == ApprovalStatus.APPROVED)
                                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                Text("+${approval.requestedPoints}分", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("通过")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拒绝")
                }
            }
        }
    }
}

/** 审核状态文案（含撤销/作废，保证生命周期展示一致） */
private fun statusLabel(status: ApprovalStatus): String = when (status) {
    ApprovalStatus.PENDING -> "待审核"
    ApprovalStatus.APPROVED -> "已通过"
    ApprovalStatus.REJECTED -> "已拒绝"
    ApprovalStatus.CANCELLED -> "已取消打卡（未计分）"
    ApprovalStatus.REVOKED -> "已撤销（积分已扣回）"
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

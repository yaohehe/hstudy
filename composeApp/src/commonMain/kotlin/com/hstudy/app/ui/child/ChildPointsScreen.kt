package com.hstudy.app.ui.child

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.PointsBadge
import com.hstudy.app.ui.common.ResponsiveContent
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ChildPointsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val totalPoints by app.pointRepository.getTotalPoints().collectAsState(initial = 0)
    // 待家长审批的积分：尚未计入余额，单独展示，避免与可用余额混淆
    val pendingPoints by app.pointRepository.getPendingApprovalPoints().collectAsState(initial = 0)
    val records by app.pointRepository.getAllRecords().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            HStudyTopBar(title = "积分明细", onNavigateBack = onNavigateBack, actions = { PointsBadge(points = totalPoints) })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(windowSizeClass = windowSizeClass, modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // 总计卡片
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("总积分余额", style = MaterialTheme.typography.titleSmall)
                                Text("⭐ $totalPoints 分", style = MaterialTheme.typography.headlineLarge)
                                // 待审批积分不参与余额，单独列示
                                if (pendingPoints > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "⏳ 待家长审核 +$pendingPoints 分（通过后计入余额）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { Text("积分变动记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }

                    items(records) { record ->
                        PointHistoryItem(record = record)
                    }

                    if (records.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("还没有积分记录哦，快去打卡吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PointHistoryItem(record: PointRecordEntity) {
    val icon = when (record.type) {
        PointRecordType.TASK_COMPLETE -> "✅"
        PointRecordType.BONUS -> "🎁"
        PointRecordType.ADJUSTMENT -> "✏️"
        PointRecordType.REWARD_EXCHANGE -> "🛒"
        PointRecordType.REVERSAL -> "↩️"
    }
    val isPositive = record.points >= 0
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.note.ifEmpty {
                        when (record.type) {
                            PointRecordType.TASK_COMPLETE -> "完成任务"
                            PointRecordType.BONUS -> "获得奖励"
                            PointRecordType.ADJUSTMENT -> "家长调整"
                            PointRecordType.REWARD_EXCHANGE -> "兑换奖励"
                            PointRecordType.REVERSAL -> "撤销扣回"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(dateFormat.format(Date(record.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${if (isPositive) "+" else ""}${record.points}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isPositive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
    }
}

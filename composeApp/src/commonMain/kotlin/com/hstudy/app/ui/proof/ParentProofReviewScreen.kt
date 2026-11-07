package com.hstudy.app.ui.proof

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import com.hstudy.app.ui.dt.fileExists
import coil3.compose.AsyncImage
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@Composable
fun ParentProofReviewScreen(
    onNavigateBack: () -> Unit,
    onViewPhoto: (String) -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val pendingProofs by app.habitRepository.getPendingProofs().collectAsState(initial = emptyList())
    val allProofs by app.habitRepository.getAllProofs().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    var showOnlyPending by remember { mutableStateOf(true) }
    val displayProofs = if (showOnlyPending) pendingProofs else allProofs

    // 获取任务名称映射
    var taskNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val taskList = app.taskRepository.getAllActiveTasks().first()
        taskNames = taskList.associate { it.id to it.title }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = "🔍 完成证明审核",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (pendingProofs.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${pendingProofs.size}")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 筛选
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(selected = showOnlyPending, onClick = { showOnlyPending = true }, label = { Text("待审核 (${pendingProofs.size})") })
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(selected = !showOnlyPending, onClick = { showOnlyPending = false }, label = { Text("全部") })
            }

            if (displayProofs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (showOnlyPending) "没有待审核的证明" else "还没有完成证明",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayProofs, key = { it.id }) { proof ->
                        ProofReviewCard(
                            proof = proof,
                            taskName = taskNames[proof.taskId] ?: "任务#${proof.taskId}",
                            onViewPhoto = { onViewPhoto(proof.photoPath) },
                            onApprove = {
                                scope.launch {
                                    // 规则调整：审核完成证明不再直接发放积分（积分由任务打卡的审批流程发放），
                                    // 审核仅更新证明状态，作为完成情况的留痕。
                                    app.habitRepository.approveProof(proof.id, 0)
                                    snackbarHostState.showSnackbar("✅ 已通过（积分请通过任务打卡审批发放）")
                                }
                            },
                            onReject = {
                                scope.launch {
                                    app.habitRepository.rejectProof(proof.id)
                                    snackbarHostState.showSnackbar("❌ 已拒绝")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofReviewCard(
    proof: CompletionProofEntity,
    taskName: String,
    onViewPhoto: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val statusColor = when (proof.status) {
        ProofStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        ProofStatus.APPROVED -> MaterialTheme.colorScheme.secondary
        ProofStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    val statusText = when (proof.status) {
        ProofStatus.PENDING -> "⏳ 待审核"
        ProofStatus.APPROVED -> "✅ 已通过"
        ProofStatus.REJECTED -> "❌ 已拒绝"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(taskName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(proof.submittedAt))
                Text(dateStr, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 照片缩略图
            if (proof.photoPath.isNotEmpty() && fileExists(proof.photoPath)) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onViewPhoto() }
                ) {
                    AsyncImage(
                        model = proof.photoPath,
                        contentDescription = "证明照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 备注
            if (proof.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("备注: ${proof.note}", style = MaterialTheme.typography.bodySmall)
            }

            // 操作按钮 (仅待审核时显示)
            if (proof.status == ProofStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("拒绝")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("通过 (+5分)")
                    }
                }
            }
        }
    }
}

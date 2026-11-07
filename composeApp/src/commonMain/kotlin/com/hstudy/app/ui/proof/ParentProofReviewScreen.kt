package com.hstudy.app.ui.proof
package com.hstudy.app.ui.proof


import androidx.compose.foundation.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.ui.dt.fileExists
import coil3.compose.AsyncImage
import coil3.compose.AsyncImage
import com.hstudy.app.AppGraph
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.ui.common.*
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.File
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*
import com.hstudy.app.ui.dt.*


@Composable
@Composable
fun ParentProofReviewScreen(
fun ParentProofReviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateBack: () -> Unit,
    onViewPhoto: (String) -> Unit,
    onViewPhoto: (String) -> Unit,
    windowSizeClass: WindowSizeClass
    windowSizeClass: WindowSizeClass
) {
) {
    val app = AppGraph
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val scope = rememberCoroutineScope()
    val pendingProofs by app.habitRepository.getPendingProofs().collectAsState(initial = emptyList())
    val pendingProofs by app.habitRepository.getPendingProofs().collectAsState(initial = emptyList())
    val allProofs by app.habitRepository.getAllProofs().collectAsState(initial = emptyList())
    val allProofs by app.habitRepository.getAllProofs().collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarHostState = remember { SnackbarHostState() }


    var showOnlyPending by remember { mutableStateOf(true) }
    var showOnlyPending by remember { mutableStateOf(true) }
    val displayProofs = if (showOnlyPending) pendingProofs else allProofs
    val displayProofs = if (showOnlyPending) pendingProofs else allProofs


    // 获取任务名称映射
    // 获取任务名称映射
    var taskNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var taskNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
    LaunchedEffect(Unit) {
        val taskList = app.taskRepository.getAllActiveTasks().first()
        val taskList = app.taskRepository.getAllActiveTasks().first()
        taskNames = taskList.associate { it.id to it.title }
        taskNames = taskList.associate { it.id to it.title }
    }
    }


    Scaffold(
    Scaffold(
        topBar = {
        topBar = {
            HStudyTopBar(
            HStudyTopBar(
                title = "🔍 完成证明审核",
                title = "🔍 完成证明审核",
                onNavigateBack = onNavigateBack,
                onNavigateBack = onNavigateBack,
                actions = {
                actions = {
                    if (pendingProofs.isNotEmpty()) {
                    if (pendingProofs.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("${pendingProofs.size}")
                            Text("${pendingProofs.size}")
                        }
                        }
                    }
                    }
                }
                }
            )
            )
        },
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.padding(padding)) {
            // 筛选
            // 筛选
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(selected = showOnlyPending, onClick = { showOnlyPending = true }, label = { Text("待审核 (${pendingProofs.size})") })
                FilterChip(selected = showOnlyPending, onClick = { showOnlyPending = true }, label = { Text("待审核 (${pendingProofs.size})") })
                Spacer(modifier = Modifier.width(8.dp))
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(selected = !showOnlyPending, onClick = { showOnlyPending = false }, label = { Text("全部") })
                FilterChip(selected = !showOnlyPending, onClick = { showOnlyPending = false }, label = { Text("全部") })
            }
            }


            if (displayProofs.isEmpty()) {
            if (displayProofs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", style = MaterialTheme.typography.displayLarge)
                        Text("✅", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (showOnlyPending) "没有待审核的证明" else "还没有完成证明",
                        Text(if (showOnlyPending) "没有待审核的证明" else "还没有完成证明",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                }
                }
            } else {
            } else {
                LazyColumn(
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                ) {
                    items(displayProofs, key = { it.id }) { proof ->
                    items(displayProofs, key = { it.id }) { proof ->
                        ProofReviewCard(
                        ProofReviewCard(
                            proof = proof,
                            proof = proof,
                            taskName = taskNames[proof.taskId] ?: "任务#${proof.taskId}",
                            taskName = taskNames[proof.taskId] ?: "任务#${proof.taskId}",
                            onViewPhoto = { onViewPhoto(proof.photoPath) },
                            onViewPhoto = { onViewPhoto(proof.photoPath) },
                            onApprove = {
                            onApprove = {
                                scope.launch {
                                scope.launch {
                                    // 规则调整：审核完成证明不再直接发放积分（积分由任务打卡的审批流程发放），
                                    // 规则调整：审核完成证明不再直接发放积分（积分由任务打卡的审批流程发放），
                                    // 审核仅更新证明状态，作为完成情况的留痕。
                                    // 审核仅更新证明状态，作为完成情况的留痕。
                                    app.habitRepository.approveProof(proof.id, 0)
                                    app.habitRepository.approveProof(proof.id, 0)
                                    snackbarHostState.showSnackbar("✅ 已通过（积分请通过任务打卡审批发放）")
                                    snackbarHostState.showSnackbar("✅ 已通过（积分请通过任务打卡审批发放）")
                                }
                                }
                            },
                            },
                            onReject = {
                            onReject = {
                                scope.launch {
                                scope.launch {
                                    app.habitRepository.rejectProof(proof.id)
                                    app.habitRepository.rejectProof(proof.id)
                                    snackbarHostState.showSnackbar("❌ 已拒绝")
                                    snackbarHostState.showSnackbar("❌ 已拒绝")
                                }
                                }
                            }
                            }
                        )
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
    }
}
}


@Composable
@Composable
private fun ProofReviewCard(
private fun ProofReviewCard(
    proof: CompletionProofEntity,
    proof: CompletionProofEntity,
    taskName: String,
    taskName: String,
    onViewPhoto: () -> Unit,
    onViewPhoto: () -> Unit,
    onApprove: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
    onReject: () -> Unit
) {
) {
    val statusColor = when (proof.status) {
    val statusColor = when (proof.status) {
        ProofStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        ProofStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        ProofStatus.APPROVED -> MaterialTheme.colorScheme.secondary
        ProofStatus.APPROVED -> MaterialTheme.colorScheme.secondary
        ProofStatus.REJECTED -> MaterialTheme.colorScheme.error
        ProofStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    }
    val statusText = when (proof.status) {
    val statusText = when (proof.status) {
        ProofStatus.PENDING -> "⏳ 待审核"
        ProofStatus.PENDING -> "⏳ 待审核"
        ProofStatus.APPROVED -> "✅ 已通过"
        ProofStatus.APPROVED -> "✅ 已通过"
        ProofStatus.REJECTED -> "❌ 已拒绝"
        ProofStatus.REJECTED -> "❌ 已拒绝"
    }
    }


    Card(modifier = Modifier.fillMaxWidth()) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(taskName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(taskName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                }
                val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(proof.submittedAt))
                val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(proof.submittedAt))
                Text(dateStr, style = MaterialTheme.typography.labelSmall)
                Text(dateStr, style = MaterialTheme.typography.labelSmall)
            }
            }


            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.height(8.dp))


            // 照片缩略图
            // 照片缩略图
            if (proof.photoPath.isNotEmpty() && fileExists(proof.photoPath)) {
            if (proof.photoPath.isNotEmpty() && fileExists(proof.photoPath)) {
                Card(
                Card(
                    modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onViewPhoto() }
                    modifier = Modifier.fillMaxWidth().height(160.dp).clickable { onViewPhoto() }
                ) {
                ) {
                    AsyncImage(
                    AsyncImage(
                        model = proof.photoPath,
                        model = proof.photoPath,
                        contentDescription = "证明照片",
                        contentDescription = "证明照片",
                        modifier = Modifier.fillMaxSize()
                        modifier = Modifier.fillMaxSize()
                    )
                    )
                }
                }
            }
            }


            // 备注
            // 备注
            if (proof.note.isNotEmpty()) {
            if (proof.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("备注: ${proof.note}", style = MaterialTheme.typography.bodySmall)
                Text("备注: ${proof.note}", style = MaterialTheme.typography.bodySmall)
            }
            }


            // 操作按钮 (仅待审核时显示)
            // 操作按钮 (仅待审核时显示)
            if (proof.status == ProofStatus.PENDING) {
            if (proof.status == ProofStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                    OutlinedButton(
                        onClick = onReject,
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("拒绝")
                        Text("拒绝")
                    }
                    }
                    Button(
                    Button(
                        onClick = onApprove,
                        onClick = onApprove,
                        modifier = Modifier.weight(1f)
                        modifier = Modifier.weight(1f)
                    ) {
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("通过 (+5分)")
                        Text("通过 (+5分)")
                    }
                    }
                }
                }
            }
            }
        }
        }
    }
    }
}
}

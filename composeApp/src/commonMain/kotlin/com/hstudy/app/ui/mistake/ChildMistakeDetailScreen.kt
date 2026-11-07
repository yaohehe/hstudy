package com.hstudy.app.ui.mistake

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.mistakeErrorTypes
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun ChildMistakeDetailScreen(
    mistakeId: Long,
    onNavigateBack: () -> Unit
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var mistake by remember { mutableStateOf<com.hstudy.app.data.local.entity.MistakeEntity?>(null) }
    val reviews by app.mistakeRepository.getReviewsForMistake(mistakeId).collectAsState(initial = emptyList())

    // 主动回忆模式状态：先遮住答案让孩子回想，再显示答案并自评
    var answerRevealed by remember { mutableStateOf(false) }
    var reviewDone by remember { mutableStateOf(false) }

    // 举一反三笔记输入
    var variationInput by remember { mutableStateOf("") }

    // 编辑模式状态
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }
    var editWrongReason by remember { mutableStateOf("") }
    var editCorrectMethod by remember { mutableStateOf("") }
    var editErrorType by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf(MistakeSubject.MATH) }

    LaunchedEffect(mistakeId) {
        mistake = app.mistakeRepository.getMistakeById(mistakeId)
        variationInput = mistake?.variationNote ?: ""
    }

    val m = mistake ?: return

    val subjectEmoji = when (m.subject) {
        MistakeSubject.MATH -> "🔢"
        MistakeSubject.CHINESE -> "📝"
        MistakeSubject.ENGLISH -> "🔤"
        MistakeSubject.SCIENCE -> "🔬"
        MistakeSubject.OTHER -> "📋"
    }

    Scaffold(
        topBar = {
            HStudyTopBar(
                title = "$subjectEmoji 错题详情",
                onNavigateBack = onNavigateBack,
                actions = {
                    // 编辑按钮
                    IconButton(onClick = {
                        if (!isEditing) {
                            editTitle = m.title
                            editContent = m.content
                            editWrongReason = m.wrongReason
                            editCorrectMethod = m.correctMethod
                            editErrorType = m.errorType
                            editSubject = m.subject
                            isEditing = true
                        }
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            app.mistakeRepository.setMastered(m.id, !m.isMastered)
                            mistake = app.mistakeRepository.getMistakeById(mistakeId)
                        }
                    }) {
                        Text(if (m.isMastered) "✅" else "⬜", style = MaterialTheme.typography.titleLarge)
                    }
                    // 删除按钮
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isEditing) {
                // ===== 编辑模式 =====
                Text("✏️ 编辑错题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // 科目选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MistakeSubject.entries.forEach { s ->
                        val label = when (s) {
                            MistakeSubject.MATH -> "数学"; MistakeSubject.CHINESE -> "语文"
                            MistakeSubject.ENGLISH -> "英语"; MistakeSubject.SCIENCE -> "科学"
                            MistakeSubject.OTHER -> "其他"
                        }
                        FilterChip(selected = editSubject == s, onClick = { editSubject = s }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it },
                    label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = editContent, onValueChange = { editContent = it },
                    label = { Text("题目内容") }, modifier = Modifier.fillMaxWidth().height(100.dp))
                OutlinedTextField(value = editWrongReason, onValueChange = { editWrongReason = it },
                    label = { Text("错误原因") }, modifier = Modifier.fillMaxWidth().height(80.dp))
                OutlinedTextField(value = editCorrectMethod, onValueChange = { editCorrectMethod = it },
                    label = { Text("正确解法") }, modifier = Modifier.fillMaxWidth().height(80.dp))
                // 错误类型
                Text("错误类型", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    mistakeErrorTypes.forEach { et ->
                        FilterChip(selected = editErrorType == et, onClick = { editErrorType = if (editErrorType == et) "" else et },
                            label = { Text(et, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val updated = m.copy(
                                title = editTitle, content = editContent,
                                wrongReason = editWrongReason, correctMethod = editCorrectMethod,
                                errorType = editErrorType, subject = editSubject
                            )
                            app.mistakeRepository.updateMistake(updated)
                            mistake = updated
                            isEditing = false
                            snackbarHostState.showSnackbar("✅ 错题已更新")
                        }
                    }) { Text("保存修改") }
                    OutlinedButton(onClick = { isEditing = false }) { Text("取消") }
                }
            } else {
            // ===== 正常显示模式 =====
            // 标题
            Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (m.isMastered) "✅ 已掌握" else "⏳ 未掌握") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                val dateStr = com.hstudy.app.ui.dt.SimpleDateFormat("yyyy-MM-dd", com.hstudy.app.ui.dt.Locale.getDefault()).format(com.hstudy.app.ui.dt.Date(m.createdAt))
                Text("添加于 $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 题目内容
            if (m.content.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📋 题目描述", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(m.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 主动回忆提示：未掌握且未显示答案时，先遮住答案让孩子回想
            if (!m.isMastered && !answerRevealed) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🤔", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("先自己回想一下这道题怎么做", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("主动回忆比直接看答案记得更牢！想好了再点击下方按钮核对。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // 错误原因（主动回忆模式下需先显示答案）
            if ((answerRevealed || m.isMastered) && m.wrongReason.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("❌ 错误原因", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(m.wrongReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // 正确解法（主动回忆模式下需先显示答案）
            if ((answerRevealed || m.isMastered) && m.correctMethod.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✅ 正确解法", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(m.correctMethod, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // 照片
            if (m.photoPath.isNotEmpty() && fileExists(m.photoPath)) {
                Text("📷 题目照片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    AsyncImage(
                        model = m.photoPath,
                        contentDescription = "错题照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 主动回忆复习流程
            when {
                // 已掌握：仅显示状态
                m.isMastered -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text("✅ 这道题已掌握，真棒！偶尔回顾一下保持记忆。",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
                // 第一步：还没显示答案 → 引导显示答案
                !answerRevealed -> {
                    Button(
                        onClick = { answerRevealed = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("👀 我想好了，显示答案核对") }
                }
                // 第二步：已显示答案但未自评 → 自评做对/没记住
                !reviewDone -> {
                    Text("📝 对照答案，你这次做对了吗？", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.reviewMistake(mistakeId, points = 0, note = "主动回忆-做对")
                                    app.pointApprovalRepository.submitApproval("MISTAKE_REVIEW", "错题复习做对: ${m.title}", 3)
                                    app.userPreferences.boostPetMood(2) // 复习让宠物开心
                                    com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                                    reviewDone = true
                                    snackbarHostState.showSnackbar("🎉 做对了！+3分已提交家长审核")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("✅ 做对了 (+5)") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.reviewMistake(mistakeId, points = 0, note = "主动回忆-没记住")
                                    app.pointApprovalRepository.submitApproval("MISTAKE_REVIEW", "错题复习: ${m.title}", 1)
                                    reviewDone = true
                                    snackbarHostState.showSnackbar("💪 +1分已提交审核，多复习几次就记住了")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("❌ 没记住 (+2)") }
                    }
                }
                // 第三步：自评完成
                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("✅ 本次复习完成！系统会按遗忘曲线自动安排下次复习时间。",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            // ===== 举一反三（掌握后拓展思考，加深理解）=====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (m.hasVariation) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡 举一反三", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (m.hasVariation) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("✅ 已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // 学科引导思考问题
                    val prompts = when (m.subject) {
                        MistakeSubject.MATH -> listOf("这道题考查的是什么知识点？", "改变数字或条件，你还会解吗？", "你能自己编一道类似的题吗？")
                        MistakeSubject.CHINESE -> listOf("这道题涉及哪些字词/知识点？", "换个语境你还能用对吗？", "你能造一个类似的句子吗？")
                        MistakeSubject.ENGLISH -> listOf("这道题的语法/单词重点是什么？", "换个时态/句型你还会吗？", "你能用这个知识点造个句吗？")
                        MistakeSubject.SCIENCE -> listOf("这道题背后的科学原理是什么？", "改变实验条件会怎样？", "生活中还有哪些类似现象？")
                        MistakeSubject.OTHER -> listOf("这道题的核心要点是什么？", "换个角度你会怎么思考？", "你能举一个类似的例子吗？")
                    }
                    Text("🤔 试着想一想：", style = MaterialTheme.typography.labelMedium)
                    prompts.forEach { p -> Text("• $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    if (m.hasVariation) {
                        // 已举一反三：展示笔记
                        if (m.variationNote.isNotEmpty()) {
                            Text("📝 我的举一反三笔记：", style = MaterialTheme.typography.labelMedium)
                            Text(m.variationNote, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        // 未举一反三：输入笔记 + 完成按钮
                        OutlinedTextField(
                            value = variationInput,
                            onValueChange = { variationInput = it },
                            label = { Text("写下你的思考/变式题（可选）") },
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.saveVariation(m, variationInput)
                                    app.pointApprovalRepository.submitApproval("VARIATION", "举一反三思考: ${m.title}", 3)
                                    mistake = app.mistakeRepository.getMistakeById(mistakeId)
                                    snackbarHostState.showSnackbar("💡 举一反三完成 +3分已提交家长审核")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("💡 完成举一反三思考 (+3分)") }
                    }
                }
            }

            // 复习记录
            if (reviews.isNotEmpty()) {
                Text("复习记录 (${reviews.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                reviews.take(5).forEach { review ->
                    val dateStr = com.hstudy.app.ui.dt.SimpleDateFormat("MM-dd HH:mm", com.hstudy.app.ui.dt.Locale.getDefault()).format(com.hstudy.app.ui.dt.Date(review.reviewedAt))
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$dateStr +${review.earnedPoints}分", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            } // end else (normal display mode)
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除错题") },
            text = { Text("确定要删除「${m.title}」吗？删除后不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.mistakeRepository.deleteMistake(m.id)
                            showDeleteConfirm = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

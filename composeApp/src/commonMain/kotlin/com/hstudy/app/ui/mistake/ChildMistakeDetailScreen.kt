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
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.dt.fileExists
import com.hstudy.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun ChildMistakeDetailScreen(
    mistakeId: Long,
    onNavigateBack: () -> Unit
) {
    val app = AppGraph
    val strings = LocalStrings.current.mistakeDetail
    val shared = LocalStrings.current.mistake
    val common = LocalStrings.current.common
    val displayLanguage = LocalDisplayLanguage.current
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
                title = strings.screenTitle(subjectEmoji),
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
                        Icon(Icons.Filled.Edit, contentDescription = strings.editContentDescription)
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
                        Icon(Icons.Filled.Delete, contentDescription = common.deleteContentDescription, tint = MaterialTheme.colorScheme.error)
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
                Text(strings.editHeading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // 科目选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MistakeSubject.entries.forEach { s ->
                        val label = when (s) {
                            MistakeSubject.MATH -> shared.subjectMath; MistakeSubject.CHINESE -> shared.subjectChinese
                            MistakeSubject.ENGLISH -> shared.subjectEnglish; MistakeSubject.SCIENCE -> shared.subjectScience
                            MistakeSubject.OTHER -> shared.subjectOther
                        }
                        FilterChip(selected = editSubject == s, onClick = { editSubject = s }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it },
                    label = { Text(strings.titleLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = editContent, onValueChange = { editContent = it },
                    label = { Text(strings.contentLabel) }, modifier = Modifier.fillMaxWidth().height(100.dp))
                OutlinedTextField(value = editWrongReason, onValueChange = { editWrongReason = it },
                    label = { Text(strings.wrongReasonLabel) }, modifier = Modifier.fillMaxWidth().height(80.dp))
                OutlinedTextField(value = editCorrectMethod, onValueChange = { editCorrectMethod = it },
                    label = { Text(strings.correctMethodLabel) }, modifier = Modifier.fillMaxWidth().height(80.dp))
                // 错误类型
                Text(strings.errorTypeLabel, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    mistakeErrorTypes.forEach { et ->
                        FilterChip(selected = editErrorType == et, onClick = { editErrorType = if (editErrorType == et) "" else et },
                            label = { Text(localizedPresetText(et, displayLanguage), style = MaterialTheme.typography.labelSmall) })
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
                            snackbarHostState.showSnackbar(strings.updatedMessage)
                        }
                    }) { Text(strings.saveButton) }
                    OutlinedButton(onClick = { isEditing = false }) { Text(common.cancelButton) }
                }
            } else {
            // ===== 正常显示模式 =====
            // 标题
            Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (m.isMastered) shared.masteredStatus else shared.unmasteredStatus) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                val dateStr = com.hstudy.app.ui.dt.SimpleDateFormat("yyyy-MM-dd", com.hstudy.app.ui.dt.Locale.getDefault()).format(com.hstudy.app.ui.dt.Date(m.createdAt))
                Text(strings.addedOn(dateStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 题目内容
            if (m.content.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.contentHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                        Text(strings.recallHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(strings.recallHint,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // 错误原因（主动回忆模式下需先显示答案）
            if ((answerRevealed || m.isMastered) && m.wrongReason.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.wrongReasonHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(m.wrongReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // 正确解法（主动回忆模式下需先显示答案）
            if ((answerRevealed || m.isMastered) && m.correctMethod.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.correctMethodHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(m.correctMethod, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            // 照片
            if (m.photoPath.isNotEmpty() && fileExists(m.photoPath)) {
                Text(strings.photoHeading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    AsyncImage(
                        model = m.photoPath,
                        contentDescription = shared.photoContentDescription,
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
                        Text(strings.masteredCard,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
                // 第一步：还没显示答案 → 引导显示答案
                !answerRevealed -> {
                    Button(
                        onClick = { answerRevealed = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.revealButton) }
                }
                // 第二步：已显示答案但未自评 → 自评做对/没记住
                !reviewDone -> {
                    Text(strings.selfCheckPrompt, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.reviewMistake(mistakeId, points = 0, note = "主动回忆-做对")
                                    app.pointApprovalRepository.submitApproval("MISTAKE_REVIEW", "错题复习做对: ${m.title}", 3)
                                    app.userPreferences.boostPetMood(2) // 复习让宠物开心
                                    com.hstudy.app.ui.common.AchievementManager.evaluate(app)
                                    reviewDone = true
                                    snackbarHostState.showSnackbar(strings.gotItMessage)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.gotItButton) }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.reviewMistake(mistakeId, points = 0, note = "主动回忆-没记住")
                                    app.pointApprovalRepository.submitApproval("MISTAKE_REVIEW", "错题复习: ${m.title}", 1)
                                    reviewDone = true
                                    snackbarHostState.showSnackbar(strings.missedMessage)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.missedButton) }
                    }
                }
                // 第三步：自评完成
                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(strings.reviewDoneCard,
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
                        Text(strings.variationTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (m.hasVariation) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.variationDone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // 学科引导思考问题
                    val prompts = when (m.subject) {
                        MistakeSubject.MATH -> strings.variationPromptsMath
                        MistakeSubject.CHINESE -> strings.variationPromptsChinese
                        MistakeSubject.ENGLISH -> strings.variationPromptsEnglish
                        MistakeSubject.SCIENCE -> strings.variationPromptsScience
                        MistakeSubject.OTHER -> strings.variationPromptsOther
                    }
                    Text(strings.variationThink, style = MaterialTheme.typography.labelMedium)
                    prompts.forEach { p -> Text("• $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    if (m.hasVariation) {
                        // 已举一反三：展示笔记
                        if (m.variationNote.isNotEmpty()) {
                            Text(strings.variationNotesHeading, style = MaterialTheme.typography.labelMedium)
                            Text(m.variationNote, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        // 未举一反三：输入笔记 + 完成按钮
                        OutlinedTextField(
                            value = variationInput,
                            onValueChange = { variationInput = it },
                            label = { Text(strings.variationInputLabel) },
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    app.mistakeRepository.saveVariation(m, variationInput)
                                    app.pointApprovalRepository.submitApproval("VARIATION", "举一反三思考: ${m.title}", 3)
                                    mistake = app.mistakeRepository.getMistakeById(mistakeId)
                                    snackbarHostState.showSnackbar(strings.variationDoneMessage)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(strings.variationButton) }
                    }
                }
            }

            // 复习记录
            if (reviews.isNotEmpty()) {
                Text(strings.reviewHistoryTitle(reviews.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                reviews.take(5).forEach { review ->
                    val dateStr = com.hstudy.app.ui.dt.SimpleDateFormat("MM-dd HH:mm", com.hstudy.app.ui.dt.Locale.getDefault()).format(com.hstudy.app.ui.dt.Date(review.reviewedAt))
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📖", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.reviewHistoryLine(dateStr, review.earnedPoints), style = MaterialTheme.typography.bodySmall)
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
            title = { Text(strings.deleteDialogTitle) },
            text = { Text(strings.deleteDialogText(m.title)) },
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
                ) { Text(common.deleteContentDescription) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(common.cancelButton) } }
        )
    }
}

package com.hstudy.app.ui.mistake

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.mistakeErrorTypes
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import coil3.compose.AsyncImage
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.dt.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMistakeAddScreen(
    onNavigateBack: () -> Unit
) {
    val strings = LocalStrings.current.mistakeAdd
    val shared = LocalStrings.current.mistake
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var wrongReason by rememberSaveable { mutableStateOf("") }
    var errorType by rememberSaveable { mutableStateOf("") }
    var correctMethod by rememberSaveable { mutableStateOf("") }
    var subjectName by rememberSaveable { mutableStateOf(MistakeSubject.MATH.name) }
    var subject by remember { mutableStateOf(MistakeSubject.MATH) }
    var photoPath by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // 从保存的名称恢复科目选择（进程恢复场景）
    LaunchedEffect(subjectName) {
        subject = try { MistakeSubject.valueOf(subjectName) } catch (_: Exception) { MistakeSubject.MATH }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 科目选择
            Text(strings.subjectLabel, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MistakeSubject.entries.forEach { s ->
                    val label = when (s) {
                        MistakeSubject.MATH -> shared.subjectMathChip
                        MistakeSubject.CHINESE -> shared.subjectChineseChip
                        MistakeSubject.ENGLISH -> shared.subjectEnglishChip
                        MistakeSubject.SCIENCE -> shared.subjectScienceChip
                        MistakeSubject.OTHER -> shared.subjectOtherChip
                    }
                    FilterChip(selected = subject == s, onClick = { subject = s; subjectName = s.name }, label = { Text(label) })
                }
            }

            // 题目摘要
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(strings.titleLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 题目详情
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text(strings.contentLabel) },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            // 错误原因
            OutlinedTextField(
                value = wrongReason, onValueChange = { wrongReason = it },
                label = { Text(strings.wrongReasonLabel) },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 错误类型（用于错因分析，针对性改进）
            Text(strings.errorTypeHint, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mistakeErrorTypes.forEach { type ->
                    FilterChip(
                        selected = errorType == type,
                        onClick = { errorType = if (errorType == type) "" else type },
                        label = { Text(type) }
                    )
                }
            }

            // 正确解法
            OutlinedTextField(
                value = correctMethod, onValueChange = { correctMethod = it },
                label = { Text(strings.correctMethodLabel) },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 拍照按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
Button(onClick = {
                    Platform.camera.takePhoto { path -> if (path != null) photoPath = path }
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.takePhotoButton)
                }
                if (photoPath.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(strings.photoTaken, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // 预览照片
            if (photoPath.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(
                        model = photoPath,
                        contentDescription = shared.photoContentDescription,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (title.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar(strings.missingTitleMessage) }
                        return@Button
                    }
                    isSaving = true
                    scope.launch {
                        app.mistakeRepository.addMistake(
                            MistakeEntity(
                                subject = subject,
                                title = title,
                                content = content,
                                wrongReason = wrongReason,
                                correctMethod = correctMethod,
                                photoPath = photoPath,
                                errorType = errorType
                            )
                        )
                        isSaving = false
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(strings.saveButton)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}



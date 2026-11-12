package com.hstudy.app.ui.proof

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.localizedPresetText
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.launch
import com.hstudy.app.ui.dt.SimpleDateFormat
import coil3.compose.AsyncImage
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.dt.*

@Composable
fun ChildProofCaptureScreen(
    taskId: Long,
    taskTitle: String,
    onNavigateBack: () -> Unit
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalStrings.current.childProofCapture
    val displayLanguage = LocalDisplayLanguage.current

    var photoPath by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = strings.title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 任务信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.taskLabel, style = MaterialTheme.typography.labelMedium)
                    Text(localizedPresetText(taskTitle, displayLanguage), style = MaterialTheme.typography.titleMedium)
                }
            }

            // 拍照按钮
            FilledTonalButton(
                onClick = {
                    Platform.camera.takePhoto { path -> if (path != null) photoPath = path }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.takePhotoButton)
            }

            // 照片预览
            if (photoPath.isNotEmpty() && fileExists(photoPath)) {
                Card(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    AsyncImage(
                        model = photoPath,
                        contentDescription = strings.photoContentDescription,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null,
                                modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(strings.emptyPhotoHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 备注
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(strings.noteLabel) },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 提交按钮
            Button(
                onClick = {
                    if (photoPath.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar(strings.needPhotoMessage) }
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        app.habitRepository.addProof(
                            CompletionProofEntity(
                                taskId = taskId,
                                photoPath = photoPath,
                                note = note,
                                date = today
                            )
                        )
                        isSubmitting = false
                        snackbarHostState.showSnackbar(strings.submittedMessage)
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(strings.submitButton)
            }

            // 提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(strings.tipsTitle, style = MaterialTheme.typography.labelMedium)
                    Text(strings.tipsBody, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}



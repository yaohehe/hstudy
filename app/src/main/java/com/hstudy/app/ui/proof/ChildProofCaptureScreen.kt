package com.hstudy.app.ui.proof

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hstudy.app.HStudyApp
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChildProofCaptureScreen(
    taskId: Long,
    taskTitle: String,
    onNavigateBack: () -> Unit
) {
    val app = HStudyApp.instance
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoPath by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            photoPath = copyUriToAppStorage(context, photoUri!!)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoPath = copyUriToAppStorage(context, it)
        }
    }

    Scaffold(
        topBar = { HStudyTopBar(title = "📷 提交完成证明", onNavigateBack = onNavigateBack) },
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
                    Text("📋 关联任务", style = MaterialTheme.typography.labelMedium)
                    Text(taskTitle, style = MaterialTheme.typography.titleMedium)
                }
            }

            // 拍照/选图按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        val uri = createPhotoUri(context)
                        if (uri != null) {
                            photoUri = uri
                            cameraLauncher.launch(uri)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拍照")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("从相册选")
                }
            }

            // 照片预览
            if (photoPath.isNotEmpty() && File(photoPath).exists()) {
                Card(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(File(photoPath)),
                        contentDescription = "证明照片",
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
                            Text("点击拍照或选择照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 备注
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("备注说明（可选）") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 提交按钮
            Button(
                onClick = {
                    if (photoPath.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("请先拍照或选择照片") }
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
                        snackbarHostState.showSnackbar("✅ 证明已提交，等待父母审核")
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("📤 提交完成证明")
            }

            // 提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("💡 温馨提示", style = MaterialTheme.typography.labelMedium)
                    Text("提交后父母会审核。证明用于留痕，积分请在完成打卡后等待家长审批到账。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun createPhotoUri(context: Context): Uri? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "HStudy_$timestamp.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/HStudy")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "HStudy")
            if (!dir.exists()) dir.mkdirs()
            Uri.fromFile(File(dir, "HStudy_$timestamp.jpg"))
        }
    } catch (e: Exception) { null }
}

private fun copyUriToAppStorage(context: Context, uri: Uri): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
    val file = File(dir, "proof_$timestamp.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    return file.absolutePath
}

package com.hstudy.app.ui.mistake

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.mistakeErrorTypes
import com.hstudy.app.ui.common.HStudyTopBar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.hstudy.app.ui.dt.SimpleDateFormat
import com.hstudy.app.ui.dt.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMistakeAddScreen(
    onNavigateBack: () -> Unit
) {
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var wrongReason by rememberSaveable { mutableStateOf("") }
    var errorType by rememberSaveable { mutableStateOf("") }
    var correctMethod by rememberSaveable { mutableStateOf("") }
    var subjectName by rememberSaveable { mutableStateOf(MistakeSubject.MATH.name) }
    var subject by remember { mutableStateOf(MistakeSubject.MATH) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoPath by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // 从保存的名称恢复科目选择（进程恢复场景）
    LaunchedEffect(subjectName) {
        subject = try { MistakeSubject.valueOf(subjectName) } catch (_: Exception) { MistakeSubject.MATH }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            photoPath = saveUriToAppStorage(context, photoUri!!)
        }
    }

    Scaffold(
        topBar = {
            HStudyTopBar(title = "➕ 添加错题", onNavigateBack = onNavigateBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 科目选择
            Text("科目", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MistakeSubject.entries.forEach { s ->
                    val label = when (s) {
                        MistakeSubject.MATH -> "🔢 数学"
                        MistakeSubject.CHINESE -> "📝 语文"
                        MistakeSubject.ENGLISH -> "🔤 英语"
                        MistakeSubject.SCIENCE -> "🔬 科学"
                        MistakeSubject.OTHER -> "📋 其他"
                    }
                    FilterChip(selected = subject == s, onClick = { subject = s; subjectName = s.name }, label = { Text(label) })
                }
            }

            // 题目摘要
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("题目摘要") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 题目详情
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text("题目详细描述") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            // 错误原因
            OutlinedTextField(
                value = wrongReason, onValueChange = { wrongReason = it },
                label = { Text("❌ 错误原因分析") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 错误类型（用于错因分析，针对性改进）
            Text("错误类型（可选，用于错因分析）", style = MaterialTheme.typography.titleSmall)
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
                label = { Text("✅ 正确解法") },
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )

            // 拍照按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val uri = createImageUri(context)
                    if (uri != null) {
                        photoUri = uri
                        cameraLauncher.launch(uri)
                    }
                }) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拍照记录")
                }
                if (photoPath.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("📷 已拍照", color = MaterialTheme.colorScheme.secondary)
                }
            }

            // 预览照片
            if (photoPath.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(File(photoPath)),
                        contentDescription = "错题照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (title.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("请输入题目摘要") }
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
                Text("保存错题")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 创建用于拍照的 Uri
 */
private fun createImageUri(context: Context): Uri? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "HStudy_$timestamp.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/HStudy")
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "HStudy")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "HStudy_$timestamp.jpg")
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * 将 Uri 内容复制到应用内部存储并返回路径
 */
private fun saveUriToAppStorage(context: Context, uri: Uri): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "mistake_$timestamp.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    return file.absolutePath
}

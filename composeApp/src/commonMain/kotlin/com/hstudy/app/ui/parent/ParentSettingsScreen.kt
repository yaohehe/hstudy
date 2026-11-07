package com.hstudy.app.ui.parent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hstudy.app.ui.theme.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hstudy.app.AppGraph
import com.hstudy.app.data.backup.BackupHelper
import com.hstudy.app.i18n.AppLanguage
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.appLanguageFromStorage
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.restartApp
import com.hstudy.app.ui.common.HStudyTopBar
import com.hstudy.app.ui.common.ResponsiveContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ParentSettingsScreen(
    onNavigateBack: () -> Unit,
    windowSizeClass: WindowSizeClass
) {
    val strings = LocalStrings.current.settings
    val app = AppGraph
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPassword by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }
    var childProgress by remember { mutableStateOf<com.hstudy.app.data.sync.ChildProgressSummary?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }

    // 导入孩子进度报告：跨平台改用 Platform.fileSharer 文本选择器
    // （替代 ActivityResultContracts.OpenDocument + context/uri 版本）
    val importProgress: () -> Unit = {
        Platform.fileSharer.pickTextFile { text ->
            if (text != null) {
                scope.launch {
                    isTransferring = true
                    val result = com.hstudy.app.data.sync.FamilySyncHelper.importProgressReport(text)
                    if (result.isSuccess) {
                        childProgress = result.getOrNull()
                        snackbarHostState.showSnackbar("✅ 已获取孩子最新进度")
                    } else {
                        snackbarHostState.showSnackbar("导入失败：${result.exceptionOrNull()?.message}")
                    }
                    isTransferring = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        hasPassword = app.userPreferences.hasParentPassword.first()
        deviceName = app.userPreferences.deviceName.first() ?: ""
    }

    // 密码设置对话框
    var showPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { HStudyTopBar(title = "⚙️ 家长设置", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ResponsiveContent(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // ===== 家长密码 =====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🔒 家长密码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (hasPassword) "已设置密码。孩子切换到家长身份或修改规则时需输入密码。"
                                else "未设置密码。建议设置密码，防止孩子随意修改积分和任务规则。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showPasswordDialog = true }) {
                                    Text(if (hasPassword) "修改密码" else "设置密码")
                                }
                                if (hasPassword) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            app.userPreferences.clearParentPassword()
                                            hasPassword = false
                                            snackbarHostState.showSnackbar("已清除家长密码")
                                        }
                                    }) {
                                        Text("清除密码")
                                    }
                                }
                            }
                        }
                    }

                    // ===== 每周学习周报推送 =====
                    val weeklyReportEnabled by app.userPreferences.weeklyReportEnabled.collectAsState(initial = false)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📊 每周学习周报", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("每周日晚定时推送孩子本周学习情况（打卡/专注/复习/积分），无需打开 App 即可掌握。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = weeklyReportEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        app.userPreferences.setWeeklyReportEnabled(enabled)
                                        if (enabled) {
                                            WeeklyReportReceiver.schedule()
                                            snackbarHostState.showSnackbar("✅ 已开启每周学习周报（每周日晚推送）")
                                        } else {
                                            WeeklyReportReceiver.cancel()
                                            snackbarHostState.showSnackbar("已关闭每周学习周报")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // ===== 数据备份与恢复 =====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("💾 数据备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("定期备份孩子的学习数据（积分/错题/专注/成就等），换设备或误卸载时可恢复。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    isBackingUp = true
                                    scope.launch {
                                        val result = BackupHelper.backup()
                                        isBackingUp = false
                                        result.fold(
                                            onSuccess = { snackbarHostState.showSnackbar("✅ 备份成功！文件已保存到应用目录") },
                                            onFailure = { snackbarHostState.showSnackbar("❌ 备份失败：${it.message}") }
                                        )
                                    }
                                }, enabled = !isBackingUp) {
                                    Text(if (isBackingUp) "备份中..." else "备份数据")
                                }
                                OutlinedButton(onClick = { showRestoreConfirm = true },
                                    enabled = BackupHelper.hasBackup()) {
                                    Text("恢复数据")
                                }
                            }
                        }
                    }
                    if (showRestoreConfirm) {
                        AlertDialog(
                            onDismissRequest = { showRestoreConfirm = false },
                            title = { Text("确认恢复数据") },
                            text = { Text("恢复将用备份覆盖当前所有数据，并重启应用。此操作不可撤销，确定继续吗？") },
                            confirmButton = {
                                Button(onClick = {
                                    showRestoreConfirm = false
                                    scope.launch {
                                        val result = BackupHelper.restore()
                                        result.fold(
                                            onSuccess = {
                                                // 与原 v2.88 一致：先提示，再延时让 Snackbar 显示，然后重启进程
                                                snackbarHostState.showSnackbar("✅ 恢复成功，即将重启应用...")
                                                kotlinx.coroutines.delay(1200)
                                                if (!restartApp()) {
                                                    // iOS 系统不允许应用自重启，回退为提示用户手动重启
                                                    snackbarHostState.showSnackbar("请重新启动 App 以生效")
                                                }
                                            },
                                            onFailure = { snackbarHostState.showSnackbar("❌ 恢复失败：${it.message}") }
                                        )
                                    }
                                }) { Text("确认恢复") }
                            },
                            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") } }
                        )
                    }

                    // ===== 家长远程控制（配对后家长在自己手机上管理孩子设备）=====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("📲 家长远程控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "孩子有自己的手机/平板？配对后您可以在自己手机上查看孩子进度、推送任务设置。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 操作指引
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("使用方法", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("📤 推送设置：把您配置的任务/奖励发送给孩子设备应用",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("📥 查看进度：接收孩子发来的学习进度报告",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("💡 传输方式：蓝牙/WiFi直连/微信/邮件等任何方式均可",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // 操作按钮
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 推送设置给孩子
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isTransferring = true
                                            val result = com.hstudy.app.data.sync.FamilySyncHelper.exportControlConfig()
                                            if (result.isSuccess) {
                                                com.hstudy.app.data.sync.FamilySyncHelper.shareFile(
                                                    result.getOrNull()!!, "发送家长设置到孩子设备"
                                                )
                                                snackbarHostState.showSnackbar("✅ 设置已导出，请发送给孩子")
                                            } else {
                                                snackbarHostState.showSnackbar("导出失败：${result.exceptionOrNull()?.message}")
                                            }
                                            isTransferring = false
                                        }
                                    },
                                    enabled = !isTransferring
                                ) { Text("📤 推送设置给孩子") }

                                // 查看孩子进度
                                OutlinedButton(
                                    onClick = importProgress,
                                    enabled = !isTransferring
                                ) { Text("📥 查看孩子进度") }
                            }

                            if (isTransferring) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在处理…", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // 孩子进度展示（导入后显示）
                            if (childProgress != null) {
                                val cp = childProgress!!
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("📊 ${cp.deviceName}的学习进度", style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column { Text("⭐ 总积分", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.totalPoints}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                            Column { Text("🔥 连续打卡", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.streak}天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                            Column { Text("🍅 总专注", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.totalFocusMinutes}分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column { Text("✏️ 总打卡", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.totalCompletions}次", style = MaterialTheme.typography.bodyMedium) }
                                            Column { Text("📚 错题掌握", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.masteredMistakes}/${cp.totalMistakes}", style = MaterialTheme.typography.bodyMedium) }
                                            Column { Text("📅 今日打卡", style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.todayCompletedTasks}个", style = MaterialTheme.typography.bodyMedium) }
                                        }
                                    }
                                }
                            }

                            // 设备名称
                            OutlinedTextField(
                                value = deviceName,
                                onValueChange = { deviceName = it },
                                label = { Text("本设备名称") },
                                placeholder = { Text("如：妈妈的手机") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            TextButton(onClick = {
                                val name = deviceName.ifBlank { "家长设备" }
                                scope.launch {
                                    app.userPreferences.setDeviceName(name)
                                    snackbarHostState.showSnackbar("已保存设备名称：$name")
                                }
                            }) { Text("保存设备名称") }
                        }
                    }

                    // ===== 语言 =====
                    // 本页既有习惯：偏好读写走 AppGraph.userPreferences（全工程唯一 DataStore 实例，
                    // 不在这里 new UserPreferences()），写入用 rememberCoroutineScope 的 scope，
                    // 读取用裸 collectAsState（对照上面的 weeklyReportEnabled）——不加 catch 与本页
                    // 其它订阅失败模式一致；App.kt 根组合才需要 catch（那里崩了等于整 app 白屏）。
                    val storedLanguage by app.userPreferences.appLanguage.collectAsState(initial = null)
                    val currentLanguage = appLanguageFromStorage(storedLanguage)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🌐 ${strings.languageTitle}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(strings.languageNote,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = currentLanguage == AppLanguage.SYSTEM,
                                    onClick = {
                                        scope.launch { app.userPreferences.setAppLanguage(AppLanguage.SYSTEM) }
                                    },
                                    label = { Text(strings.languageSystem) }
                                )
                                FilterChip(
                                    selected = currentLanguage == AppLanguage.ZH,
                                    onClick = {
                                        scope.launch { app.userPreferences.setAppLanguage(AppLanguage.ZH) }
                                    },
                                    label = { Text(strings.languageZh) }
                                )
                                FilterChip(
                                    selected = currentLanguage == AppLanguage.EN,
                                    onClick = {
                                        scope.launch { app.userPreferences.setAppLanguage(AppLanguage.EN) }
                                    },
                                    label = { Text(strings.languageEn) }
                                )
                            }
                            Text(strings.partialTranslationNote,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordSetupDialog(
            hasExisting = hasPassword,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { pwd ->
                scope.launch {
                    app.userPreferences.setParentPassword(pwd)
                    hasPassword = true
                    showPasswordDialog = false
                    snackbarHostState.showSnackbar("家长密码已设置")
                }
            }
        )
    }
}

@Composable
private fun PasswordSetupDialog(
    hasExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExisting) "修改家长密码" else "设置家长密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it; error = null },
                    label = { Text("新密码（建议4-6位数字）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPwd, onValueChange = { confirmPwd = it; error = null },
                    label = { Text("确认密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pwd.length < 4 -> error = "密码至少4位"
                    pwd != confirmPwd -> error = "两次输入的密码不一致"
                    else -> onConfirm(pwd)
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

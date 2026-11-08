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
import com.hstudy.app.i18n.CanonicalParentSettingsText
import com.hstudy.app.i18n.DisplayLanguage
import com.hstudy.app.i18n.LocalDisplayLanguage
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
    val common = LocalStrings.current.common
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
                        snackbarHostState.showSnackbar(strings.progressImportedMessage)
                    } else {
                        snackbarHostState.showSnackbar(strings.childProgressImportFailedMessage(result.exceptionOrNull()?.message))
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
        topBar = { HStudyTopBar(title = strings.pageTitle, onNavigateBack = onNavigateBack) },
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
                            Text(strings.passwordCardTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (hasPassword) strings.passwordSetNote
                                else strings.passwordMissingNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showPasswordDialog = true }) {
                                    Text(if (hasPassword) strings.changePasswordButton else strings.setPasswordButton)
                                }
                                if (hasPassword) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            app.userPreferences.clearParentPassword()
                                            hasPassword = false
                                            snackbarHostState.showSnackbar(strings.passwordClearedMessage)
                                        }
                                    }) {
                                        Text(strings.clearPasswordButton)
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
                                Text(strings.weeklyReportTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(strings.weeklyReportNote,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = weeklyReportEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        app.userPreferences.setWeeklyReportEnabled(enabled)
                                        if (enabled) {
                                            WeeklyReportReceiver.schedule()
                                            snackbarHostState.showSnackbar(strings.weeklyReportOnMessage)
                                        } else {
                                            WeeklyReportReceiver.cancel()
                                            snackbarHostState.showSnackbar(strings.weeklyReportOffMessage)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // ===== 数据备份与恢复 =====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(strings.backupCardTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(strings.backupNote,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    isBackingUp = true
                                    scope.launch {
                                        val result = BackupHelper.backup()
                                        isBackingUp = false
                                        result.fold(
                                            onSuccess = { snackbarHostState.showSnackbar(strings.backupSuccessMessage) },
                                            onFailure = { snackbarHostState.showSnackbar(strings.backupFailureMessage(it.message)) }
                                        )
                                    }
                                }, enabled = !isBackingUp) {
                                    Text(if (isBackingUp) strings.backupRunningLabel else strings.backupButton)
                                }
                                OutlinedButton(onClick = { showRestoreConfirm = true },
                                    enabled = BackupHelper.hasBackup()) {
                                    Text(strings.restoreButton)
                                }
                            }
                        }
                    }
                    if (showRestoreConfirm) {
                        AlertDialog(
                            onDismissRequest = { showRestoreConfirm = false },
                            title = { Text(strings.restoreDialogTitle) },
                            text = { Text(strings.restoreDialogNote) },
                            confirmButton = {
                                Button(onClick = {
                                    showRestoreConfirm = false
                                    scope.launch {
                                        val result = BackupHelper.restore()
                                        result.fold(
                                            onSuccess = {
                                                // 与原 v2.88 一致：先提示，再延时让 Snackbar 显示，然后重启进程
                                                snackbarHostState.showSnackbar(strings.restoreSuccessMessage)
                                                kotlinx.coroutines.delay(1200)
                                                if (!restartApp()) {
                                                    // iOS 系统不允许应用自重启，回退为提示用户手动重启
                                                    snackbarHostState.showSnackbar(strings.restartAppNote)
                                                }
                                            },
                                            onFailure = { snackbarHostState.showSnackbar(strings.restoreFailureMessage(it.message)) }
                                        )
                                    }
                                }) { Text(strings.restoreConfirmButton) }
                            },
                            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text(common.cancelButton) } }
                        )
                    }

                    // ===== 家长远程控制（配对后家长在自己手机上管理孩子设备）=====
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(strings.remoteControlTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                strings.remoteControlNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 操作指引
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(strings.howToTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(strings.pushStepNote,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(strings.viewStepNote,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(strings.transportNote,
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
                                                    result.getOrNull()!!, strings.exportChooserTitle
                                                )
                                                snackbarHostState.showSnackbar(strings.settingsExportedMessage)
                                            } else {
                                                snackbarHostState.showSnackbar(strings.exportFailedMessage(result.exceptionOrNull()?.message))
                                            }
                                            isTransferring = false
                                        }
                                    },
                                    enabled = !isTransferring
                                ) { Text(strings.pushSettingsButton) }

                                // 查看孩子进度
                                OutlinedButton(
                                    onClick = importProgress,
                                    enabled = !isTransferring
                                ) { Text(strings.viewProgressButton) }
                            }

                            if (isTransferring) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strings.processingLabel, style = MaterialTheme.typography.bodySmall)
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
                                        Text(strings.progressCardTitle(strings.deviceNameDisplayLabel(cp.deviceName)), style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column { Text(strings.progressPointsLabel, style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.totalPoints}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                            Column { Text(strings.progressStreakLabel, style = MaterialTheme.typography.labelSmall)
                                                Text(strings.streakDaysValue(cp.streak), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                            Column { Text(strings.progressFocusLabel, style = MaterialTheme.typography.labelSmall)
                                                Text(strings.progressFocusMinutesValue(cp.totalFocusMinutes, common.minuteLabel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column { Text(strings.progressCheckinLabel, style = MaterialTheme.typography.labelSmall)
                                                Text(strings.completionsValue(cp.totalCompletions), style = MaterialTheme.typography.bodyMedium) }
                                            Column { Text(strings.progressMistakesLabel, style = MaterialTheme.typography.labelSmall)
                                                Text("${cp.masteredMistakes}/${cp.totalMistakes}", style = MaterialTheme.typography.bodyMedium) }
                                            Column { Text(strings.progressTodayCheckinLabel, style = MaterialTheme.typography.labelSmall)
                                                Text(strings.todayCheckinsValue(cp.todayCompletedTasks), style = MaterialTheme.typography.bodyMedium) }
                                        }
                                    }
                                }
                            }

                            // 设备名称
                            OutlinedTextField(
                                value = deviceName,
                                onValueChange = { deviceName = it },
                                label = { Text(strings.deviceNameLabel) },
                                placeholder = { Text(strings.deviceNamePlaceholder) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            TextButton(onClick = {
                                val name = deviceName.ifBlank { CanonicalParentSettingsText.DEVICE_NAME_FALLBACK }
                                scope.launch {
                                    app.userPreferences.setDeviceName(name)
                                    snackbarHostState.showSnackbar(strings.deviceNameSavedMessage(strings.deviceNameDisplayLabel(name)))
                                }
                            }) { Text(strings.saveDeviceNameButton) }
                        }
                    }

                    // ===== 语言 =====
                    // 本页既有习惯：偏好读写走 AppGraph.userPreferences（全工程唯一 DataStore 实例，
                    // 不在这里 new UserPreferences()），写入用 rememberCoroutineScope 的 scope，
                    // 读取用裸 collectAsState（对照上面的 weeklyReportEnabled）——不加 catch 与本页
                    // 其它订阅失败模式一致；App.kt 根组合才需要 catch（那里崩了等于整 app 白屏）。
                    // initial 用 AppGraph.bootstrap() 预读的快照而不是 null：null 会被
                    // appLanguageFromStorage 解析成 SYSTEM，于是「存了 en 的设备」在 DataStore 首次
                    // emission 之前看到高亮的是「跟随系统」，随后跳一次。App.kt 根组合的 initial 用的是
                    // 同一个值，两处必须一致，否则首帧文案与首帧选中项对不上。
                    // 局限同 App.kt，且**Ruling 46 之后已经不再是"会话内不回写"**：AppGraph 现在订阅
                    // appLanguage 流刷新那个快照，所以切完语言后的任何一次重建都回锚到**当前**选择。
                    // 注：本段注释用「」而不用英文双引号。理由是读代码的人：引号里的中文看起来
                    // 像一条待迁移的界面字面量，得停下来判断它归不归文案表管；「」在中文排版里
                    // 明确表示「被指称的词」而不是「要显示的话」，这个判断就不用做了。
                    // （原先写的理由是 scripts/i18n_inventory.py 会把注释里的中文数进清单——
                    //  Ruling 18 之后脚本会剥掉 // 之后的内容，那条已经成立不了了，约定本身照旧。）
                    val storedLanguage by app.userPreferences.appLanguage.collectAsState(initial = AppGraph.startupLanguage.value)
                    val currentLanguage = appLanguageFromStorage(storedLanguage)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // emoji 属于表里的文案（「🌐 语言」/「🌐 Language」），不在页面里拼接：
                            // 该页既有四个标题字面量本来就含 emoji，Task 11 按整串迁移，拼在代码里会分叉。
                            Text(strings.languageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            // partialTranslationNote 只在英文态出现（spec §7「英文态在该页附一行说明」）。
                            // 这句话的作用对象是英文读者：他在这里看到界面仍是中文，需要知道「还没适配」
                            // 而不是「功能坏了」。中文态显示同一句是不通的，Task 11 完成后更是假话，
                            // 所以不要把它「简化」回无条件渲染。
                            // 生效语言从根读（LocalDisplayLanguage），不在本页重算
                            // appLanguageFromStorage(...).resolveWith(systemLanguageTag())：
                            // 那等于第二份解析实现，会和 App.kt 漂移。
                            if (LocalDisplayLanguage.current == DisplayLanguage.EN) {
                                Text(strings.partialTranslationNote,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                    snackbarHostState.showSnackbar(strings.passwordSetMessage)
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
    val strings = LocalStrings.current.settings
    val common = LocalStrings.current.common

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExisting) strings.passwordDialogEditTitle else strings.passwordDialogSetTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it; error = null },
                    label = { Text(strings.newPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPwd, onValueChange = { confirmPwd = it; error = null },
                    label = { Text(strings.confirmPasswordLabel) },
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
                    pwd.length < 4 -> error = strings.passwordTooShortError
                    pwd != confirmPwd -> error = strings.passwordMismatchError
                    else -> onConfirm(pwd)
                }
            }) { Text(strings.passwordOkButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(common.cancelButton) } }
    )
}

package com.hstudy.app.platform

import android.content.Intent
import com.hstudy.app.HStudyApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual val systemFileSystem: FileSystem get() = FileSystem.SYSTEM

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** 与 v2.88 的 `BackupHelper.restartApp(context)` 逐句一致：launch intent + CLEAR_TOP/NEW_TASK + 结束进程 */
actual fun restartApp(): Boolean {
    val context = HStudyApplication.instance
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
    return true
}

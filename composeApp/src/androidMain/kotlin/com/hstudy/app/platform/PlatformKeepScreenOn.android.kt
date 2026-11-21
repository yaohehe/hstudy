package com.hstudy.app.platform

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Android 实现：经 view 绑定取宿主窗口——从 `LocalView.current.context` 沿 [ContextWrapper]
 * 链上溯到 [Activity]，计时中 `addFlags(FLAG_KEEP_SCREEN_ON)`，enabled 翻转或组合销毁时
 * `clearFlags` 释放（息屏策略交还系统）。
 *
 * 不用 `View.keepScreenOn`（API 33+ 才有，本应用 minSdk 26）；
 * MainActivity 直接 `setContent`，context 链必然含 Activity，取不到窗口时静默降级为无常亮
 * （与仓内平台服务"未安装实现静默无操作"的口径一致）。
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.findActivityWindow()
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun View.findActivityWindow(): android.view.Window? {
    var ctx: android.content.Context? = context
    while (ctx != null) {
        if (ctx is Activity) return ctx.window
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}

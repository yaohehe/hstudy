package com.hstudy.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * iOS 实现：`UIApplication.sharedApplication.idleTimerDisabled`——UIApplication 全局可得，
 * 不需要 UI 句柄注入，因此本侧**不是 no-op**，与 Android 行为对称：
 * enabled=true 期间阻止系统自动息屏，翻转/组合销毁时复位（释放策略）。
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}

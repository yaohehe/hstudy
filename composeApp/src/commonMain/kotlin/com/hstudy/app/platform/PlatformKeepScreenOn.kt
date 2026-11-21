package com.hstudy.app.platform

import androidx.compose.runtime.Composable

/**
 * 屏幕常亮（v3.3.1 D-06，指令 §4.2 第 3 点）：番茄钟计时期间保持屏幕不熄，
 * 结束/离开页面（组合销毁）自动释放，交还系统息屏策略。
 *
 * 与 [Platform.camera] 等既有平台服务不同，这里必须走 expect/actual：
 * 常亮开关绑的是"当前组合所在的窗口/UIApplication"，只有组合层能拿到，
 * 注入式接口（Platform.xxx）没有 view/window 句柄可用。
 *
 * Android actual：`LocalView` 上溯到宿主 Activity，`window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)`；
 * iOS actual：`UIApplication.sharedApplication.idleTimerDisabled`。
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

package com.hstudy.app.platform

import androidx.compose.ui.window.ComposeUIViewController
import com.hstudy.app.App
import com.hstudy.app.AppGraph
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIViewController

/**
 * iOS 平台入口：Swift 侧在 App.init 调用（导出为 IosPlatformKt.doInitIosPlatform()）。
 * 初始化 Platform 服务后执行 AppGraph.bootstrap（默认数据补全 + 提醒恢复）。
 */
fun initIosPlatform() {
    Platform.init(
        filesDir = NSHomeDirectory() + "/Documents",
        cacheDir = NSTemporaryDirectory(),
        notifications = IosNotificationScheduler(),
        fileSharer = IosFileSharer(),
        camera = IosCameraLauncher(),
        deviceDiscovery = IosDeviceDiscovery()
    )
    // 番茄钟震动/提示音/可见进度提醒（iOS 侧实现）
    installDeviceFeedback(IosDeviceFeedback())
    AppGraph.bootstrap()
}

/** Swift 侧 ContentView.makeUIViewController 调用（导出为 IosPlatformKt.MainViewController()） */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
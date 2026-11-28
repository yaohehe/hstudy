package com.hstudy.app.platform

import androidx.compose.ui.window.ComposeUIViewController
import com.hstudy.app.App
import com.hstudy.app.AppGraph
import com.hstudy.app.platform.Analytics
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIViewController

/**
 * iOS 平台入口：Swift 侧在 App.init 调用（导出为 IosPlatformKt.doInitIosPlatform(analytics:appChannel:)）。
 * 初始化 Platform 服务后执行 AppGraph.bootstrap（默认数据补全 + 提醒恢复）。
 */
fun initIosPlatform(analytics: Analytics, appChannel: String) {
    Platform.appChannel = appChannel
    Platform.init(
        filesDir = NSHomeDirectory() + "/Documents",
        cacheDir = NSTemporaryDirectory(),
        notifications = IosNotificationScheduler(),
        fileSharer = IosFileSharer(),
        camera = IosCameraLauncher(),
        deviceDiscovery = IosDeviceDiscovery(),
        analytics = analytics
    )
    // 番茄钟震动/提示音/可见进度提醒（iOS 侧实现）
    installDeviceFeedback(IosDeviceFeedback())
    AppGraph.bootstrap()
}

/** Called by the Swift notification delegate for cold-start and warm-start routes. */
fun handleIosNotificationRoute(route: String?) {
    if (route != null && NotificationRoutes.isAllowed(route)) {
        Platform.pendingRoute = route
    }
}

/** Swift 侧 ContentView.makeUIViewController 调用（导出为 IosPlatformKt.MainViewController()） */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

package com.hstudy.app.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/** 取当前最顶层 ViewController（分享面板/相机/文件选择器都挂在它上面） */
internal fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    @Suppress("DEPRECATION")
    var root = app.keyWindow?.rootViewController
    if (root == null) {
        @Suppress("DEPRECATION")
        root = (app.windows.firstOrNull() as? UIWindow)?.rootViewController
    }
    while (root?.presentedViewController != null) {
        root = root.presentedViewController
    }
    return root
}
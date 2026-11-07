@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hstudy.app.platform

import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIModalPresentationStyle
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/**
 * iOS 文件分享：写临时文件后弹 UIActivityViewController；
 * 导入用 UIDocumentPickerViewController（public.json/public.text），delegate 回调内容。
 */
class IosFileSharer : FileSharer {

    // delegate 是 weak 引用，必须强持有直到回调完成
    private var pickerDelegate: PickerDelegate? = null

    override fun shareTextFile(fileName: String, content: String, mimeType: String) {
        val path = NSTemporaryDirectory() + fileName
        @Suppress("DEPRECATION")
        val ok = (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!ok) return
        val root = topViewController() ?: return
        val url = NSURL.fileURLWithPath(path)
        val vc = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // iPad 上分享面板没有 popover 锚点会直接抛异常；这里用 pageSheet 形式呈现，
        // 无需锚点（新版 SDK 未把 popoverPresentationController 暴露给 cinterop）。
        vc.modalPresentationStyle = UIModalPresentationStyle.UIModalPresentationPageSheet
        root.presentViewController(vc, animated = true, completion = null)
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        val root = topViewController()
        if (root == null) {
            onResult(null)
            return
        }
        @Suppress("DEPRECATION")
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.json", "public.text", "public.data"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )
        val delegate = PickerDelegate { result ->
            pickerDelegate = null
            onResult(result)
        }
        pickerDelegate = delegate
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        root.presentViewController(picker, animated = true, completion = null)
    }

    private class PickerDelegate(
        private val onResult: (String?) -> Unit
    ) : NSObject(), UIDocumentPickerDelegateProtocol {

        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            var content: String? = null
            if (url != null) {
                val scoped = url.startAccessingSecurityScopedResource()
                try {
                    content = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) as? String
                } finally {
                    if (scoped) url.stopAccessingSecurityScopedResource()
                }
            }
            onResult(content)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onResult(null)
        }
    }
}
package com.hstudy.app.platform

import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS 拍照：UIImagePickerController（camera），JPEG 保存到 Documents/photos/，回调绝对路径。
 * 需要 Info.plist 的 NSCameraUsageDescription（已在 iosApp/Info.plist 配置）。
 */
class IosCameraLauncher : CameraLauncher {

    // delegate 是 weak 引用，必须强持有直到回调完成
    private var cameraDelegate: CameraDelegate? = null

    override fun takePhoto(onResult: (String?) -> Unit) {
        val root = topViewController()
        if (root == null ||
            !UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
        ) {
            // 无相机（模拟器）或无界面时回调 null
            onResult(null)
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val delegate = CameraDelegate { path ->
            cameraDelegate = null
            onResult(path)
        }
        cameraDelegate = delegate
        picker.delegate = delegate
        root.presentViewController(picker, animated = true, completion = null)
    }

    private class CameraDelegate(
        private val onResult: (String?) -> Unit
    ) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            var path: String? = null
            if (image != null) {
                val dir = NSHomeDirectory() + "/Documents/photos"
                NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
                val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                val file = "$dir/photo_$timestamp.jpg"
                val data = UIImageJPEGRepresentation(image, 0.9)
                if (data != null && data.writeToFile(file, atomically = true)) {
                    path = file
                }
            }
            picker.dismissViewControllerAnimated(true, completion = null)
            onResult(path)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, completion = null)
            onResult(null)
        }
    }
}
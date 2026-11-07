package com.hstudy.app.platform

import android.content.Context
import android.content.Intent

/**
 * Android 拍照入口：交给透明辅助 CameraCaptureActivity 处理权限申请 + 拍照 + 结果回传。
 */
class AndroidCameraLauncher(private val context: Context) : CameraLauncher {

    override fun takePhoto(onResult: (String?) -> Unit) {
        CameraCaptureActivity.callback = onResult
        try {
            context.startActivity(
                Intent(context, CameraCaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            CameraCaptureActivity.callback = null
            onResult(null)
        }
    }
}
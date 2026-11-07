package com.hstudy.app.platform

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * 透明辅助 Activity：申请相机权限 → MediaStore.ACTION_IMAGE_CAPTURE，
 * 照片输出到 cacheDir/photos/（FileProvider URI），回调返回绝对路径；取消/失败回调 null。
 */
class CameraCaptureActivity : Activity() {

    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoFile = savedInstanceState?.getString(STATE_PHOTO_PATH)?.let { File(it) }
        if (savedInstanceState != null && callback == null) {
            // 进程被回收后回调已丢失，直接结束
            finish()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                deliver(null)
                finish()
            }
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            photoFile = file
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("photo", uri)
            }
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            deliver(null)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAMERA) {
            val path = if (resultCode == RESULT_OK) {
                photoFile?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
            } else {
                null
            }
            deliver(path)
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, photoFile?.absolutePath)
    }

    override fun onDestroy() {
        super.onDestroy()
        callback?.let {
            callback = null
            it(null)
        }
    }

    private fun deliver(value: String?) {
        val cb = callback
        callback = null
        cb?.invoke(value)
    }

    companion object {
        /** 由 AndroidCameraLauncher.takePhoto 设置，一次性消费 */
        var callback: ((String?) -> Unit)? = null
        private const val REQ_PERMISSION = 4201
        private const val REQ_CAMERA = 4202
        private const val STATE_PHOTO_PATH = "photo_path"
    }
}
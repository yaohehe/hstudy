package com.hstudy.app.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 透明辅助 Activity：调起系统文件选择器（ACTION_OPEN_DOCUMENT）导入文本，
 * 通过静态回调把内容交还给共享层；取消/失败回调 null。
 */
class FilePickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // 进程被回收后恢复：无法找回回调，直接结束
            finish()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "text/*"))
        }
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: Exception) {
            deliver(null)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK) {
            val uri = data?.data
            val text = if (resultCode == RESULT_OK && uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            deliver(text)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 兜底：异常销毁时确保回调不悬挂
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
        /** 由 AndroidFileSharer.pickTextFile 设置，一次性消费 */
        var callback: ((String?) -> Unit)? = null
        private const val REQ_PICK = 4101
    }
}
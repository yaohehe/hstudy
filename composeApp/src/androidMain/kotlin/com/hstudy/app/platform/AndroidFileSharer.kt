package com.hstudy.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android 文件分享：写入 cacheDir/shared/ 后经 FileProvider 弹系统分享面板；
 * 导入走透明辅助 Activity（FilePickerActivity）调起系统文件选择器。
 */
class AndroidFileSharer(private val context: Context) : FileSharer {

    override fun shareTextFile(fileName: String, content: String, mimeType: String) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "分享文件")
            val activity = ActivityTracker.currentActivity
            if (activity != null) {
                activity.startActivity(chooser)
            } else {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        FilePickerActivity.callback = onResult
        try {
            context.startActivity(
                Intent(context, FilePickerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            FilePickerActivity.callback = null
            onResult(null)
        }
    }
}
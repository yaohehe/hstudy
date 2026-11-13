package com.hstudy.app.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hstudy.app.i18n.ShareStrings
import com.hstudy.app.i18n.currentDisplayLanguageSnapshot
import java.io.File

/**
 * Android 文件分享：写入 cacheDir/shared/ 后经 FileProvider 弹系统分享面板；
 * 导入走透明辅助 Activity（FilePickerActivity）调起系统文件选择器。
 *
 * 分享面板标题（W9 Task 2，spec §1/§3 的"分享标题例外"、L-08）：[ShareStrings.shareFileTitle]
 * 按生效语言取词。语言来源选型：[FileSharer.shareTextFile] 是**同步**接口，没有 suspend
 * 上下文，所以用 [currentDisplayLanguageSnapshot]——`AppGraph.startupLanguage`（bootstrap 起
 * 跟随存储刷新的进程内快照，终审 Ruling 46）加系统语言解析，与 `currentDisplayLanguage()` 同一
 * 解析式、且不阻塞调用线程（分享通常在 UI 线程上触发）。快照尚未播种时退化为 SYSTEM→系统语言，
 * 与 App 首帧同口径；代价与窗口见该函数的 KDoc。
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
            val chooser = Intent.createChooser(
                sendIntent,
                ShareStrings.shareFileTitle(currentDisplayLanguageSnapshot())
            )
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
package com.hstudy.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.hstudy.app.HStudyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 学习数据备份与恢复工具：
 * - 备份：用 SQLite 的 VACUUM INTO 生成数据库一致性快照（安全，不干扰正在使用的数据库）
 * - 恢复：关闭数据库→替换数据库文件→重启 App，确保所有数据完整恢复
 * 备份文件存放在应用专属外部存储（无需额外权限）。
 */
object BackupHelper {

    private const val DB_NAME = "hstudy_db"
    private const val BACKUP_FILE_NAME = "hstudy_backup.db"

    /** 备份文件位置（应用专属外部存储） */
    fun getBackupFile(context: Context): File =
        File(context.getExternalFilesDir(null), BACKUP_FILE_NAME)

    /** 是否存在可用备份 */
    fun hasBackup(context: Context): Boolean = getBackupFile(context).exists()

    /** 备份数据库（VACUUM INTO 安全快照） */
    suspend fun backup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val db = HStudyApp.instance.database
            val backupFile = getBackupFile(context)
            if (backupFile.exists()) backupFile.delete()
            db.openHelper.writableDatabase.execSQL("VACUUM INTO '${backupFile.absolutePath}'")
            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从备份恢复数据库：关闭数据库→删除现有DB及WAL/SHM→复制备份→重启App。
     * 恢复成功后会重启应用以重新初始化所有数据仓库。
     */
    suspend fun restore(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile(context)
            if (!backupFile.exists()) return@withContext Result.failure(Exception("没有找到备份文件"))
            val dbPath = context.getDatabasePath(DB_NAME)
            // 关闭数据库连接
            HStudyApp.instance.database.close()
            // 删除现有数据库及附属文件
            dbPath.delete()
            File(dbPath.absolutePath + "-wal").delete()
            File(dbPath.absolutePath + "-shm").delete()
            // 复制备份到数据库位置
            backupFile.copyTo(dbPath, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 重启应用（恢复数据库后调用，重新初始化所有数据） */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** 获取备份文件的分享Uri（通过FileProvider安全分享） */
    fun getShareUri(context: Context): Uri? {
        val backupFile = getBackupFile(context)
        if (!backupFile.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
    }

    /** 创建分享备份文件的Intent（可发送到任何设备：蓝牙/WiFi直连/邮件/云盘等） */
    fun createShareIntent(context: Context): Intent? {
        val uri = getShareUri(context) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HStudy学习数据备份")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 从外部Uri导入备份文件（用户通过文件选择器选取收到的备份） */
    suspend fun importFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupFile = getBackupFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("无法读取文件"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

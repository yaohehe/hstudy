package com.hstudy.app.data.backup

import com.hstudy.app.AppGraph
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 学习数据备份与恢复工具：
 * - 备份：用 SQLite 的 VACUUM INTO 生成数据库一致性快照（安全，不干扰正在使用的数据库）
 * - 恢复：关闭数据库→替换数据库文件→重启 App，确保所有数据完整恢复
 *
 * KMP 说明：
 * - 原 Android 版把备份放在 getExternalFilesDir 并通过 FileProvider/Intent 分享二进制文件；
 *   跨平台 [com.hstudy.app.platform.FileSharer] 只支持文本分享，因此分享/导入时将二进制
 *   快照包装为 Base64 JSON 信封（type=hstudy_db_backup）。本地备份文件仍是原始 SQLite 快照。
 * - 数据库位置与 DatabaseFactory 一致：<dataDir>/databases/hstudy_db。
 * - [restore] 成功后调用方必须重启应用（Android 可用进程重启；iOS 提示用户重启），
 *   以重新初始化 AppGraph 中所有数据仓库。
 */
object BackupHelper {

    private const val DB_NAME = "hstudy_db"
    private const val BACKUP_FILE_NAME = "hstudy_backup.db"

    private val fs: FileSystem get() = FileSystem.SYSTEM
    private val json = Json { ignoreUnknownKeys = true }

    /** 备份文件位置（应用私有 filesDir，跨平台一致） */
    fun getBackupFilePath(): String =
        (Platform.filesDir.toPath() / BACKUP_FILE_NAME).toString()

    private fun backupPath(): Path = Platform.filesDir.toPath() / BACKUP_FILE_NAME

    private fun dbPath(): Path = Platform.dataDir.toPath() / "databases" / DB_NAME

    /** 是否存在可用备份 */
    fun hasBackup(): Boolean = fs.exists(backupPath())

    /** 备份数据库（VACUUM INTO 安全快照），返回备份文件路径 */
    suspend fun backup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = AppGraph.database
            val backupFile = backupPath()
            if (fs.exists(backupFile)) fs.delete(backupFile)
            db.openHelper.writableDatabase.execSQL("VACUUM INTO '$backupFile'")
            Result.success(backupFile.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从备份恢复数据库：关闭数据库→删除现有DB及WAL/SHM→复制备份。
     * 恢复成功后调用方必须重启应用以重新初始化所有数据仓库。
     */
    suspend fun restore(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupFile = backupPath()
            if (!fs.exists(backupFile)) return@withContext Result.failure(Exception("没有找到备份文件"))
            val dbFile = dbPath()
            // 关闭数据库连接
            AppGraph.database.close()
            // 删除现有数据库及附属文件
            if (fs.exists(dbFile)) fs.delete(dbFile)
            val wal = "$dbFile-wal".toPath()
            val shm = "$dbFile-shm".toPath()
            if (fs.exists(wal)) fs.delete(wal)
            if (fs.exists(shm)) fs.delete(shm)
            // 复制备份到数据库位置
            fs.copy(backupFile, dbFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 分享备份到另一台设备：读取本地 SQLite 快照 → Base64 JSON 信封 → 系统文本分享面板。
     * 无备份时静默跳过。
     */
    fun shareBackup() {
        try {
            val backupFile = backupPath()
            if (!fs.exists(backupFile)) return
            val bytes = fs.read(backupFile) { readByteArray() }
            val envelope = DbBackupEnvelope(data = bytes.toByteString().base64())
            Platform.fileSharer.shareTextFile(
                fileName = "hstudy_backup.json",
                content = json.encodeToString(envelope),
                mimeType = "application/json"
            )
        } catch (_: Exception) {
            // 分享失败不影响主流程
        }
    }

    /**
     * 从文件选择器导入备份（用户选取收到的 Base64 JSON 备份文件）。
     * 导入成功后仍需调用 [restore] + 重启应用。取消选择时返回失败。
     */
    fun importBackup(onResult: (Result<Unit>) -> Unit) {
        Platform.fileSharer.pickTextFile { text ->
            if (text == null) {
                onResult(Result.failure(Exception("未选择文件")))
                return@pickTextFile
            }
            val result: Result<Unit> = runCatching {
                val envelope = json.decodeFromString<DbBackupEnvelope>(text)
                if (envelope.type != "hstudy_db_backup") throw Exception("不是有效的备份文件")
                val bytes = envelope.data.decodeBase64() ?: throw Exception("备份内容损坏")
                val backupFile = backupPath()
                fs.write(backupFile) { write(bytes.toByteArray()) }
                Unit
            }
            onResult(result)
        }
    }

    /** Base64 备份信封（仅用于跨设备传输；本地备份文件仍为原始 SQLite 快照） */
    @Serializable
    private data class DbBackupEnvelope(
        val type: String = "hstudy_db_backup",
        val version: Int = 1,
        val data: String = ""
    )
}
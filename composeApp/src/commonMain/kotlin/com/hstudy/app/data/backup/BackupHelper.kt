package com.hstudy.app.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.AppGraph
import com.hstudy.app.data.local.DatabaseRestoreRecovery
import com.hstudy.app.data.local.execSQL
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 学习数据备份与恢复工具。所有文件操作均可注入，生产工厂由 [AppGraph.backupHelper] 提供。
 *
 * - 备份：VACUUM INTO 唯一临时文件，校验成功后原子替换旧备份。
 * - 导入：只校验并原子替换备份，绝不修改实时数据库。
 * - 恢复：先原子写 PREPARED 日志，再把实时 DB/WAL/SHM 改名为 .bak；候选复制、重开和校验全部成功后才标记
 *   COMMITTED 并清理 .bak/日志。进程死亡后 DatabaseFactory 在 Room 构造前按日志恢复 .bak 并删除部分候选；
 *   任一步失败都会先关闭已尝试重开的候选，再删除候选并回滚 .bak；候选关闭失败时保留日志与恢复文件。
 *
 * KMP 说明：跨设备传输使用 Base64 JSON 信封（type=hstudy_db_backup），本地文件仍是 SQLite 快照。
 * [restore] 成功或回滚后都会通过生产回调重建 Room，并重新绑定仓库到新 DAO。
 */
class BackupHelper(
    private val fs: FileSystem,
    private val liveDbPath: Path,
    private val backupPath: Path,
    private val validator: SqliteBackupValidator,
    private val closeDatabase: () -> Unit,
    private val reopenDatabase: suspend () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 是否存在可用备份。 */
    fun hasBackup(): Boolean = fs.exists(backupPath)

    /** 备份数据库（VACUUM INTO 安全快照），返回备份文件路径。 */
    suspend fun backup(): Result<String> = withContext(ioDispatcher) {
        try {
            if (!fs.exists(liveDbPath)) {
                return@withContext Result.failure(Exception("数据库文件尚未创建，暂时无法备份"))
            }
            val staged = uniqueSibling(".tmp")
            try {
                BundledSQLiteDriver().open(liveDbPath.toString()).use { connection ->
                    connection.execSQL("VACUUM INTO '${staged.toString().sqlLiteral()}'")
                }
                if (!validator.validate(staged)) {
                    throw IOException("备份快照校验失败")
                }
                fs.atomicMove(staged, backupPath)
                Result.success(backupPath.toString())
            } catch (error: Exception) {
                deleteQuietly(staged)?.let(error::addSuppressed)
                Result.failure(error)
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** 关闭数据库，安装已验证候选；任何失败都恢复原 live/WAL/SHM。 */
    suspend fun restore(): Result<Unit> = withContext(ioDispatcher) {
        if (!validator.validate(backupPath)) {
            return@withContext Result.failure(Exception("备份文件无效或已损坏"))
        }
        val originals = listOf(
            liveDbPath,
            "$liveDbPath-wal".toPath(),
            "$liveDbPath-shm".toPath(),
        )
        val moved = mutableListOf<Pair<Path, Path>>()
        val journal = DatabaseRestoreRecovery.journalPath(liveDbPath)
        var journalCreated = false
        var closeCompleted = false
        var copyAttempted = false
        var reopenAttempted = false
        try {
            if (fs.exists(journal)) {
                throw IOException("存在未处理的恢复日志：$journal")
            }
            originals.forEach { original ->
                val recovery = "$original.bak".toPath()
                if (fs.exists(recovery)) {
                    throw IOException("存在未处理的恢复备份：$recovery")
                }
            }
            closeDatabase()
            closeCompleted = true
            // Durable intent must exist before the first live file is moved.
            DatabaseRestoreRecovery.writeJournal(fs, liveDbPath, DatabaseRestoreRecovery.PREPARED)
            journalCreated = true
            originals.forEach { original ->
                if (fs.exists(original)) {
                    val recovery = "$original.bak".toPath()
                    fs.atomicMove(original, recovery)
                    moved += original to recovery
                }
            }
            copyAttempted = true
            fs.copy(backupPath, liveDbPath)
            reopenAttempted = true
            reopenDatabase()
            if (!validator.validate(liveDbPath)) {
                throw IOException("恢复后的数据库校验失败")
            }

            // The candidate is validated. From here on, startup cleanup must preserve it even if
            // deleting a .bak file fails; COMMITTED distinguishes that state from a partial copy.
            DatabaseRestoreRecovery.writeJournal(fs, liveDbPath, DatabaseRestoreRecovery.COMMITTED)
            moved.forEach { (_, recovery) -> deleteQuietly(recovery) }
            if (moved.none { (_, recovery) -> fs.exists(recovery) }) {
                deleteQuietly(journal)
            }
            Result.success(Unit)
        } catch (error: Exception) {
            if (reopenAttempted) {
                // Reopen may have constructed and opened the candidate before returning or throwing.
                // Never delete/restore files underneath that live Room handle. If it cannot be closed,
                // leave both the candidate and every .bak file intact for manual recovery.
                try {
                    closeDatabase()
                } catch (closeError: Exception) {
                    error.addSuppressed(closeError)
                    return@withContext Result.failure(error)
                }
            }
            val rollbackError = rollback(moved, removeCandidate = copyAttempted)
            rollbackError?.let(error::addSuppressed)
            if (closeCompleted && rollbackError == null) {
                if (journalCreated) {
                    runCatching { DatabaseRestoreRecovery.clearJournal(fs, liveDbPath) }
                        .exceptionOrNull()?.let(error::addSuppressed)
                }
                // The old files are back in place; leave the app usable after a failed restore.
                runCatching { reopenDatabase() }.exceptionOrNull()?.let(error::addSuppressed)
            }
            Result.failure(error)
        }
    }

    /**
     * 分享备份到另一台设备：读取本地 SQLite 快照 → Base64 JSON 信封 → 系统文本分享面板。
     * 无备份时静默跳过。
     */
    fun shareBackup() {
        try {
            if (!fs.exists(backupPath)) return
            val bytes = fs.read(backupPath) { readByteArray() }
            val envelope = DbBackupEnvelope(
                type = "hstudy_db_backup",
                version = 1,
                data = bytes.toByteString().base64(),
            )
            Platform.fileSharer.shareTextFile(
                fileName = "hstudy_backup.json",
                content = json.encodeToString(envelope),
                mimeType = "application/json"
            )
        } catch (_: Exception) {
            // 分享失败不影响主流程
        }
    }

    /** 从文件选择器导入备份；成功后调用 [restore] 安装，Room 与仓库会在恢复流程内重建。 */
    fun importBackup(onResult: (Result<Unit>) -> Unit) {
        Platform.fileSharer.pickTextFile { text ->
            onResult(if (text == null) Result.failure(Exception("未选择文件")) else importBackup(text))
        }
    }

    /** 纯文本入口，便于在真实文件/SQLite 测试中绕过系统文件选择器。 */
    internal fun importBackup(text: String): Result<Unit> = runCatching {
        val envelope = json.decodeFromString<DbBackupEnvelope>(text)
        if (envelope.type != "hstudy_db_backup" || envelope.version != 1) {
            throw IOException("不是有效的备份信封")
        }
        val bytes = envelope.data?.decodeBase64() ?: throw IOException("备份内容损坏")
        val staged = uniqueSibling(".import.tmp")
        try {
            fs.write(staged) { write(bytes.toByteArray()) }
            if (!validator.validate(staged)) {
                throw IOException("备份内容不是有效的 SQLite 数据库")
            }
            fs.atomicMove(staged, backupPath)
        } catch (error: Exception) {
            deleteQuietly(staged)?.let(error::addSuppressed)
            throw error
        }
        Unit
    }

    private fun rollback(
        moved: List<Pair<Path, Path>>,
        removeCandidate: Boolean,
    ): Throwable? {
        var firstError: Throwable? = null
        fun capture(error: Throwable) {
            if (firstError == null) firstError = error else firstError?.addSuppressed(error)
        }
        if (removeCandidate) {
            listOf(liveDbPath, "$liveDbPath-wal".toPath(), "$liveDbPath-shm".toPath()).forEach { path ->
                runCatching {
                    if (fs.exists(path)) fs.delete(path)
                }.exceptionOrNull()?.let(::capture)
            }
        }
        moved.asReversed().forEach { (original, recovery) ->
            try {
                fs.atomicMove(recovery, original)
            } catch (error: Exception) {
                capture(error)
            }
        }
        return firstError
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun uniqueSibling(suffix: String): Path {
        repeat(10) {
            val candidate = backupPath.parent!! / "${backupPath.name}.${Uuid.random()}$suffix"
            if (!fs.exists(candidate)) return candidate
        }
        throw IOException("无法创建唯一的备份临时文件")
    }

    private fun deleteQuietly(path: Path): Throwable? = runCatching {
        if (fs.exists(path)) fs.delete(path)
    }.exceptionOrNull()

    private fun String.sqlLiteral(): String = replace("'", "''")

    /** Base64 备份信封（仅用于跨设备传输；本地备份文件仍为原始 SQLite 快照）。 */
    @Serializable
    private data class DbBackupEnvelope(
        val type: String? = null,
        val version: Int? = null,
        val data: String? = null,
    )

    companion object {
        private const val DB_NAME = "hstudy_db"
        private const val BACKUP_FILE_NAME = "hstudy_backup.db"

        /** 生产构造入口：路径来自 Platform，关闭/重开使用 AppGraph 中的 Room 数据库。 */
        operator fun invoke(): BackupHelper = AppGraph.backupHelper

        /** 备份文件位置（应用私有 filesDir，跨平台一致）。 */
        fun getBackupFilePath(): String = productionBackupPath().toString()

        fun hasBackup(): Boolean = invoke().hasBackup()

        suspend fun backup(): Result<String> = invoke().backup()

        suspend fun restore(): Result<Unit> = invoke().restore()

        fun shareBackup() = invoke().shareBackup()

        fun importBackup(onResult: (Result<Unit>) -> Unit) = invoke().importBackup(onResult)

        internal fun productionLiveDbPath(): Path =
            Platform.dataDir.toPath() / "databases" / DB_NAME

        internal fun productionBackupPath(): Path =
            Platform.filesDir.toPath() / BACKUP_FILE_NAME
    }
}

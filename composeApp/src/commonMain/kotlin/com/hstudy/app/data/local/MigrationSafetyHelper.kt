package com.hstudy.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hstudy.app.data.repository.backupStamp
import com.hstudy.app.platform.Platform
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * 数据库迁移安全网：迁移前自动备份、迁移后做完整性校验。
 *
 * 用法：在 Room.databaseBuilder 上调用
 *   .addCallback(MigrationSafetyHelper.callback())
 *   并在 App 启动早期 IO 协程里调用一次 [backupIfExists]。
 *
 * 设计原则：
 * 1. 备份：在迁移发生前把 SQLite 文件复制到应用私有目录
 *    <filesDir>/db_backups/hstudy_db-backup-<timestamp>.db。
 *    即使迁移本身出错或被手动 destructiveMigration 回退，备份仍然存在。
 * 2. 校验：onOpen 回调中检查关键表行数与 v12 新增列是否存在，确保未发生整表丢失。
 *    校验日志以 "HStudy/Migration" 前缀输出，便于排查。
 * 3. 回退：用户如需回退到旧版本，可用备份文件手动替换数据库文件后安装旧版。
 *
 * KMP 说明：原 Android 实现使用 Context/java.io.File，这里改为 okio.FileSystem +
 * [Platform.dataDir]。数据库文件位置与 DatabaseFactory 保持一致（<dataDir>/databases/hstudy_db），
 * 与旧 Android 版升级安装时旧库原位兼容。
 */
object MigrationSafetyHelper {

    private const val TAG = "HStudy/Migration"
    private const val BACKUP_DIR = "db_backups"
    private const val DB_NAME = "hstudy_db"

    private val fs: FileSystem get() = FileSystem.SYSTEM

    private fun logInfo(msg: String) = println("[$TAG] I: $msg")
    private fun logWarn(msg: String) = println("[$TAG] W: $msg")
    private fun logError(msg: String, t: Throwable? = null) =
        println("[$TAG] E: $msg${t?.let { " (${it.message})" } ?: ""}")

    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
                verifySchema(db)
            } catch (t: Throwable) {
                logError("post-migration verification failed", t)
            }
        }
    }

    /**
     * 在迁移发生前由调用方主动调用一次（推荐在启动早期 IO 协程里执行），
     * 把当前的 hstudy_db 复制到备份目录。Room 自身的 destructiveMigration 是
     * 不可逆的；本函数无副作用，失败也不会影响 App 启动。
     */
    fun backupIfExists() {
        try {
            // 正式位置（DatabaseFactory）：<dataDir>/databases/hstudy_db；兼容早期 KMP 快照位置 filesDir/hstudy_db
            val dbDirPath = Platform.dataDir.toPath() / "databases" / DB_NAME
            val filesPath = Platform.filesDir.toPath()
            val legacyPath = runCatching { filesPath.parent?.let { it / "databases" / DB_NAME } }.getOrNull()
            val candidates = listOfNotNull(dbDirPath, filesPath / DB_NAME, legacyPath)
            val dbFile = candidates.firstOrNull { fs.exists(it) } ?: return
            val backupDir = Platform.filesDir.toPath() / BACKUP_DIR
            fs.createDirectories(backupDir)
            val stamp = backupStamp()
            val target = backupDir / "$DB_NAME-backup-$stamp.db"
            if (!fs.exists(target)) {
                fs.copy(dbFile, target)
            }
            val size = fs.metadataOrNull(target)?.size ?: -1
            logInfo("pre-migration backup written: $target (size=$size bytes)")

            // 只保留最近 5 份备份，防止磁盘膨胀
            fs.list(backupDir)
                .sortedByDescending { fs.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
                .drop(5)
                .forEach { runCatching { fs.delete(it) } }
        } catch (t: Throwable) {
            logWarn("pre-migration backup skipped: ${t.message}")
        }
    }

    private fun verifySchema(db: SupportSQLiteDatabase) {
        val tables = listOf(
            "tasks", "point_records", "task_completions", "rewards", "reward_exchanges",
            "mistakes", "mistake_reviews", "focus_sessions", "daily_plans",
            "completion_proofs", "habit_challenges", "achievements",
            "daily_assignments", "wish_goals", "daily_schedules", "point_approvals"
        )
        val counts = mutableMapOf<String, Int>()
        for (t in tables) {
            try {
                db.query("SELECT COUNT(*) FROM `$t`").use { c ->
                    if (c.moveToNext()) counts[t] = c.getInt(0)
                }
            } catch (t2: Throwable) {
                logWarn("verify: missing or unreadable table $t (${t2.message})")
            }
        }
        val dbVersion = runCatching {
            db.query("PRAGMA user_version").use { c -> if (c.moveToNext()) c.getInt(0) else -1 }
        }.getOrDefault(-1)
        logInfo("post-migration verify: dbVersion=$dbVersion, rowCounts=$counts")

        // v12 专属校验：point_approvals 必须含新增列 taskId/bizKey/awardedRecordId/date
        val expectedNewCols = listOf("taskId", "bizKey", "awardedRecordId", "date")
        val missing = mutableListOf<String>()
        for (col in expectedNewCols) {
            val ok = runCatching {
                db.query("SELECT `$col` FROM `point_approvals` LIMIT 1").use { true }
            }.getOrDefault(false)
            if (!ok) missing += col
        }
        if (missing.isNotEmpty()) {
            logError("post-migration verify: point_approvals missing columns $missing")
        } else {
            logInfo("post-migration verify: point_approvals schema OK (v12 columns present)")
        }
    }
}
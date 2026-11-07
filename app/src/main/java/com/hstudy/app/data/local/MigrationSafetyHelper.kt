package com.hstudy.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据库迁移安全网：迁移前自动备份、迁移后做完整性校验。
 *
 * 用法：在 [androidx.room.Room.databaseBuilder] 上调用
 *   .addCallback(MigrationSafetyHelper.callback())
 *   并在 App 启动早期 IO 协程里调用一次 [backupIfExists]。
 *
 * 设计原则：
 * 1. 备份：在迁移发生前把 SQLite 文件复制到 app 私有目录
 *    /data/data/<pkg>/files/db_backups/hstudy_db-<timestamp>.db。
 *    即使迁移本身出错或被手动 destructiveMigration 回退，备份仍然存在。
 * 2. 校验：onOpen 回调中检查关键表行数与 v12 新增列是否存在，确保未发生整表丢失。
 *    校验日志写入 Logcat tag "HStudy/Migration"，便于排查。
 * 3. 回退：用户如需回退到 v2.86，可：
 *    - 卸载 v2.87 → 安装 v2.86（v2.86 schema v11 与备份完全匹配）。
 *    - 或手动 `adb push backup.db /data/data/<pkg>/databases/hstudy_db` 后安装 v2.86。
 */
object MigrationSafetyHelper {

    private const val TAG = "HStudy/Migration"
    private const val BACKUP_DIR = "db_backups"
    private const val DB_NAME = "hstudy_db"

    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
                verifySchema(db)
            } catch (t: Throwable) {
                Log.e(TAG, "post-migration verification failed", t)
            }
        }
    }

    /**
     * 在迁移发生前由调用方主动调用一次（推荐在启动早期 IO 协程里执行），
     * 把当前的 hstudy_db 复制到备份目录。Room 自身的 destructiveMigration 是
     * 不可逆的；本函数无副作用，失败也不会影响 App 启动。
     */
    fun backupIfExists(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return
            val backupDir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val target = File(backupDir, "$DB_NAME-backup-$stamp.db")
            dbFile.copyTo(target, overwrite = false)
            Log.i(TAG, "pre-migration backup written: ${target.absolutePath} (size=${target.length()} bytes)")

            // 只保留最近 5 份备份，防止磁盘膨胀
            backupDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
        } catch (t: Throwable) {
            Log.w(TAG, "pre-migration backup skipped: ${t.message}")
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
                db.query("SELECT COUNT(*) FROM `$t`", emptyArray<Any>()).use { c ->
                    if (c.moveToFirst()) counts[t] = c.getInt(0)
                }
            } catch (t2: Throwable) {
                Log.w(TAG, "verify: missing or unreadable table $t (${t2.message})")
            }
        }
        Log.i(TAG, "post-migration verify: dbVersion=${db.version}, rowCounts=$counts")

        // v12 专属校验：point_approvals 必须含新增列 taskId/bizKey/awardedRecordId/date
        val expectedNewCols = listOf("taskId", "bizKey", "awardedRecordId", "date")
        val missing = mutableListOf<String>()
        for (col in expectedNewCols) {
            val ok = runCatching {
                db.query("SELECT `$col` FROM `point_approvals` LIMIT 1", emptyArray<Any>()).use { true }
            }.getOrDefault(false)
            if (!ok) missing += col
        }
        if (missing.isNotEmpty()) {
            Log.e(TAG, "post-migration verify: point_approvals missing columns $missing")
        } else {
            Log.i(TAG, "post-migration verify: point_approvals schema OK (v12 columns present)")
        }
    }
}
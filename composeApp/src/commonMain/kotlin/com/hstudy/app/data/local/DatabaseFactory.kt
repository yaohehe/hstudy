package com.hstudy.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.platform.Platform
import com.hstudy.app.platform.ioDispatcher
import com.hstudy.app.platform.systemFileSystem

private var instance: AppDatabase? = null

fun getAppDatabase(): AppDatabase = instance ?: buildAppDatabase().also { instance = it }

/** 数据库文件名（历史约定，v2.88 起同名） */
private const val DB_NAME = "hstudy_db"

fun buildAppDatabase(): AppDatabase {
    // Android 老用户兼容：原 Room 数据库位于 <dataDir>/databases/hstudy_db
    val dbFile = "${Platform.dataDir}/databases/$DB_NAME"
    ensureParentDir(dbFile)
    // 与原 Android 版 HStudyApp 行为一致：打开/迁移数据库前先把现有 hstudy_db 复制到
    // db_backups（无现有库时静默跳过）；Room 首次访问才会真正打开数据库，因此此处早于迁移。
    MigrationSafetyHelper.backupIfExists()
    return appDatabaseBuilder(dbFile)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher)
        .addMigrations(*Migrations.ALL)
        .addCallback(MigrationSafetyHelper.callback())
        .build()
}

/**
 * 补建数据库父目录：Android 上 `<dataDir>/databases` 由系统创建（此处幂等），
 * iOS 上 `<Documents>/databases` 不存在时 sqlite 打不开文件，故统一先建目录。
 * 只建目录、不改路径，用户数据位置与 v2.88 完全一致。
 */
private fun ensureParentDir(dbFilePath: String) {
    val parent = dbFilePath.substringBeforeLast('/')
    if (parent.isBlank()) return
    runCatching {
        with(okio.Path.Companion) {
            val dir = parent.toPath()
            if (!systemFileSystem.exists(dir)) systemFileSystem.createDirectories(dir)
        }
    }
}

/**
 * 平台侧 Room builder：
 * - Android 的 [Room.databaseBuilder] 只接受"数据库名"（内部用 Context.getDatabasePath 解析成
 *   <dataDir>/databases/<name>），传入绝对路径会被解析成错误路径甚至抛 IllegalArgumentException，
 *   因此 androidMain 的 actual 会取 [dbFilePath] 的文件名，结果路径与传入的绝对路径一致；
 * - iOS/native 的 Room KMP builder 直接把名字当路径处理，actual 直接透传绝对路径。
 */
expect fun appDatabaseBuilder(dbFilePath: String): RoomDatabase.Builder<AppDatabase>

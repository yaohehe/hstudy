package com.hstudy.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.Dispatchers

private var instance: AppDatabase? = null

fun getAppDatabase(): AppDatabase = instance ?: buildAppDatabase().also { instance = it }

fun buildAppDatabase(): AppDatabase {
    // Android 老用户兼容：原 Room 数据库位于 <dataDir>/databases/hstudy_db
    val dbFile = Platform.dataDir + "/databases/hstudy_db"
    // 与原 Android 版 HStudyApp 行为一致：打开/迁移数据库前先把现有 hstudy_db 复制到
    // db_backups（无现有库时静默跳过）；Room 首次访问才会真正打开数据库，因此此处早于迁移。
    MigrationSafetyHelper.backupIfExists()
    return Room.databaseBuilder<AppDatabase>(name = dbFile)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*Migrations.ALL)
        .addCallback(MigrationSafetyHelper.callback())
        .build()
}
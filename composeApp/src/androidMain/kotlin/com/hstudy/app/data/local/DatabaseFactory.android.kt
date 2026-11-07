package com.hstudy.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hstudy.app.HStudyApplication

/**
 * Android 侧 Room builder。
 *
 * Room 在 Android 上只接受"数据库名"，最终路径由 [Context.getDatabasePath] 解析为
 * `<dataDir>/databases/<name>`，与 v2.88 单模块版 `Room.databaseBuilder(context, AppDatabase::class.java, "hstudy_db")`
 * 完全相同，因此老用户升级后数据库原位复用（数据连续性要求）。
 */
actual fun appDatabaseBuilder(dbFilePath: String): RoomDatabase.Builder<AppDatabase> {
    val context = HStudyApplication.instance
    val dbName = dbFilePath.substringAfterLast('/')
    return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
}

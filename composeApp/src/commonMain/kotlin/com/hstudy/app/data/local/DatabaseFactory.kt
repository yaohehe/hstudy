package com.hstudy.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.Dispatchers

private var instance: AppDatabase? = null

fun getAppDatabase(): AppDatabase = instance ?: buildAppDatabase().also { instance = it }

fun buildAppDatabase(): AppDatabase {
    val dbFile = Platform.filesDir + "/hstudy_db"
    return Room.databaseBuilder<AppDatabase>(name = dbFile)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*Migrations.ALL)
        .build()
}
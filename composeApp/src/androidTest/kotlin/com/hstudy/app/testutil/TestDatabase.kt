package com.hstudy.app.testutil

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hstudy.app.data.local.AppDatabase
import com.hstudy.app.data.local.Migrations
import kotlinx.coroutines.Dispatchers

fun testDatabase(context: Context, name: String? = null): AppDatabase {
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
    } else {
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
    }
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

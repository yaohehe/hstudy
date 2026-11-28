package com.hstudy.app.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room KMP 的 Android 数据库由 BundledSQLiteDriver 打开，没有 SupportSQLiteOpenHelper.Factory；
 * 因此必须使用 common Room API 的写连接事务，不能调用 Android-only 的 withTransaction。
 */
private class AndroidRoomTransactionRunner(private val database: AppDatabase) : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.useWriterConnection { connection ->
            connection.immediateTransaction { block() }
        }
}

actual fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner =
    AndroidRoomTransactionRunner(database)

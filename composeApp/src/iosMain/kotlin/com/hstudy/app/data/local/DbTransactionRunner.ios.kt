package com.hstudy.app.data.local

import androidx.room.useWriterConnection

private class IosRoomTransactionRunner(private val database: AppDatabase) : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.useWriterConnection { connection ->
            connection.immediateTransaction { block() }
        }
}

actual fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner =
    IosRoomTransactionRunner(database)

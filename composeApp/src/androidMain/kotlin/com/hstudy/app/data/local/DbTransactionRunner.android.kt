package com.hstudy.app.data.local

import androidx.room.withTransaction

/**
 * Android actual：委托 Room 2.7 的 `RoomDatabase.withTransaction` 真事务
 * （实现自 commonMain 原 `RoomTransactionRunner` 逐字搬入，CI fix round）。
 * 事务承载在数据库自身的连接池上（BundledSQLiteDriver），跨 DAO（pointDao + pointApprovalDao）
 * 的多表写原子性由 SQLite 事务保证；嵌套调用（如 expireStalePendingApprovals → revokeApproval）
 * 由 Room 的重入机制合并到最外层事务。
 *
 * 红线：Android 行为与 Task C（987752e）生产路径零变化——三入口仍走真 withTransaction。
 */
private class AndroidRoomTransactionRunner(private val database: AppDatabase) : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)
}

actual fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner =
    AndroidRoomTransactionRunner(database)

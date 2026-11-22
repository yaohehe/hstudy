package com.hstudy.app.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * v3.3.2 Task C（P2-2 / v3.3.1 终审残留窗口）—— 仓库层事务执行器抽象。
 *
 * 为什么是接口而不是直接把 [RoomDatabase] 注进 PointRepository：
 * - 本仓 commonTest 按 RA9 约定**不引入 JVM sqlite**，仓库测试全部走手写 Fake DAO
 *   （见 PointRepositoryInvariantTest 的 FakePointDao / FakePointApprovalDao）；
 *   Fake 无法伪造真 RoomDatabase 的事务语义，注入抽象后测试可以插一个
 *   "快照-回滚" journal 假事务器，把"中途异常整体回滚"钉在纯逻辑层。
 * - 生产装配（AppGraph）注入 [RoomTransactionRunner]，即 Room 2.7 KMP 的
 *   `RoomDatabase.withTransaction`（DatabaseFactory 已配 setQueryCoroutineContext，
 *   满足其挂起协程上下文要求；嵌套调用与 Room 一致=同一事务内联执行）。
 */
interface DbTransactionRunner {
    /**
     * 把 [block] 包进一个事务：正常返回即提交，block 抛出任何异常则**整体回滚**后异常继续上抛。
     * 可重入：同一调用链内嵌套 [runInTransaction] 加入最外层事务，不另起事务、不提前提交。
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

/**
 * 直通实现（**不做事务**，原样执行 block）：
 * - 单元测试的默认值——保持既有两参构造 `PointRepository(pointDao, approvalDao)` 的全部
 *   317 条基线用例字节行为不变（红线：幂等判定逻辑零变化，事务只做原子化）；
 * - 也用作事务器不可用场景的兜底形状（行为退化为 v3.3.1 现状）。
 */
object DirectTransactionRunner : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
}

/**
 * Room 实现：委托 `androidx.room.withTransaction`（Room 2.7.0 KMP，全平台可用）。
 * 事务承载在数据库自身的连接池上（BundledSQLiteDriver），跨 DAO（pointDao + pointApprovalDao）
 * 的多表写原子性由 SQLite 事务保证；嵌套调用（如 expireStalePendingApprovals → revokeApproval）
 * 由 Room 的重入机制合并到最外层事务。
 */
class RoomTransactionRunner(private val database: RoomDatabase) : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)
}

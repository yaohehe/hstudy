package com.hstudy.app.data.local

/**
 * v3.3.2 Task C（P2-2 / v3.3.1 终审残留窗口）—— 仓库层事务执行器抽象。
 *
 * 为什么是接口而不是直接把 [androidx.room.RoomDatabase] 注进 PointRepository：
 * - 本仓 commonTest 按 RA9 约定**不引入 JVM sqlite**，仓库测试全部走手写 Fake DAO
 *   （见 PointRepositoryInvariantTest 的 FakePointDao / FakePointApprovalDao）；
 *   Fake 无法伪造真 RoomDatabase 的事务语义，注入抽象后测试可以插一个
 *   "快照-回滚" journal 假事务器，把"中途异常整体回滚"钉在纯逻辑层。
 * - 生产装配（AppGraph）注入 [roomTransactionRunner]（expect 工厂）：
 *   Android 与 iOS actual 均使用 Room KMP 的 `useWriterConnection` / `immediateTransaction`
 *   真事务，以匹配 `BundledSQLiteDriver` 的连接模型。
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
 * - 生产平台工厂不得返回该实现；Android 与 iOS 均使用真实 Room 事务。
 */
object DirectTransactionRunner : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
}

/**
 * 平台事务工厂（CI iosSimulatorArm64 fix round）：
 *
 * **为什么按平台提供 actual**：保留平台装配边界，两个 actual 均使用 Room KMP 的
 * `useWriterConnection` 获取写连接，再以 `immediateTransaction` 执行真事务。
 * Android 数据库使用 `BundledSQLiteDriver`，不能调用依赖 SupportSQLiteOpenHelper 的
 * Android-only `withTransaction`。
 */
expect fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner

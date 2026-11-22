package com.hstudy.app.data.local

/**
 * v3.3.2 Task C（P2-2 / v3.3.1 终审残留窗口）—— 仓库层事务执行器抽象。
 *
 * 为什么是接口而不是直接把 [androidx.room.RoomDatabase] 注进 PointRepository：
 * - 本仓 commonTest 按 RA9 约定**不引入 JVM sqlite**，仓库测试全部走手写 Fake DAO
 *   （见 PointRepositoryInvariantTest 的 FakePointDao / FakePointApprovalDao）；
 *   Fake 无法伪造真 RoomDatabase 的事务语义，注入抽象后测试可以插一个
 *   "快照-回滚" journal 假事务器，把"中途异常整体回滚"钉在纯逻辑层。
 * - 生产装配（AppGraph）注入 [roomTransactionRunner]（expect 工厂，CI fix round）：
 *   Android actual 委托 Room 的 `RoomDatabase.withTransaction` 真事务（DatabaseFactory
 *   已配 setQueryCoroutineContext，满足其挂起协程上下文要求；嵌套调用与 Room 一致=同一
 *   事务内联执行）；iOS actual 返回 [DirectTransactionRunner] 直通——原因见 [roomTransactionRunner] KDoc。
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
 * - iOS 生产装配的实际形态（Room KMP 原生事务 API 缺位，见 [roomTransactionRunner] 的
 *   iosMain actual KDoc 披露）；
 * - 也用作事务器不可用场景的兜底形状（行为退化为 v3.3.1 现状）。
 */
object DirectTransactionRunner : DbTransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
}

/**
 * 平台事务工厂（CI iosSimulatorArm64 fix round）：
 *
 * **为什么不是 commonMain 直接实现**：Room 2.7 的 `androidx.room.withTransaction` 扩展只发布在
 * JVM/Android klib，common klib 没有对应的挂起事务 API，commonMain 引用会让 iOS 目标编译失败
 * （CI run 45045438877：`Unresolved reference 'withTransaction'` → `:composeApp:compileKotlinIosSimulatorArm64 FAILED`）。
 * 原 Task C 把它写在 commonMain 的 `RoomTransactionRunner` 是平台 API 可用性误判，真实现现移入 androidMain。
 *
 * - Android actual（`DbTransactionRunner.android.kt`）：真 Room `withTransaction`，
 *   跨 DAO 多表写原子性与嵌套重入语义与 Task C 生产路径零变化（红线）；
 * - iOS actual（`DbTransactionRunner.ios.kt`）：返回 [DirectTransactionRunner] 直通，
 *   **批量原子降级为直通**，待 Room 在 common klib 提供原生事务 API 后回切实实现
 *   （验收文档 F-7 关联披露）。
 */
expect fun roomTransactionRunner(database: AppDatabase): DbTransactionRunner

package com.hstudy.app.data.repository

import com.hstudy.app.data.local.DbTransactionRunner
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * v3.3.2 Task C（P2-2 + v3.3.1 终审残留窗口）—— 三入口事务原子化回归套件。
 *
 * 组装方式（RA9 延续，不引入 JVM sqlite）：复用同包手写 [FakePointDao] / [FakePointApprovalDao]，
 * 事务行为由 [JournalTxRunner]（快照-回滚假事务器）在纯逻辑层钉：
 * - 语义与 Room 2.7 KMP `RoomDatabase.withTransaction` 对齐：最外层开事务快照、block 抛任何
 *   异常整体回滚后继续上抛、同链嵌套（expireStalePendingApprovals → revokeApproval）合并进
 *   最外层事务不另起；
 * - 抛点注入用接口委托（`PointDao by base` / `PointApprovalDao by base`）在任意第 N 次写操作
 *   模拟"批量中途 DAO 失败"，Fake 层可控复现。
 *
 * **披露**：本套件钉的是"三入口确实经 transactionRunner 包裹 + 包裹层回滚语义正确"；
 * **真 SQLite 事务行为（隔离级别、崩溃恢复）待真机/CI 验证**（仓库无 JVM Room 测试基建，
 * AppGraph 装配行由编译钉住）。
 *
 * 红线复查：单条幂等判定（不变量 3）、undoCheckIn/REVERSAL 路径（不变量 4）、
 * 余额=SUM（不变量 5）在本套件里既经直通旧构造（其余 317 条基线）也经事务包装（本套件）
 * 各钉一遍——事务只原子化、不改判定。
 */
class PointRepositoryTransactionAtomicityTest {

    private val day = 24L * 60 * 60 * 1000

    /** 模拟"DAO 中途写失败"（真机上对应 sqlite 报错/进程被杀前的异常） */
    private class SimulatedDbFailure(message: String) : RuntimeException(message)

    // ==================================================================
    // 快照-回滚假事务器（对齐 Room withTransaction 的重入/回滚语义）
    // ==================================================================

    private class JournalTxRunner(
        private val pointDao: FakePointDao,
        private val approvalDao: FakePointApprovalDao,
    ) : DbTransactionRunner {
        var enters = 0
            private set
        /** 真正开启的最外层事务次数（嵌套调用不计数）——批量整批只应 1 次 */
        var outerRuns = 0
            private set
        private var depth = 0
        private var recordSnap: List<PointRecordEntity>? = null
        private var completionSnap: List<TaskCompletionEntity>? = null
        private var approvalSnap: List<PointApprovalEntity>? = null

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            enters++
            if (depth == 0) {
                outerRuns++
                recordSnap = pointDao.records.toList()
                completionSnap = pointDao.completions.toList()
                approvalSnap = approvalDao.approvals.toList()
            }
            depth++
            try {
                return block()
            } catch (t: Throwable) {
                // 任意深度失败都恢复到**最外层**快照（Room：内层异常连带外层整体回滚）
                recordSnap?.let { (pointDao.records as MutableList).apply { clear(); addAll(it) } }
                completionSnap?.let { (pointDao.completions as MutableList).apply { clear(); addAll(it) } }
                approvalSnap?.let { (approvalDao.approvals as MutableList).apply { clear(); addAll(it) } }
                throw t
            } finally {
                depth--
                if (depth == 0) {
                    recordSnap = null
                    completionSnap = null
                    approvalSnap = null
                }
            }
        }
    }

    /** 第 N 次 insertPointRecord 抛异常的 PointDao 委托（写透到 base，快照/回滚仍由 base 承担） */
    private class ThrowingPointDao(
        private val base: FakePointDao,
        private val throwOnNthInsert: Int,
    ) : PointDao by base {
        private var inserts = 0
        override suspend fun insertPointRecord(record: PointRecordEntity): Long {
            inserts++
            if (inserts == throwOnNthInsert) {
                throw SimulatedDbFailure("模拟第 $inserts 次 insertPointRecord 失败")
            }
            return base.insertPointRecord(record)
        }
    }

    /** 第 N 次 updateReview 抛异常的 PointApprovalDao 委托 */
    private class ThrowingApprovalDao(
        private val base: FakePointApprovalDao,
        private val throwOnNthUpdateReview: Int,
    ) : PointApprovalDao by base {
        private var reviews = 0
        override suspend fun updateReview(id: Long, status: ApprovalStatus, awardedRecordId: Long?, reviewedAt: Long) {
            reviews++
            if (reviews == throwOnNthUpdateReview) {
                throw SimulatedDbFailure("模拟第 $reviews 次 updateReview 失败")
            }
            base.updateReview(id, status, awardedRecordId, reviewedAt)
        }
    }

    private suspend fun insertPending(
        approvalDao: FakePointApprovalDao,
        bizKey: String,
        requestedAt: Long = 100_000L * day,
        points: Int = 4,
        status: ApprovalStatus = ApprovalStatus.PENDING,
    ): Long = approvalDao.insert(
        PointApprovalEntity(
            actionType = "TEST",
            description = "描述 $bizKey",
            requestedPoints = points,
            status = status,
            requestedAt = requestedAt,
            bizKey = bizKey,
            date = "2026-01-01"
        )
    )

    // ==================================================================
    // 入口 1：approveAllAndAward（P2-2）
    // ==================================================================

    @Test
    fun approveAllAndAward_success_wrapsWholeBatchInOneOuterTransaction(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        insertPending(approvalDao, "BIZ:ALL:1", points = 3)
        insertPending(approvalDao, "BIZ:ALL:2", points = 4)
        insertPending(approvalDao, "BIZ:ALL:3", points = 5)
        val journal = JournalTxRunner(pointDao, approvalDao)
        val repo = PointRepository(pointDao, approvalDao, journal)

        val awarded = repo.approveAllAndAward()

        assertEquals(12, awarded, "成功路径总额=三条待审之和（判定逻辑不变）")
        assertEquals(1, journal.outerRuns, "P2-2：整批必须包在**一个**最外层事务里，不逐条各开")
        assertEquals(1, journal.enters)
        assertEquals(3, pointDao.records.size)
        assertEquals(12, repo.getTotalPoints().first(), "不变量 5：余额 = SUM")
        assertTrue(approvalDao.approvals.all { it.status == ApprovalStatus.APPROVED })
    }

    @Test
    fun approveAllAndAward_throwMidBatch_rollsBackEntireBatch(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val id1 = insertPending(approvalDao, "BIZ:RB:1", points = 3)
        val id2 = insertPending(approvalDao, "BIZ:RB:2", points = 4)
        val id3 = insertPending(approvalDao, "BIZ:RB:3", points = 5)
        val journal = JournalTxRunner(pointDao, approvalDao)
        // 第 2 条的积分流水写失败：第 1 条此前已"入账+APPROVED"
        val repo = PointRepository(ThrowingPointDao(pointDao, throwOnNthInsert = 2), approvalDao, journal)

        assertFailsWith<SimulatedDbFailure> { repo.approveAllAndAward() }

        // 整批回滚：不存在"前半批 APPROVED、后半批 PENDING"的半批态
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(id1)?.status, "第一条的 APPROVED 必须回滚")
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(id2)?.status)
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(id3)?.status)
        assertTrue(pointDao.records.isEmpty(), "第一条已写的积分流水必须一并回滚，余额保持 SUM=0")
        assertEquals(0, repo.getTotalPoints().first())
    }

    @Test
    fun approveAllAndAward_secondClickUnderTransaction_stillAwardsOnce(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        insertPending(approvalDao, "BIZ:DC:A", points = 6)
        val journal = JournalTxRunner(pointDao, approvalDao)
        val repo = PointRepository(pointDao, approvalDao, journal)

        val first = repo.approveAllAndAward()
        val second = repo.approveAllAndAward()

        assertEquals(6, first)
        assertEquals(0, second, "既有幂等断言（不变量 3）在事务包装下零回归")
        assertEquals(1, pointDao.records.size)
        assertEquals(6, repo.getTotalPoints().first())
        assertEquals(2, journal.outerRuns, "两次点击=两个独立事务（事务间不共享快照）")
    }

    // ==================================================================
    // 入口 2：revokeApproval（终审 Minor-1 读-判-写窗口）
    // ==================================================================

    @Test
    fun revokeApproval_approved_success_runsInOneTransaction(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        // 直通构造做前置：打卡+审批（与 v3.3.1 行为同源，零变化）
        val setup = PointRepository(pointDao, approvalDao)
        setup.checkIn("BIZ:RV:1", "TEST", "描述", 7, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        setup.approveAndAward(approvalId)

        val journal = JournalTxRunner(pointDao, approvalDao)
        val repo = PointRepository(pointDao, approvalDao, journal)

        val clawedBack = repo.revokeApproval(approvalId)

        assertEquals(7, clawedBack, "扣回口径（不变量 4）在事务包装下零变化")
        assertEquals(1, journal.outerRuns, "复读→判半区→判最新→委托 undoCheckIn 同在一个事务内")
        assertEquals(listOf(7, -7), pointDao.records.map { it.points }, "append-only：原记录不删，只追加 REVERSAL")
        assertEquals(ApprovalStatus.REVOKED, approvalDao.getById(approvalId)?.status)
        assertEquals(0, repo.getTotalPoints().first(), "净额归零，SUM 不变量成立")
    }

    @Test
    fun revokeApproval_throwOnReversalInsert_rollsBackReadJudgeWriteWindow(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val setup = PointRepository(pointDao, approvalDao)
        setup.checkIn("BIZ:RV:2", "TEST", "描述", 7, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        setup.approveAndAward(approvalId) // 账本第 1 条 +7

        val journal = JournalTxRunner(pointDao, approvalDao)
        // 前置 +7 是经 base 写入的（不经包装器计数）：REVERSAL 即包装器上的第 1 次 insert——在此抛=撤回写一半失败
        val repo = PointRepository(ThrowingPointDao(pointDao, throwOnNthInsert = 1), approvalDao, journal)

        assertFailsWith<SimulatedDbFailure> { repo.revokeApproval(approvalId) }

        // 回滚到撤回前：仍 APPROVED、账本仍只有 +7，绝不出现"REVOKED 但没扣回"或反之的半态
        assertEquals(ApprovalStatus.APPROVED, approvalDao.getById(approvalId)?.status)
        assertEquals(listOf(7), pointDao.records.map { it.points })
        assertEquals(7, repo.getTotalPoints().first(), "不变量 5：回滚后余额=SUM 仍成立")
    }

    @Test
    fun revokeApproval_secondClickUnderTransaction_stillZeroWrites(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val setup = PointRepository(pointDao, approvalDao)
        setup.checkIn("BIZ:RV:3", "TEST", "描述", 5, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        setup.approveAndAward(approvalId)
        val journal = JournalTxRunner(pointDao, approvalDao)
        val repo = PointRepository(pointDao, approvalDao, journal)

        val first = repo.revokeApproval(approvalId)
        val ledgerAfterFirst = pointDao.records.toList()
        val second = repo.revokeApproval(approvalId)

        assertEquals(5, first)
        assertEquals(0, second, "终态幂等（D-10）在事务包装下零回归")
        assertEquals(ledgerAfterFirst, pointDao.records.toList(), "二次撤回零写入")
        assertEquals(0, repo.getTotalPoints().first())
    }

    // ==================================================================
    // 入口 3：expireStalePendingApprovals（D-11 复读窗口 + 嵌套重入）
    // ==================================================================

    @Test
    fun expireStalePendingApprovals_success_nestedRevokeSharesOneOuterTransaction(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val now = 100_000L * day
        val s1 = insertPending(approvalDao, "BIZ:EX:1", requestedAt = now - 45 * day, points = 8)
        val s2 = insertPending(approvalDao, "BIZ:EX:2", requestedAt = now - 31 * day, points = 3)
        val legacy = insertPending(approvalDao, "", requestedAt = now - 40 * day, points = 9)
        val fresh = insertPending(approvalDao, "BIZ:EX:FRESH", requestedAt = now - 2 * day, points = 4)
        val journal = JournalTxRunner(pointDao, approvalDao)
        val repo = PointRepository(pointDao, approvalDao, journal)

        val cleaned = repo.expireStalePendingApprovals(now)

        assertEquals(3, cleaned)
        assertEquals(1, journal.outerRuns, "清理整批一个事务；内层 revokeApproval 嵌套重入不另起最外层事务")
        assertEquals(3, journal.enters, "外层 1 次 + 两条 bizKey 行各嵌套 1 次")
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(s1)?.status)
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(s2)?.status)
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(legacy)?.status)
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(fresh)?.status, "边界内新单不动（D-11 判定零变化）")
        assertTrue(pointDao.records.isEmpty(), "PENDING 清理零入账：回滚/提交两态下 SUM 恒 0")
        assertEquals(0, repo.getTotalPoints().first())
    }

    @Test
    fun expireStalePendingApprovals_throwMidBatch_rollsBackAllCancellations(): Unit = runBlocking {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val now = 100_000L * day
        val s1 = insertPending(approvalDao, "BIZ:EXRB:1", requestedAt = now - 45 * day)
        val s2 = insertPending(approvalDao, "BIZ:EXRB:2", requestedAt = now - 33 * day)
        val approvalsBefore = approvalDao.approvals.toList()
        val journal = JournalTxRunner(pointDao, approvalDao)
        // 两条 PENDING 行各走 updateReview(CANCELLED) 一次：第 2 次抛=清到一半失败
        val repo = PointRepository(
            pointDao,
            ThrowingApprovalDao(approvalDao, throwOnNthUpdateReview = 2),
            journal
        )

        assertFailsWith<SimulatedDbFailure> { repo.expireStalePendingApprovals(now) }

        assertEquals(approvalsBefore, approvalDao.approvals.toList(), "第一条的 CANCELLED 必须一并回滚")
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(s1)?.status)
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(s2)?.status)
        assertTrue(pointDao.records.isEmpty())
        assertEquals(2, approvalDao.getPendingCount().first(), "回滚后看板计数与账本一致，不虚报 cleaned")
    }
}

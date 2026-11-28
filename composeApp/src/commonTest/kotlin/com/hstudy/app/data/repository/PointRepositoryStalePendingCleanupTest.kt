package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.3.1 D-11（超期待审一键清理，Antigravity 指令 §5.2）仓库/DAO 层回归套件。
 *
 * 组装方式与 [PointRepositoryParentRevokeTest] 相同：手写 Fake DAO（复用同包
 * [FakePointDao] / [FakePointApprovalDao]，RA9 不引入 JVM sqlite），钉住五条要求：
 * 1. **30 天边界**：判据是 `requestedAt < now - STALE_PENDING_MILLIS` 的**严格早于**——
 *    恰好落在边界值（now-30d 整）的申请不算超期、绝不清理；
 * 2. **清理走 undoCheckIn 对称闭环**：有 bizKey 的行逐条经 [PointRepository.revokeApproval]
 *    入口（复用既有闭环，禁止裸通用状态设置绕开不变量 4）——PENDING 半区置 CANCELLED、
 *    任务类同步清掉当日完成记录，与孩子端撤销未审批打卡同形；
 * 3. **清理后 PENDING 归零**：看板/徽章计数流归 0；
 * 4. **余额零变化**：未审批申请从未计分，清理不写任何积分记录（不变量 5 append-only，余额分毫不动）；
 * 5. **二次清理 0 变动**：幂等——状态、审批列表与账本全量快照不变，返回 0。
 *
 * 另钉空 bizKey 的 v2 遗留行 **cancelPending 窄 CAS 兜底**（报告披露项）：只按 id 精确取消该超期行，
 * 绝不凭 getLatestByBizKey("") 误伤其它空 bizKey 单（含 APPROVED 遗留行）。
 */
class PointRepositoryStalePendingCleanupTest {

    private val day = 24L * 60 * 60 * 1000

    private fun newRepo(): Triple<FakePointDao, FakePointApprovalDao, PointRepository> {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        return Triple(pointDao, approvalDao, PointRepository(pointDao, approvalDao))
    }

    /** 直接插一条显式 requestedAt 的申请（Fake 与实体默认时钟无关，判定只看显式值） */
    private suspend fun insertPending(
        approvalDao: FakePointApprovalDao,
        bizKey: String,
        requestedAt: Long,
        points: Int = 5,
        status: ApprovalStatus = ApprovalStatus.PENDING,
        taskId: Long? = null,
        date: String = "2026-01-01"
    ): Long = approvalDao.insert(
        PointApprovalEntity(
            actionType = "TEST",
            description = "描述 $bizKey",
            requestedPoints = points,
            status = status,
            requestedAt = requestedAt,
            taskId = taskId,
            bizKey = bizKey,
            date = date
        )
    )

    @Test
    fun expireStalePendingApprovals_boundaryValueExactlyThirtyDaysOld_isNotTouched(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val now = 100_000L * day
        val staleId = insertPending(approvalDao, "BIZ:STALE", requestedAt = now - 31 * day)
        // 边界钉：threshold = now-30d，SQL 判据是严格 `<`——requestedAt 恰等于 threshold 不超期
        val edgeId = insertPending(approvalDao, "BIZ:EDGE", requestedAt = now - PointRepository.STALE_PENDING_MILLIS)
        val freshId = insertPending(approvalDao, "BIZ:FRESH", requestedAt = now - 29 * day)
        // 非 PENDING 的陈旧行不在清理口径内（status 谓词与 SQL 一致）
        val approvedOldId = insertPending(approvalDao, "BIZ:OLD-OK", requestedAt = now - 60 * day,
            status = ApprovalStatus.APPROVED)

        val cleaned = repo.expireStalePendingApprovals(now)

        assertEquals(1, cleaned, "只清严格超期的 PENDING：边界值/新单/非 PENDING 均不动")
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(staleId)?.status)
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(edgeId)?.status, "30 天整恰在边界：不算超期")
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(freshId)?.status)
        assertEquals(ApprovalStatus.APPROVED, approvalDao.getById(approvedOldId)?.status)
        assertTrue(pointDao.records.isEmpty(), "清理未审批申请绝不写积分记录")
        assertEquals(0, repo.getTotalPoints().first(), "余额零变化")
    }

    @Test
    fun expireStalePendingApprovals_cancelsAllStaleAndPendingCountDropsToZero(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val now = 100_000L * day
        insertPending(approvalDao, "BIZ:S1", requestedAt = now - 45 * day, points = 8)
        insertPending(approvalDao, "BIZ:S2", requestedAt = now - 31 * day, points = 3)
        insertPending(approvalDao, "BIZ:F1", requestedAt = now - 2 * day, points = 4)
        // 计数流与清理列表同源（同一条 threshold 判据）
        assertEquals(2, repo.getStalePendingApprovalCount(now - PointRepository.STALE_PENDING_MILLIS).first())
        assertEquals(3, approvalDao.getPendingCount().first())

        val cleaned = repo.expireStalePendingApprovals(now)

        assertEquals(2, cleaned, "两笔严格超期全清，边界内新单保留")
        assertEquals(0, repo.getStalePendingApprovalCount(now - PointRepository.STALE_PENDING_MILLIS).first())
        assertEquals(1, approvalDao.getPendingCount().first(), "清理后仅剩边界内的新申请")
        assertEquals(2, approvalDao.approvals.count { it.status == ApprovalStatus.CANCELLED })
        assertTrue(pointDao.records.isEmpty(), "PENDING 从未计分：清理不产生任何账本记录")
        assertEquals(0, repo.getTotalPoints().first(), "不变量 5：余额 = SUM = 0，分毫不动")
    }

    @Test
    fun expireStalePendingApprovals_secondRunIsIdempotentAndWritesNothing(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val now = 100_000L * day
        insertPending(approvalDao, "BIZ:D11-A", requestedAt = now - 40 * day)
        insertPending(approvalDao, "BIZ:D11-B", requestedAt = now - 35 * day)
        assertEquals(2, repo.expireStalePendingApprovals(now))

        val approvalsBefore = approvalDao.approvals.toList()
        val ledgerBefore = pointDao.records.map { it.points }

        val second = repo.expireStalePendingApprovals(now)

        assertEquals(0, second, "二次清理必须返回 0（UI snackbar 如实报 0）")
        assertEquals(approvalsBefore, approvalDao.approvals.toList(), "二次清理审批列表零变动")
        assertEquals(ledgerBefore, pointDao.records.map { it.points }, "二次清理账本零变动")
    }

    @Test
    fun expireStalePendingApprovals_taskPendingRowGoesThroughUndoCheckInClosedLoop(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        // 真任务打卡：完成记录 + PENDING 申请（requestedAt = 真实当前时钟），
        // 以「未来基准时刻 = 提交时刻 + 31 天」判超期，清理必须走 undoCheckIn 半区：
        // 置 CANCELLED + 删除当日完成记录 + 零积分记录（与孩子端撤销未审批打卡同形）。
        repo.checkInForTask(taskId = 7L, points = 6, note = "语文抄写")
        val today = PointRepository.getTodayString()
        val submittedAt = approvalDao.approvals.single().requestedAt
        assertTrue(pointDao.getCompletedTaskIdsForDate(today).first().contains(7L), "前置：打卡有完成记录")

        val cleaned = repo.expireStalePendingApprovals(submittedAt + 31 * day)

        assertEquals(1, cleaned)
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.approvals.single().status)
        assertTrue(pointDao.getCompletedTaskIdsForDate(today).first().isEmpty(), "闭环同步清掉当日完成记录")
        assertTrue(pointDao.records.isEmpty(), "未审批清理不写积分记录")
        assertEquals(0, repo.getTotalPoints().first(), "余额零变化")
    }

    @Test
    fun expireStalePendingApprovals_legacyEmptyBizKeyRow_isCancelledByIdWithoutCollateral(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val now = 100_000L * day
        // v2 迁移遗留：bizKey 为空的行无法被统一撤销锚定（revokeApproval 会保守拒绝），
        // 清理侧按 id 直接 cancelPending 窄 CAS 兜底（报告披露项）。同形另两行必须毫发无损：
        // 空 bizKey 的新 PENDING（不超期）与空 bizKey 的 APPROVED 遗留行（非 PENDING）。
        val legacyStaleId = insertPending(approvalDao, "", requestedAt = now - 32 * day, points = 9)
        val legacyFreshId = insertPending(approvalDao, "", requestedAt = now - 3 * day, points = 7)
        val legacyApprovedId = insertPending(approvalDao, "", requestedAt = now - 50 * day, points = 11,
            status = ApprovalStatus.APPROVED)

        val cleaned = repo.expireStalePendingApprovals(now)

        assertEquals(1, cleaned, "只清那一条超期空 bizKey 行")
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(legacyStaleId)?.status)
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(legacyFreshId)?.status, "绝不误伤其它空 bizKey 单")
        assertEquals(ApprovalStatus.APPROVED, approvalDao.getById(legacyApprovedId)?.status)
        assertTrue(pointDao.records.isEmpty(), "兜底同样零扣回、零入账")
        assertEquals(0, repo.getTotalPoints().first())
    }

    @Test
    fun expireStalePendingApprovals_stalePendingShadowedByNewerApprovedSameBizKey_isSkippedWithoutReversal(): Unit = runBlocking {
        // D-11 review fix：清理按 id 复读、仅当陈旧行仍是其 bizKey 的最新一条才撤销。
        // v2 遗留重复：同 bizKey 陈旧 PENDING（id 小）+ 更新 APPROVED（id 大）并存时，
        // 旧实现会经 getLatestByBizKey 命中 APPROVED 半区追加 REVERSAL 扣回——
        // 新实现整行跳过：不动账本、不动新单、cleaned=0，陈旧行保持 PENDING（跳过语义）。
        val (pointDao, approvalDao, repo) = newRepo()
        val now = 100_000L * day
        val staleDupId = insertPending(approvalDao, "BIZ:DUP", requestedAt = now - 40 * day, points = 5)
        val newerApprovedId = insertPending(approvalDao, "BIZ:DUP", requestedAt = now - 10 * day, points = 5,
            status = ApprovalStatus.APPROVED)
        val ledgerBefore = pointDao.records.toList()

        val cleaned = repo.expireStalePendingApprovals(now)

        assertEquals(0, cleaned, "陈旧行不是 bizKey 最新一条：跳过且不计入 cleaned，snackbar 不虚报")
        assertEquals(ApprovalStatus.PENDING, approvalDao.getById(staleDupId)?.status, "跳过语义：陈旧行原样保留")
        assertEquals(ApprovalStatus.APPROVED, approvalDao.getById(newerApprovedId)?.status, "更新单绝不误伤")
        assertEquals(ledgerBefore, pointDao.records.toList(), "零写入：不产生 REVERSAL，余额与'余额不变'承诺一致")
        assertEquals(0, repo.getTotalPoints().first(), "不变量 5：余额分毫不动")
    }
}

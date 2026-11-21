package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v3.3.1 D-10（家长撤回审批闭环，Antigravity 指令 §4.4）仓库层回归套件。
 *
 * 组装方式与 [PointRepositoryInvariantTest] 相同：**手写 Fake DAO**（复用其同包
 * [FakePointDao] / [FakePointApprovalDao]，RA9 不引入 JVM sqlite），钉住家长入口
 * [PointRepository.revokeApproval] 的三条要求：
 * 1. 撤回 APPROVED → 余额扣回 + REVERSAL 反向记录 + 状态转 REVOKED（不变量 4 家长侧对称）；
 * 2. 二次撤回 0 变动（终态幂等拒绝，账本与状态都不动）；
 * 3. 入口只复用 [PointRepository.undoCheckIn]，不新写扣回逻辑——所以这里对账本/状态的
 *    断言与孩子端不变量 4 逐条同形（+N、-N、REVERSAL、approvalId 关联、净额 0、余额=SUM）。
 *
 * 另钉两条入口护栏：PENDING 走同一条 undoCheckIn 语义（置 CANCELLED、0 扣回）；
 * 空 bizKey 的 v2 遗留 APPROVED 行保守拒绝（0 变动，绝不凭 getLatestByBizKey("") 误伤他单）。
 */
class PointRepositoryParentRevokeTest {

    private fun newRepo(): Triple<FakePointDao, FakePointApprovalDao, PointRepository> {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        return Triple(pointDao, approvalDao, PointRepository(pointDao, approvalDao))
    }

    @Test
    fun revokeApproval_whenApproved_clawsBackBalanceAppendsReversalAndRevokes(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:D10-APPROVED", "TEST", "描述", 8, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        repo.approveAndAward(approvalId)
        assertEquals(8, repo.getTotalPoints().first(), "前置：审批通过后余额 +8")

        val clawedBack = repo.revokeApproval(approvalId)

        assertEquals(8, clawedBack, "家长撤回必须如实返回扣回分值（8 分）")
        assertEquals(2, pointDao.records.size, "append-only：不删原记录，只追加 REVERSAL")
        val original = pointDao.records[0]
        val reversal = pointDao.records[1]
        assertEquals(8, original.points)
        assertEquals(-8, reversal.points)
        assertEquals(PointRecordType.REVERSAL, reversal.type)
        assertEquals(approvalId, reversal.approvalId, "REVERSAL 必须关联原审批单（审计轨迹）")
        assertEquals(ApprovalStatus.REVOKED, approvalDao.approvals.single().status)
        assertEquals(0, original.points + reversal.points, "不变量 4：净额必须归零")
        assertEquals(0, repo.getTotalPoints().first(), "不变量 5：余额 = SUM = 0（已扣回）")
    }

    @Test
    fun revokeApproval_secondCall_returnsZeroAndWritesNothing(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:D10-DOUBLE", "TEST", "描述", 6, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        repo.approveAndAward(approvalId)
        repo.revokeApproval(approvalId)

        val ledgerBefore = pointDao.records.map { it.points }
        val approvalsBefore = approvalDao.approvals.toList()

        val second = repo.revokeApproval(approvalId)

        assertEquals(0, second, "REVOKED 是终态：二次撤回必须 0 扣回")
        assertEquals(ledgerBefore, pointDao.records.map { it.points }, "二次撤回账本零变动")
        assertEquals(approvalsBefore, approvalDao.approvals.toList(), "二次撤回审批单零变动")
        assertEquals(ApprovalStatus.REVOKED, approvalDao.approvals.single().status)
    }

    @Test
    fun revokeApproval_whenPending_setsCancelledAndZeroClawedBack(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:D10-PENDING", "TEST", "描述", 5, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id

        val clawedBack = repo.revokeApproval(approvalId)

        assertEquals(0, clawedBack, "未审批：本就没分，无扣回")
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.approvals.single().status)
        assertTrue(pointDao.records.isEmpty(), "不变量 1/4：未审批撤回不产生任何积分记录")
    }

    @Test
    fun revokeApproval_terminalRejected_returnsZeroWithoutWrites(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:D10-REJECTED", "TEST", "描述", 4, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        repo.rejectApproval(approvalId)

        val clawedBack = repo.revokeApproval(approvalId)

        assertEquals(0, clawedBack, "REJECTED 终态不可再撤回（UI 不显示入口，仓库层幂等兜底）")
        assertEquals(ApprovalStatus.REJECTED, approvalDao.approvals.single().status)
        assertTrue(pointDao.records.isEmpty(), "拒绝单从未计分，撤回不得写账本")
    }

    @Test
    fun revokeApproval_legacyEmptyBizKey_refusesWithoutTouchingAnyApproval(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        // v2 迁移遗留：bizKey 为空的历史 APPROVED 行（approveAndAward 的防重同样跳过它们）。
        // 统一撤销按 bizKey 锚定，入口必须保守拒绝——绝不把 getLatestByBizKey("") 命中的
        // "另一条空 bizKey 单"误置终态。
        val legacyId = approvalDao.insert(
            PointApprovalEntity(
                actionType = "OTHER",
                description = "遗留已通过单（无 bizKey）",
                requestedPoints = 9,
                status = ApprovalStatus.APPROVED,
                date = "2026-01-01"
            )
        )

        val clawedBack = repo.revokeApproval(legacyId)

        assertEquals(0, clawedBack, "空 bizKey 无法锚定撤销闭环，必须返回 0 不做扣回")
        assertEquals(ApprovalStatus.APPROVED, approvalDao.approvals.single().status, "保守拒绝：状态不动")
        assertTrue(pointDao.records.isEmpty(), "保守拒绝：不写 REVERSAL")
        assertEquals(0, repo.revokeApproval(9999L), "查无此单同样返回 0")
    }
}

package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * D-03（v3.3.1 指令 §4.1 P1-01）仓库层新接口 [PointRepository.getTaskApprovalStates] 的钉死套件。
 *
 * 组装沿用 [PointRepositoryInvariantTest] 的 RA9 模式：**手写 Fake DAO**（复用同包
 * [FakePointDao] / [FakePointApprovalDao]，不引入 JVM sqlite）。Fake 的
 * `getTaskApprovalsForDate` 与 Room SQL（`bizKey LIKE 'TASK:%' AND date = :date ORDER BY id ASC`）
 * 逐字同判据，本套件钉三条 brief 要求（要求 2）：
 * 1. **同一 bizKey 多条取最新（id 最大）**——拒绝后重打卡追加新行，状态必须跟着最新行走
 *    （[sameBizKeyMultipleRows_latestByIdWins]，顺带钉住要求 4 的数据前提：checkInForTask
 *    对 REJECTED 的 bizKey 天然新建 PENDING）；
 * 2. **跨日不串**——只认 date 列等于入参当日的行（[crossDay_statesDoNotMix]）；
 * 3. **非 `TASK:` 前缀不混入**——布置任务/习惯等 taskId 不得进映射（[nonTaskBizKeyPrefixes_excluded]）。
 *
 * bizKey 前缀与日期编码以 [PointRepository.checkInForTask] 的生成逻辑为准：
 * `TASK:<taskId>:<yyyy-MM-dd>`，date 列与 bizKey 尾巴同源同值。
 */
class PointRepositoryTaskApprovalStateTest {

    private fun newRepo(): Triple<FakePointDao, FakePointApprovalDao, PointRepository> {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        return Triple(pointDao, approvalDao, PointRepository(pointDao, approvalDao))
    }

    @Test
    fun sameBizKeyMultipleRows_latestByIdWins(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()
        val today = PointRepository.getTodayString()

        // 打卡 → 唯一一条 PENDING：⏳
        repo.checkInForTask(taskId = 7, points = 3, note = "每日阅读")
        assertEquals(mapOf(7L to ApprovalStatus.PENDING), repo.getTaskApprovalStates(today).first())

        // 家长拒绝：同 bizKey 只有一条，状态跟随为 REJECTED（⛔）
        approvalDao.updateReview(approvalDao.approvals.single().id, ApprovalStatus.REJECTED, null)
        assertEquals(mapOf(7L to ApprovalStatus.REJECTED), repo.getTaskApprovalStates(today).first())

        // 拒绝后重打卡（D-03 要求 4 的数据前提）：checkInForTask 新建**第二条** PENDING，
        // 同 bizKey 两条行必须取 id 最大的一条 → ⏳，而不是旧 REJECTED
        val again = repo.checkInForTask(taskId = 7, points = 3, note = "每日阅读")
        assertEquals(CheckInResult.CREATED, again)
        assertEquals(2, approvalDao.approvals.count { it.bizKey == "TASK:7:$today" })
        assertEquals(mapOf(7L to ApprovalStatus.PENDING), repo.getTaskApprovalStates(today).first())

        // 新一条再被拒：id 序不倒退，映射仍跟随最新行
        approvalDao.updateReview(approvalDao.approvals.maxByOrNull { it.id }!!.id, ApprovalStatus.REJECTED, null)
        assertEquals(mapOf(7L to ApprovalStatus.REJECTED), repo.getTaskApprovalStates(today).first())
    }

    @Test
    fun crossDay_statesDoNotMix(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()

        // 直接落两行不同归属日期的 task 申请：昨天的 APPROVED 与今天的 PENDING 互不串台
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE", description = "完成任务：阅读", requestedPoints = 3,
                status = ApprovalStatus.APPROVED, taskId = 11,
                bizKey = "TASK:11:2026-01-01", date = "2026-01-01"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE", description = "完成任务：练琴", requestedPoints = 5,
                status = ApprovalStatus.PENDING, taskId = 12,
                bizKey = "TASK:12:2026-01-02", date = "2026-01-02"
            )
        )

        assertEquals(mapOf(11L to ApprovalStatus.APPROVED), repo.getTaskApprovalStates("2026-01-01").first())
        assertEquals(mapOf(12L to ApprovalStatus.PENDING), repo.getTaskApprovalStates("2026-01-02").first())
        assertEquals(emptyMap(), repo.getTaskApprovalStates("2026-01-03").first())
    }

    @Test
    fun nonTaskBizKeyPrefixes_excluded(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val today = PointRepository.getTodayString()

        // 任务打卡（TASK: 前缀）作为参照系
        repo.checkInForTask(taskId = 20, points = 4, note = "整理错题本")

        // 同 taskId 空间可能相撞的非任务类申请：布置任务 ASSIGN、习惯 HABIT、错题 MISTAKE_REV——
        // 都带 date=today 且带 taskId，**必须**被 bizKey LIKE 'TASK:%' 判据挡在映射之外
        repo.checkIn(bizKey = "ASSIGN:20:$today", actionType = "ASSIGNMENT",
            description = "完成布置任务: 数学卷", points = 6, taskId = 20, date = today)
        repo.checkIn(bizKey = "HABIT:21:$today", actionType = "HABIT_CHECKIN",
            description = "习惯打卡: 早睡", points = 2, taskId = 21, date = today)
        repo.checkIn(bizKey = "MISTAKE_REV:22:$today", actionType = "MISTAKE_REVIEW",
            description = "错题复习：分数计算", points = 3, taskId = 22, date = today)

        val states = repo.getTaskApprovalStates(today).first()
        assertEquals(setOf(20L), states.keys, "只有 TASK: 前缀的申请进映射；ASSIGN/HABIT/MISTAKE_REV 不混入")
        assertEquals(ApprovalStatus.PENDING, states[20L])
        // 三条非任务申请确实各带 taskId=20/21/22 落库了（排除依据必须是 bizKey 前缀，不是 taskId 缺失）
        assertEquals(1, approvalDao.approvals.count { it.taskId == 20L && !it.bizKey.startsWith("TASK:") })
        assertFalse(pointDao.completions.any { it.taskId == 21L }, "通用 checkIn 不写 task_completions（不变量口径核对）")
    }
}

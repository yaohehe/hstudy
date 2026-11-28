package com.hstudy.app.data.repository

import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import com.hstudy.app.data.local.entity.TaskEntity
import com.hstudy.app.data.local.entity.TaskFrequency
import com.hstudy.app.time.FixedAppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun legacyDailyApprovalIsReusedForWeeklyPeriodAndUndo(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 7,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "完成任务：周任务",
                requestedPoints = 5,
                taskId = task.id,
                bizKey = "TASK:7:2026-03-30",
                activeBizKey = "TASK:7:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )

        assertEquals(
            CheckInResult.ALREADY_PENDING,
            repo.checkInForTask(task, "2026-03-31", FixedAppClock(0L))
        )
        assertEquals(listOf("TASK:7:2026-03-30"), approvalDao.approvals.map { it.bizKey })
        assertEquals(1, pointDao.completions.size)
        assertEquals(
            mapOf(7L to ApprovalStatus.PENDING),
            repo.getTaskApprovalStates(listOf(task), LocalDate(2026, 3, 31)).first()
        )

        assertEquals(0, repo.undoTaskCheckIn(task, "2026-03-31"))
        assertTrue(pointDao.completions.isEmpty())
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.approvals.single().status)
    }

    @Test
    fun legacyApprovedAndRejectedDuplicatesStillBlockAnotherPeriodApproval(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 31,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        val period = "WEEK:2026-03-30"
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-30",
                frequencyPeriod = period
            )
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-31",
                frequencyPeriod = period
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday",
                requestedPoints = 5,
                status = ApprovalStatus.APPROVED,
                taskId = task.id,
                bizKey = "TASK:31:2026-03-30",
                frequencyPeriod = period,
                date = "2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday",
                requestedPoints = 5,
                status = ApprovalStatus.REJECTED,
                taskId = task.id,
                bizKey = "TASK:31:2026-03-31",
                frequencyPeriod = period,
                date = "2026-03-31"
            )
        )

        assertEquals(
            CheckInResult.ALREADY_CREDITED,
            repo.checkInForTask(task, "2026-04-01", FixedAppClock(0L))
        )
        assertEquals(2, approvalDao.approvals.size)
        assertEquals(2, pointDao.completions.size)
    }

    @Test
    fun secondLegacyApprovalInSamePeriodIsCancelledInsteadOfAwarded(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 32,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        val period = "WEEK:2026-03-30"
        val mondayId = approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday",
                requestedPoints = 5,
                taskId = task.id,
                bizKey = "TASK:32:2026-03-30",
                frequencyPeriod = period,
                date = "2026-03-30"
            )
        )
        val tuesdayId = approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday",
                requestedPoints = 5,
                taskId = task.id,
                bizKey = "TASK:32:2026-03-31",
                frequencyPeriod = period,
                date = "2026-03-31"
            )
        )

        assertEquals(5, repo.approveAndAward(mondayId))
        assertEquals(0, repo.approveAndAward(tuesdayId))
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(tuesdayId)?.status)
        assertEquals(listOf(5), pointDao.records.map { it.points })
    }

    @Test
    fun missingApprovalUsesCompletionSnapshotAndDoesNotRewritePending(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 33,
            title = "原任务名",
            category = TaskCategory.BASIC,
            points = 5,
            bonusPoints = 2,
            frequency = TaskFrequency.WEEKLY
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 9,
                note = "原任务名",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )

        assertEquals(CheckInResult.CREATED, repo.checkInForTask(task, "2026-03-31", FixedAppClock(0L)))
        val pending = approvalDao.approvals.single()
        assertEquals(9, pending.requestedPoints)
        assertEquals("完成任务：原任务名", pending.description)
        assertEquals("TASK:33:WEEK:2026-03-30", pending.bizKey)
        assertEquals("WEEK:2026-03-30", pending.frequencyPeriod)

        assertEquals(
            CheckInResult.ALREADY_PENDING,
            repo.checkInForTask(task.copy(title = "改名后", points = 20), "2026-04-01", FixedAppClock(0L))
        )
        assertEquals(pending, approvalDao.approvals.single())
    }

    @Test
    fun undoDeletesOnlyTheApprovalDateCompletionAmongPeriodDuplicates(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 36,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        val period = "WEEK:2026-03-30"
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-30",
                frequencyPeriod = period
            )
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-31",
                frequencyPeriod = period
            )
        )
        val approvalId = approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday",
                requestedPoints = 5,
                taskId = task.id,
                bizKey = "TASK:36:2026-03-31",
                date = "2026-03-31",
                frequencyPeriod = period
            )
        )

        assertEquals(0, repo.undoTaskCheckIn(task, "2026-04-01"))
        assertEquals(listOf("2026-03-30"), pointDao.completions.map { it.date })
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.getById(approvalId)?.status)
    }

    @Test
    fun frequencyChangedUndoDoesNotDeleteCompletionFromAnotherPeriod(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val weekly = TaskEntity(
            id = 34,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        repo.checkInForTask(weekly, "2026-03-30", FixedAppClock(0L))

        assertEquals(0, repo.undoTaskCheckIn(weekly.copy(frequency = TaskFrequency.DAILY), "2026-03-31"))
        assertEquals(1, pointDao.completions.size)
        assertEquals("WEEK:2026-03-30", pointDao.completions.single().frequencyPeriod)
        assertEquals(ApprovalStatus.PENDING, approvalDao.approvals.single().status)
    }

    @Test
    fun laterDayUndoWithoutApprovalDeletesExactPeriodCompletion(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 35,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        pointDao.insertCompletion(
            TaskCompletionEntity(
                taskId = task.id,
                pointsEarned = 5,
                date = "2026-03-23",
                frequencyPeriod = "WEEK:2026-03-23"
            )
        )

        assertEquals(0, repo.undoTaskCheckIn(task, "2026-03-31"))
        assertEquals(listOf("2026-03-23"), pointDao.completions.map { it.date })
        assertTrue(approvalDao.approvals.isEmpty())
    }

    @Test
    fun approvedDominatesNewerRejectedApprovalInSamePeriod(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 41,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday",
                requestedPoints = 5,
                status = ApprovalStatus.APPROVED,
                taskId = task.id,
                bizKey = "TASK:41:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday",
                requestedPoints = 5,
                status = ApprovalStatus.REJECTED,
                taskId = task.id,
                bizKey = "TASK:41:2026-03-31",
                date = "2026-03-31",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )

        assertEquals(
            mapOf(task.id to ApprovalStatus.APPROVED),
            repo.getTaskApprovalStates(listOf(task), LocalDate(2026, 3, 31)).first()
        )
        assertEquals(
            CheckInResult.ALREADY_CREDITED,
            repo.checkInForTask(task, "2026-04-01", FixedAppClock(0L))
        )
    }

    @Test
    fun pendingDominatesNewerTerminalApprovalAfterLatestPerBizKeySelection(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 42,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday rejected",
                requestedPoints = 5,
                status = ApprovalStatus.REJECTED,
                taskId = task.id,
                bizKey = "TASK:42:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday pending retry",
                requestedPoints = 5,
                taskId = task.id,
                bizKey = "TASK:42:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday rejected",
                requestedPoints = 5,
                status = ApprovalStatus.REJECTED,
                taskId = task.id,
                bizKey = "TASK:42:2026-03-31",
                date = "2026-03-31",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )

        assertEquals(
            mapOf(task.id to ApprovalStatus.PENDING),
            repo.getTaskApprovalStates(listOf(task), LocalDate(2026, 3, 31)).first()
        )
    }

    @Test
    fun terminalPeriodStateUsesLatestPerBizKeyThenLatestTerminalRow(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()
        val task = TaskEntity(
            id = 43,
            title = "周任务",
            category = TaskCategory.BASIC,
            points = 5,
            frequency = TaskFrequency.WEEKLY
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday approved",
                requestedPoints = 5,
                status = ApprovalStatus.APPROVED,
                taskId = task.id,
                bizKey = "TASK:43:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Monday later rejected",
                requestedPoints = 5,
                status = ApprovalStatus.REJECTED,
                taskId = task.id,
                bizKey = "TASK:43:2026-03-30",
                date = "2026-03-30",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )
        approvalDao.insert(
            PointApprovalEntity(
                actionType = "TASK_COMPLETE",
                description = "Tuesday cancelled",
                requestedPoints = 5,
                status = ApprovalStatus.CANCELLED,
                taskId = task.id,
                bizKey = "TASK:43:2026-03-31",
                date = "2026-03-31",
                frequencyPeriod = "WEEK:2026-03-30"
            )
        )

        assertEquals(
            mapOf(task.id to ApprovalStatus.CANCELLED),
            repo.getTaskApprovalStates(listOf(task), LocalDate(2026, 3, 31)).first()
        )
    }

    @Test
    fun periodStateUsesOneCompletionQueryAndOneApprovalQuery(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val tasks = TaskFrequency.entries.mapIndexed { index, frequency ->
            TaskEntity(
                id = 40L + index,
                title = frequency.name,
                category = TaskCategory.BASIC,
                points = 5,
                frequency = frequency
            )
        }
        tasks.forEach { task ->
            repo.checkInForTask(task, "2026-03-31", FixedAppClock(0L))
        }
        pointDao.completionStateQueries = 0
        approvalDao.approvalStateQueries = 0

        assertEquals(tasks.map { it.id }.toSet(), repo.getCompletedTaskIdsForPeriod(tasks, LocalDate(2026, 3, 31)).first())
        assertEquals(1, pointDao.completionStateQueries)
        assertEquals(0, approvalDao.approvalStateQueries)

        assertEquals(
            tasks.associate { it.id to ApprovalStatus.PENDING },
            repo.getTaskApprovalStates(tasks, LocalDate(2026, 3, 31)).first()
        )
        assertEquals(1, pointDao.completionStateQueries, "approval state must not subscribe to completion ranges")
        assertEquals(1, approvalDao.approvalStateQueries)
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

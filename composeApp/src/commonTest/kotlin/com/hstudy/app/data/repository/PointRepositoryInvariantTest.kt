package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 积分生命周期五条不变量的核心单元测试套件（Antigravity 指令 P0-03 / §2.4 七用例，RA9）。
 *
 * 组装方式：**手写 Fake DAO**（Room DAO 是接口，纯内存实现，不引入 JVM sqlite 依赖），
 * 用 [FakePointDao] + [FakePointApprovalDao] 拼出 [PointRepository]，逐条钉死
 * 积分规则与审批逻辑说明.md §二 的五条不变量：
 * 1. 打卡不计分（[checkIn_doesNotWritePointRecord]）；
 * 2. 一业务一生效申请（[checkIn_duplicateCall_reusesExistingPendingApproval] 与
 *    [checkIn_afterUndoApproved_createsNewPendingAndAwardsCorrectly]）；
 * 3. 审批幂等（[approveAndAward_calledTwice_onlyCreditsOnce]）；
 * 4. 撤销对称 CANCELLED|REVERSAL（[undoCheckIn_whenPending_setsCancelledAndZeroPointsClawedBack] 与
 *    [undoCheckIn_whenApproved_appendsReversalRecordAndClawsBackPoints]）；
 * 5. 账本 append-only、余额=SUM（[totalPoints_equalsSumOfAllPointRecords]）。
 *
 * 测试用 `checkIn(bizKey=…, actionType="TASK_TEST", …)` 通用入口即可覆盖全部不变量；
 * [FakePointDao.completions] 同时被任务打卡路径使用（本套件不依赖具体 actionType 语义）。
 */
class PointRepositoryInvariantTest {

    private fun newRepo(): Triple<FakePointDao, FakePointApprovalDao, PointRepository> {
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        return Triple(pointDao, approvalDao, PointRepository(pointDao, approvalDao))
    }

    // ==================================================================
    // 不变量 1：打卡绝不直接写入积分流水
    // ==================================================================

    @Test
    fun checkIn_doesNotWritePointRecord(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        val result = repo.checkIn(
            bizKey = "TEST:1:2026-01-01",
            actionType = "TEST",
            description = "测试打卡",
            points = 5,
            date = "2026-01-01"
        )
        assertEquals(CheckInResult.CREATED, result)
        assertTrue(
            pointDao.records.isEmpty(),
            "不变量 1 被破坏：打卡写了积分记录 ${pointDao.records.map { it.points }}"
        )
        assertEquals(1, approvalDao.approvals.size)
        assertEquals(ApprovalStatus.PENDING, approvalDao.approvals.single().status)
        assertEquals(5, approvalDao.approvals.single().requestedPoints)
    }

    // ==================================================================
    // 不变量 2：重复打卡复用同一申请，绝不生成两条 PENDING
    // ==================================================================

    @Test
    fun checkIn_duplicateCall_reusesExistingPendingApproval(): Unit = runBlocking {
        val (_, approvalDao, repo) = newRepo()
        val first = repo.checkIn("BIZ:DUP", "TEST", "描述A", 3, date = "2026-01-01")
        val second = repo.checkIn("BIZ:DUP", "TEST", "描述A", 3, date = "2026-01-01")
        assertEquals(CheckInResult.CREATED, first)
        assertEquals(CheckInResult.ALREADY_PENDING, second)
        assertEquals(1, approvalDao.approvals.count { it.status == ApprovalStatus.PENDING })
        assertEquals(1, approvalDao.approvals.size)
    }

    // ==================================================================
    // 不变量 3：审批幂等性验证，重复批准只加一次积分
    // ==================================================================

    @Test
    fun approveAndAward_calledTwice_onlyCreditsOnce(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:IDEMPOTENT", "TEST", "描述", 4, date = "2026-01-01")
        val approval = approvalDao.approvals.single()

        val awardedOnce = repo.approveAndAward(approval.id)
        val awardedTwice = repo.approveAndAward(approval.id)

        assertEquals(4, awardedOnce)
        assertEquals(0, awardedTwice, "不变量 3 被破坏：重复审批第二次仍发放")
        assertEquals(1, pointDao.records.size)
        assertEquals(4, pointDao.records.single().points)
        assertEquals(ApprovalStatus.APPROVED, approvalDao.approvals.single().status)
    }

    // ==================================================================
    // 不变量 4：撤销对称 —— 未审批撤销 → CANCELLED，净额 0
    // ==================================================================

    @Test
    fun undoCheckIn_whenPending_setsCancelledAndZeroPointsClawedBack(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:PENDING-UNDO", "TEST", "描述", 6, date = "2026-01-01")

        val clawedBack = repo.undoCheckIn(bizKey = "BIZ:PENDING-UNDO", date = "2026-01-01")

        assertEquals(0, clawedBack)
        assertEquals(ApprovalStatus.CANCELLED, approvalDao.approvals.single().status)
        assertTrue(
            pointDao.records.isEmpty(),
            "未审批的撤销不该产生任何积分记录（本就没分，也无残留）"
        )
    }

    // ==================================================================
    // 不变量 4：撤销对称 —— 已审批撤销 → 追加 REVERSAL，净额归零
    // ==================================================================

    @Test
    fun undoCheckIn_whenApproved_appendsReversalRecordAndClawsBackPoints(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:APPROVED-UNDO", "TEST", "描述", 7, date = "2026-01-01")
        val approvalId = approvalDao.approvals.single().id
        repo.approveAndAward(approvalId)

        val clawedBack = repo.undoCheckIn(bizKey = "BIZ:APPROVED-UNDO", date = "2026-01-01")

        assertEquals(7, clawedBack)
        assertEquals(2, pointDao.records.size, "append-only：撤销不删原记录，只追加反向记录")
        val original = pointDao.records[0]
        val reversal = pointDao.records[1]
        assertEquals(7, original.points)
        assertEquals(-7, reversal.points)
        assertEquals(PointRecordType.REVERSAL, reversal.type)
        assertEquals(approvalId, reversal.approvalId)
        assertEquals(ApprovalStatus.REVOKED, approvalDao.approvals.single().status)
        assertEquals(0, original.points + reversal.points, "净额必须归零")
    }

    // ==================================================================
    // 不变量 2 & 4：撤销后重新打卡 → 新 PENDING，审批后依然只入账一次
    // ==================================================================

    @Test
    fun checkIn_afterUndoApproved_createsNewPendingAndAwardsCorrectly(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:RECHECKIN", "TEST", "描述", 5, date = "2026-01-01")
        repo.approveAndAward(approvalDao.approvals.single().id)
        repo.undoCheckIn(bizKey = "BIZ:RECHECKIN", date = "2026-01-01")

        // 撤销已审批后重新打卡：生成一条**新** PENDING（旧的一条已是 REVOKED）
        val again = repo.checkIn("BIZ:RECHECKIN", "TEST", "描述", 5, date = "2026-01-01")
        assertEquals(CheckInResult.CREATED, again)
        assertEquals(2, approvalDao.approvals.size)
        assertEquals(1, approvalDao.approvals.count { it.status == ApprovalStatus.PENDING })

        repo.approveAndAward(approvalDao.approvals.last().id)

        // 账本：+5、-5、+5 —— 同一业务净额只入账一次
        assertEquals(listOf(5, -5, 5), pointDao.records.map { it.points })
        assertEquals(5, pointDao.records.sumOf { it.points })
        assertEquals(1, approvalDao.approvals.count { it.status == ApprovalStatus.APPROVED })
    }

    // ==================================================================
    // 不变量 5：账本 append-only，总积分恒等于所有流水的代数和
    // ==================================================================

    @Test
    fun totalPoints_equalsSumOfAllPointRecords(): Unit = runBlocking {
        val (pointDao, approvalDao, repo) = newRepo()
        repo.checkIn("BIZ:A1", "TEST", "描述1", 3, date = "2026-01-01")
        repo.approveAndAward(approvalDao.approvals.last().id)
        repo.adjustPoints(-2, "家长扣分")
        repo.spendPoints(1, "兑换消耗")
        repo.checkIn("BIZ:A2", "TEST", "描述2", 10, date = "2026-01-02")
        repo.approveAndAward(approvalDao.approvals.last().id)
        repo.undoCheckIn(bizKey = "BIZ:A2", date = "2026-01-02")

        val expectedSum = pointDao.records.sumOf { it.points }
        assertEquals(3 - 2 - 1 + 10 - 10, expectedSum)
        assertEquals(expectedSum, repo.getTotalPoints().first(), "余额必须恒等于 SUM(point_records.points)")
    }

    // ==================================================================
    // P0-01 接线（Antigravity 指令 规则 6/7）：错题复习与举一反三必须走统一审批 checkIn+bizKey
    // TDD：以下两条先于实现写入 —— 对现行代码（MistakeRepository 单参构造、无 checkIn 接线）
    // 直接编译失败（RED），实现后转绿（GREEN）。FakeMistakeDao 复用 MistakeRepositoryTest.kt 的同包实现。
    // ==================================================================

    @Test
    fun reviewMistake_withPoints_createsPendingApproval(): Unit = runBlocking {
        val mistakeDao = FakeMistakeDao()
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val repo = MistakeRepository(mistakeDao, PointRepository(pointDao, approvalDao))
        val mistakeId = repo.addMistake(MistakeEntity(subject = MistakeSubject.MATH, title = "分数计算"))

        repo.reviewMistake(mistakeId, points = 3, note = "主动回忆-做对")

        val today = PointRepository.getTodayString()
        val pending = approvalDao.approvals.filter { it.status == ApprovalStatus.PENDING }
        assertEquals(1, pending.size, "规则 6：做对 +3 必须生成且只生成一条待审申请")
        val approval = pending.single()
        assertEquals("MISTAKE_REVIEW", approval.actionType)
        assertEquals(3, approval.requestedPoints)
        assertEquals("MISTAKE_REV:$mistakeId:$today", approval.bizKey)
        assertEquals("错题复习：主动回忆-做对", approval.description)
        assertTrue(pointDao.records.isEmpty(), "不变量 1：复习打卡当场绝不写积分流水")

        // 同日重复复习同一道错题：复用同一 bizKey 的待审单，不产生第二条（不变量 2 防刷）
        repo.reviewMistake(mistakeId, points = 1, note = "主动回忆-没记住")
        assertEquals(1, approvalDao.approvals.count { it.status == ApprovalStatus.PENDING })
    }

    @Test
    fun saveVariation_createsPendingApproval(): Unit = runBlocking {
        val mistakeDao = FakeMistakeDao()
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val repo = MistakeRepository(mistakeDao, PointRepository(pointDao, approvalDao))
        val mistakeId = repo.addMistake(MistakeEntity(subject = MistakeSubject.MATH, title = "追及问题"))
        val mistake = repo.getMistakeById(mistakeId)!!

        repo.saveVariation(mistake, "换成相遇问题再解一遍")

        val today = PointRepository.getTodayString()
        val pending = approvalDao.approvals.filter { it.status == ApprovalStatus.PENDING }
        assertEquals(1, pending.size, "规则 7：举一反三必须生成待审申请")
        val approval = pending.single()
        assertEquals("VARIATION", approval.actionType)
        assertEquals(3, approval.requestedPoints)
        assertEquals("VARIATION:$mistakeId:$today", approval.bizKey)
        assertEquals("举一反三思考: 追及问题", approval.description)
        assertTrue(pointDao.records.isEmpty(), "不变量 1：举一反三当场绝不写积分流水")
    }

    // ==================================================================
    // P2-01（Antigravity 指令 规则 16）：系统自动惩罚扣分必须落 PENALTY 类型，
    // 与家长手动调整（ADJUSTMENT）在账本上可区分。
    // TDD：先于实现写入 —— 对现行代码（无 applyPenalty、无 PointRecordType.PENALTY）
    // 直接编译失败（RED），实现后转绿（GREEN）。Fake DAO 复用本套件既有实现（RA9）。
    // ==================================================================

    // ==================================================================
    // 终审 M-2（同日复习分值不降级）：ensureApproval 复用 PENDING 时分值只升不降。
    // 场景：同一天先"做对 +3"挂 PENDING，再走列表页快捷复习 +1（同 bizKey）——
    // 已挂起的 3 分必须保持 3（取 max），审批通过后 +3 恰好入账一次。
    // ==================================================================

    @Test
    fun reviewMistake_sameDayLowerPoints_keepsPendingMaxAndAwardsOnce(): Unit = runBlocking {
        val mistakeDao = FakeMistakeDao()
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val pointRepo = PointRepository(pointDao, approvalDao)
        val repo = MistakeRepository(mistakeDao, pointRepo)
        val mistakeId = repo.addMistake(MistakeEntity(subject = MistakeSubject.MATH, title = "单位换算"))

        repo.reviewMistake(mistakeId, points = 3, note = "主动回忆-做对")
        repo.reviewMistake(mistakeId, points = 1, note = "间隔复习")

        val pending = approvalDao.approvals.filter { it.status == ApprovalStatus.PENDING }
        assertEquals(1, pending.size, "不变量 2：同日重复复习仍只有一条待审申请")
        assertEquals(
            3, pending.single().requestedPoints,
            "终审 M-2：同日低分值复习不得把已挂起的 +3 待审申请改低（分值只升不降）"
        )

        val awarded = pointRepo.approveAndAward(pending.single().id)
        assertEquals(3, awarded, "审批按挂起的 3 分发放")
        assertEquals(listOf(3), pointDao.records.map { it.points }, "审批后只 +3 恰好入账一次")
        assertEquals(3, pointRepo.getTotalPoints().first(), "不变量 5：余额 = SUM = 3")
    }

    @Test
    fun applyPenalty_appendsNegativePenaltyRecord(): Unit = runBlocking {
        val (pointDao, _, repo) = newRepo()
        repo.applyPenalty(points = 6, note = "每日必须任务未完成自动扣分")

        assertEquals(1, pointDao.records.size, "账本 append-only：扣分只追加一条流水")
        val penalty = pointDao.records.single()
        assertEquals(-6, penalty.points, "applyPenalty 必须写入 -points（扣分）")
        assertEquals(PointRecordType.PENALTY, penalty.type, "P2-01：系统扣分必须是 PENALTY 类型，不得混入 ADJUSTMENT")
        assertEquals("每日必须任务未完成自动扣分", penalty.note)
        assertEquals(-6, repo.getTotalPoints().first(), "不变量 5：余额恒等于 SUM(points)")
    }
}

/**
 * [PointDao] 的内存 Fake（RA9：不引入 JVM sqlite，纯手写实现）。
 * [records] 只增不改不删（append-only 由接口本身无 update/delete 记录方法保证）。
 */
class FakePointDao : PointDao {

    val records = mutableListOf<PointRecordEntity>()
    val completions = mutableListOf<TaskCompletionEntity>()
    private var nextRecordId = 1L
    private var nextCompletionId = 1L

    override suspend fun insertPointRecord(record: PointRecordEntity): Long {
        val id = nextRecordId++
        records.add(record.copy(id = id))
        return id
    }

    override fun getAllPointRecords(): Flow<List<PointRecordEntity>> =
        MutableStateFlow(records.toList())

    override fun getPointRecordsBetween(start: Long, end: Long): Flow<List<PointRecordEntity>> =
        MutableStateFlow(records.filter { it.date in start until end })

    override fun getTotalPoints(): Flow<Int> = MutableStateFlow(records.sumOf { it.points })

    override fun getLifetimeEarnedPoints(): Flow<Int> =
        MutableStateFlow(records.filter { it.points > 0 }.sumOf { it.points })

    override suspend fun getPositivePointsBetween(start: Long, end: Long): Int =
        records.filter { it.points > 0 && it.date in start until end }.sumOf { it.points }

    override fun getPointsBetween(start: Long, end: Long): Flow<Int> =
        MutableStateFlow(records.filter { it.date in start until end }.sumOf { it.points })

    override fun getWeeklyPoints(weekStart: Long): Flow<Int> =
        MutableStateFlow(records.filter { it.weekStart == weekStart }.sumOf { it.points })

    override suspend fun insertCompletion(completion: TaskCompletionEntity): Long {
        val id = nextCompletionId++
        completions.add(completion.copy(id = id))
        return id
    }

    override fun getCompletionsByDate(date: String): Flow<List<TaskCompletionEntity>> =
        MutableStateFlow(completions.filter { it.date == date })

    override suspend fun getCompletionForTaskOnDate(taskId: Long, date: String): TaskCompletionEntity? =
        completions.firstOrNull { it.taskId == taskId && it.date == date }

    override fun getCompletedTaskIdsForDate(date: String): Flow<List<Long>> =
        MutableStateFlow(completions.filter { it.date == date }.map { it.taskId })

    override fun getCompletionsBetween(start: Long, end: Long): Flow<List<TaskCompletionEntity>> =
        MutableStateFlow(completions.filter { it.completedAt in start until end })

    override fun getEarnedPointsBetween(start: Long, end: Long): Flow<Int> =
        MutableStateFlow(completions.filter { it.completedAt in start until end }.sumOf { it.pointsEarned })

    override suspend fun deleteCompletionForTaskOnDate(taskId: Long, date: String) {
        completions.removeAll { it.taskId == taskId && it.date == date }
    }

    override suspend fun getDistinctCompletionDates(since: Long): List<String> =
        completions.filter { it.completedAt >= since }.map { it.date }.distinct()

    override suspend fun getTotalCompletionCount(): Int = completions.size
}

/** [PointApprovalDao] 的内存 Fake（RA9）。 */
class FakePointApprovalDao : PointApprovalDao {

    val approvals = mutableListOf<PointApprovalEntity>()
    private var nextId = 1L

    override fun getAllApprovals(): Flow<List<PointApprovalEntity>> =
        MutableStateFlow(approvals.toList())

    override fun getApprovalsByStatus(status: ApprovalStatus): Flow<List<PointApprovalEntity>> =
        MutableStateFlow(approvals.filter { it.status == status })

    override fun getPendingCount(): Flow<Int> =
        MutableStateFlow(approvals.count { it.status == ApprovalStatus.PENDING })

    override suspend fun insert(approval: PointApprovalEntity): Long {
        val id = nextId++
        approvals.add(approval.copy(id = id))
        return id
    }

    override suspend fun update(approval: PointApprovalEntity) {
        val index = approvals.indexOfFirst { it.id == approval.id }
        if (index >= 0) approvals[index] = approval
    }

    override suspend fun updateStatus(id: Long, status: ApprovalStatus, reviewedAt: Long) {
        val index = approvals.indexOfFirst { it.id == id }
        if (index >= 0) approvals[index] = approvals[index].copy(status = status, reviewedAt = reviewedAt)
    }

    override suspend fun approveAll(reviewedAt: Long) {
        approvals.replaceAll {
            if (it.status == ApprovalStatus.PENDING) it.copy(status = ApprovalStatus.APPROVED, reviewedAt = reviewedAt) else it
        }
    }

    override suspend fun getLatestByBizKey(bizKey: String): PointApprovalEntity? =
        approvals.lastOrNull { it.bizKey == bizKey }

    override suspend fun getPendingByBizKey(bizKey: String): PointApprovalEntity? =
        approvals.lastOrNull { it.bizKey == bizKey && it.status == ApprovalStatus.PENDING }

    override suspend fun getActiveAwardedByBizKey(bizKey: String): PointApprovalEntity? =
        approvals.lastOrNull { it.bizKey == bizKey && it.status == ApprovalStatus.APPROVED }

    override suspend fun getById(id: Long): PointApprovalEntity? =
        approvals.firstOrNull { it.id == id }

    override suspend fun updateReview(id: Long, status: ApprovalStatus, awardedRecordId: Long?, reviewedAt: Long) {
        val index = approvals.indexOfFirst { it.id == id }
        if (index >= 0) {
            approvals[index] = approvals[index].copy(
                status = status,
                reviewedAt = reviewedAt,
                awardedRecordId = awardedRecordId
            )
        }
    }

    override suspend fun getPendingList(): List<PointApprovalEntity> =
        approvals.filter { it.status == ApprovalStatus.PENDING }

    /**
     * D-03 单查询的内存 Fake：判据与 Room SQL `bizKey LIKE 'TASK:%' AND date = :date`
     * 逐字对齐（前缀 startsWith + date 列相等），id 升序返回（真实 DAO 的 ORDER BY id ASC）。
     */
    override fun getTaskApprovalsForDate(date: String): Flow<List<PointApprovalEntity>> =
        MutableStateFlow(
            approvals.filter { it.bizKey.startsWith("TASK:") && it.date == date }.sortedBy { it.id }
        )

    override fun getPendingPoints(): Flow<Int> =
        MutableStateFlow(approvals.filter { it.status == ApprovalStatus.PENDING }.sumOf { it.requestedPoints })
}

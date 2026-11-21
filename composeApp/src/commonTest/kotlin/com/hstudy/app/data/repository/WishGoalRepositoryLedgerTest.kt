package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.WishGoalDao
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.WishGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-08（v3.3.1，指令 §3.3 / P0-03）心愿存钱罐账本回归套件。
 *
 * 组装方式沿用 [PointRepositoryInvariantTest] 的 RA9 约定：**手写内存 Fake DAO**（Room DAO 是接口，
 * 不引入 JVM sqlite），把 [WishGoalRepository] 接到真实的 [PointRepository] 上 ——
 * 本任务的缺陷恰恰在"仓库之间怎么接"，用假 PointRepository 会把要钉的东西全放过去。
 *
 * 钉死四件事：
 * 1. 存入是 **REWARD_EXCHANGE**（[depositPoints_usesRewardExchangeType]），不再混进家长调整口径；
 * 2. 删除进行中的心愿**原路退还**，且退还是**新增正向记录**、当初那条消耗一字不改
 *    （[deleteGoal_withSavedPoints_refundsToBalance]，不变量 5：append-only、余额 = SUM(points)）；
 * 3. 没存过钱就**不产生退款流水**（[deleteGoal_zeroSaved_noRefundRecord]，零资金动作零记录）；
 * 4. note 只存纯 ASCII 业务键，**不落展示文案**（各条 note 断言 +
 *    [wishNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape]，Ruling 25 存储边界）。
 */
class WishGoalRepositoryLedgerTest {

    private class Fixture(
        val wishDao: FakeWishGoalDao,
        val pointDao: FakePointDao,
        val pointRepository: PointRepository,
        val wishRepository: WishGoalRepository
    )

    private fun newFixture(): Fixture {
        val wishDao = FakeWishGoalDao()
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val pointRepository = PointRepository(pointDao, approvalDao)
        return Fixture(wishDao, pointDao, pointRepository, WishGoalRepository(wishDao, pointRepository))
    }

    /** 给孩子一笔可用余额（家长加分 = ADJUSTMENT，这是它唯一该出现的口径）。 */
    private suspend fun Fixture.fund(points: Int) = pointRepository.adjustPoints(points, "家长加分")

    /** 账本不变量 5 的逐条复核：余额必须恒等于所有流水的代数和。 */
    private suspend fun Fixture.assertBalanceEqualsLedgerSum(expected: Int) {
        assertEquals(expected, pointDao.records.sumOf { it.points }, "SUM(point_records.points) 变了")
        assertEquals(expected, pointRepository.getTotalPoints().first(), "不变量 5：余额 = SUM(points)")
    }

    // ==================================================================
    // D-08 根因 1：存入必须是 REWARD_EXCHANGE，且 note 不得携带展示文案
    // ==================================================================

    @Test
    fun depositPoints_usesRewardExchangeType(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "一台新平板", targetPoints = 5000))
        fx.fund(100)

        assertTrue(fx.wishRepository.depositPoints(goalId, 30), "余额充足时存入必须成功")

        val deposit = fx.pointDao.records.last()
        assertEquals(
            PointRecordType.REWARD_EXCHANGE, deposit.type,
            "D-08 根因 1：存入是孩子的自主消耗，必须与商店消耗同口径（REWARD_EXCHANGE），不得记家长调整"
        )
        assertEquals(-30, deposit.points, "存入是扣减，符号必须为负")
        assertEquals(
            "wish_deposit:$goalId", deposit.note,
            "note 只存业务键；文案（含心愿名）不落库（Ruling 25）"
        )
        assertFalse(deposit.note.contains("平板"), "note 里不得出现心愿名等展示文案")
        assertEquals(30, fx.wishDao.getGoalById(goalId)?.savedPoints, "已存积分要累加到心愿")
        fx.assertBalanceEqualsLedgerSum(70)
    }

    @Test
    fun depositPoints_insufficientBalance_writesNothingAndKeepsSavedPoints(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "一本新书", targetPoints = 300))
        fx.fund(20)

        assertFalse(fx.wishRepository.depositPoints(goalId, 50), "余额不足必须返回 false")

        assertTrue(
            fx.pointDao.records.all { it.type == PointRecordType.ADJUSTMENT },
            "余额不足时不得留下任何消耗流水：${fx.pointDao.records.map { it.note }}"
        )
        assertEquals(0, fx.wishDao.getGoalById(goalId)?.savedPoints, "扣减失败时已存积分不得变动")
        fx.assertBalanceEqualsLedgerSum(20)
    }

    // ==================================================================
    // D-08 根因 2：删除不得吞掉已存积分
    // ==================================================================

    @Test
    fun deleteGoal_withSavedPoints_refundsToBalance(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "一个小玩具", targetPoints = 800))
        fx.fund(100)
        assertTrue(fx.wishRepository.depositPoints(goalId, 40))
        fx.assertBalanceEqualsLedgerSum(60)
        val depositRecord = fx.pointDao.records.last()

        fx.wishRepository.deleteGoal(goalId)

        assertNull(fx.wishDao.getGoalById(goalId), "心愿本身要删掉")
        val refund = fx.pointDao.records.last()
        assertTrue(refund !== depositRecord, "退款必须是**新增**一条记录，不是改写当初那条消耗")
        assertEquals(depositRecord, fx.pointDao.records[1], "不变量 5：append-only，当初的 -40 记录必须原样留在账上")
        assertEquals(PointRecordType.REWARD_EXCHANGE, refund.type, "退款与当初消耗同口径，不得混入家长调整（ADJUSTMENT）")
        assertEquals(40, refund.points, "退款必须是正向入账，金额等于已存积分")
        assertEquals("wish_refund:$goalId", refund.note, "退款 note 同样只存业务键")
        // 净额守恒：存入与退回互为相反数，孩子这一轮资金动作净影响为 0
        assertEquals(0, depositRecord.points + refund.points, "不变量 5：存入 + 退还的代数和必须归零")
        fx.assertBalanceEqualsLedgerSum(100)
    }

    @Test
    fun deleteGoal_zeroSaved_noRefundRecord(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "一次旅行", targetPoints = 5000))
        fx.fund(10)
        val recordsBefore = fx.pointDao.records.toList()

        fx.wishRepository.deleteGoal(goalId)

        assertEquals(recordsBefore, fx.pointDao.records.toList(), "没存过钱就不该产生任何流水（含零分记录）")
        assertNull(fx.wishDao.getGoalById(goalId))
        fx.assertBalanceEqualsLedgerSum(10)
    }

    /**
     * 已达成的心愿（isActive=false）是"已经花掉"的目标，删除不退款 ——
     * 家长端删除弹窗的"将自动退还"提示用同一个条件，才不会承诺不会发生的事。
     */
    @Test
    fun deleteGoal_inactiveGoalWithSavedPoints_doesNotRefund(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "已经换来的乐高", targetPoints = 100, savedPoints = 100))
        fx.wishDao.markCompleted(goalId, 1_700_000_000_000L)
        fx.fund(100)
        val recordsBefore = fx.pointDao.records.toList()

        fx.wishRepository.deleteGoal(goalId)

        assertEquals(recordsBefore, fx.pointDao.records.toList(), "非进行中心愿删除不得产生退款流水")
        assertNull(fx.wishDao.getGoalById(goalId))
    }

    @Test
    fun deleteGoal_unknownId_isNoOp(): Unit = runBlocking {
        val fx = newFixture()
        fx.fund(10)
        val recordsBefore = fx.pointDao.records.toList()

        fx.wishRepository.deleteGoal(9999L)

        assertEquals(recordsBefore, fx.pointDao.records.toList(), "查无此心愿时不得写任何流水")
    }

    // ==================================================================
    // note 业务键：形状自证（明细页据此派生文案，旧记录一律解析不出）
    // ==================================================================

    @Test
    fun wishNoteKeys_arePureAsciiAndOnlyParseTheirOwnShape() {
        val goalId = 42L
        val deposit = WishGoalRepository.wishDepositNote(goalId)
        val refund = WishGoalRepository.wishRefundNote(goalId)
        assertEquals("wish_deposit:42", deposit)
        assertEquals("wish_refund:42", refund)
        // 存储边界：note 进 DB 的字节必须纯 ASCII（英文用户不会在账本里看到中文）
        assertTrue(deposit.all { it.code < 128 } && refund.all { it.code < 128 }, "note 业务键必须纯 ASCII")

        assertEquals(WishNoteKey(goalId, WishNoteKind.DEPOSIT), WishGoalRepository.parseWishNoteKey(deposit))
        assertEquals(WishNoteKey(goalId, WishNoteKind.REFUND), WishGoalRepository.parseWishNoteKey(refund))
        // 商店消耗、家长手写备注、D-08 之前留下的中文记录：解析不出 → 页面原样展示，不改写旧账
        listOf(
            "",
            "兑换：一台新平板",
            "存入心愿存钱罐：一台新平板",      // D-08 之前的旧写入形状
            "喂养宠物",
            "wish_deposit:",                  // 缺 id
            "wish_deposit:abc",               // id 非数字
            "wish_deposit:-1",                // id 非法
            "wish_deposit:1x",
            "pre:wish_deposit:1",             // 不是前缀开头
        ).forEach { legacy ->
            assertNull(WishGoalRepository.parseWishNoteKey(legacy), "不该被解析成心愿业务键：$legacy")
        }
    }

    /** 存入到刚好达成：达成标记照旧（重构没有动第 3 步的判定）。 */
    @Test
    fun depositPoints_reachingTarget_marksGoalCompleted(): Unit = runBlocking {
        val fx = newFixture()
        val goalId = fx.wishDao.insert(WishGoalEntity(name = "一本新书", targetPoints = 50))
        fx.fund(50)

        assertTrue(fx.wishRepository.depositPoints(goalId, 50))

        val goal = fx.wishDao.getGoalById(goalId)!!
        assertEquals(50, goal.savedPoints)
        assertFalse(goal.isActive, "攒够即达成")
        assertEquals(1, fx.pointDao.records.count { it.type == PointRecordType.REWARD_EXCHANGE })
        fx.assertBalanceEqualsLedgerSum(0)
    }
}

/**
 * [WishGoalDao] 的内存 Fake（RA9：与 [FakePointDao] 同一套约定，不引入 sqlite）。
 * [addSavedPoints]/[markCompleted] 在真机上是 UPDATE 语句，这里如实改字段，
 * 这样"退款后心愿已删除"与"存入累加"两条链路都能被断言到。
 */
class FakeWishGoalDao : WishGoalDao {

    val goals = mutableListOf<WishGoalEntity>()
    private var nextId = 1L

    override suspend fun insert(goal: WishGoalEntity): Long {
        val id = nextId++
        goals.add(goal.copy(id = id))
        return id
    }

    override suspend fun update(goal: WishGoalEntity) {
        val index = goals.indexOfFirst { it.id == goal.id }
        if (index >= 0) goals[index] = goal
    }

    override suspend fun delete(id: Long) {
        goals.removeAll { it.id == id }
    }

    override fun getAllGoals(): Flow<List<WishGoalEntity>> = MutableStateFlow(goals.toList())

    override fun getActiveGoals(): Flow<List<WishGoalEntity>> =
        MutableStateFlow(goals.filter { it.isActive })

    override suspend fun getGoalById(id: Long): WishGoalEntity? = goals.firstOrNull { it.id == id }

    override suspend fun addSavedPoints(id: Long, amount: Int) {
        val index = goals.indexOfFirst { it.id == id }
        if (index >= 0) {
            val goal = goals[index]
            goals[index] = goal.copy(savedPoints = goal.savedPoints + amount)
        }
    }

    override suspend fun markCompleted(id: Long, completedAt: Long) {
        val index = goals.indexOfFirst { it.id == id }
        if (index >= 0) goals[index] = goals[index].copy(isActive = false, completedAt = completedAt)
    }
}

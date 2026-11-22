package com.hstudy.app.ui.child

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.repository.FakePointApprovalDao
import com.hstudy.app.data.repository.FakePointDao
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.platform.Platform
import com.hstudy.app.ui.common.BonusNoteKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-09（v3.3.1 指令 §4.3）首页系统奖励防重入回归套件 —— [BonusClaimGuard] 层。
 *
 * 组装沿用 [com.hstudy.app.data.repository.WishGoalRepositoryLedgerTest] 的 RA9 约定：
 * 真 [PointRepository] + 手写内存 Fake DAO（账本断言看真实落库形状），claimed 存储用
 * [FakeBonusClaimStore] —— 它以"互斥块内完成读取-判定-写入 + 可挂起的落盘窗口"复刻
 * DataStore.edit 的可观察语义（真 DataStore 的 CAS 由 [UserPreferencesBonusClaimCasTest] 直接验证），
 * 挂起点让"第一击在飞、第二击到达"的双击时序在 runTest 的单一测试调度器上**确定复现**，
 * 而不是靠真实毫秒赛跑。
 *
 * 钉死的事：
 * 1. 里程碑双击在飞：第二击被互斥当场拒绝（不排队补发），账本**只有一条**入账
 *    （[doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit]，要求 5 的主例）；
 * 2. 同一协程顺序重入 / 陈旧 UI 重试：CAS 已置位 → 第二次不再入账
 *    （[sequentialReclaimIsRejectedByCas]）；
 * 3. isClaimingBonus 与互斥同源：在飞期间三处入口全部禁用（[inFlightDisablesAllThreeEntries]，要求 3）；
 * 4. 置位后入账抛异常：claimed **不回滚**（append-only，要求 2），锁复位不死锁，
 *    重试不补发（[depositThrows_claimStaysSetAndNoDoubleDeposit]）。
 */
class BonusClaimGuardTest {

    /**
     * claimed 存储的测试替身：CAS 语义与 UserPreferences 的 tryClaim* 一致
     * （读取-判定-写入在互斥块内完成），[onEnterEdit] 钩子把"落盘"变成可控挂起点，
     * 用来模拟 DataStore flow 回流到 UI 之前的双击窗口。
     */
    private class FakeBonusClaimStore {
        private val editMutex = Mutex()
        private val claimedMilestones = mutableSetOf<Int>()
        private var claimedWeek: String? = null
        private var excellentBonusDate: String = ""

        var editInvocations = 0
            private set

        /** 测试钩子：进入 CAS 临界区后先执行/挂起在这里，再判定写入。 */
        var onEnterEdit: (suspend () -> Unit)? = null

        private suspend fun edit(block: () -> Boolean): Boolean = editMutex.withLock {
            editInvocations++
            onEnterEdit?.invoke()
            block()
        }

        suspend fun tryClaimStreakMilestone(days: Int): Boolean = edit { claimedMilestones.add(days) }

        suspend fun tryClaimWeeklyChallenge(weekStartStr: String): Boolean = edit {
            val won = claimedWeek != weekStartStr
            if (won) claimedWeek = weekStartStr
            won
        }

        suspend fun tryClaimDailyExcellentBonus(date: String): Boolean = edit {
            val won = excellentBonusDate != date
            if (won) excellentBonusDate = date
            won
        }
    }

    private class Fixture {
        val guard = BonusClaimGuard()
        val store = FakeBonusClaimStore()
        val pointDao = FakePointDao()
        val points = PointRepository(pointDao, FakePointApprovalDao())

        /** 与 ChildHomeScreen 里程碑按钮 onClick 同构：先置位，赢下 CAS 才入账（note=D-12 业务键）。 */
        suspend fun claimMilestone(days: Int, bonus: Int): Boolean =
            guard.claim(
                tryClaim = { store.tryClaimStreakMilestone(days) },
                onClaimed = { points.adjustPoints(bonus, BonusNoteKeys.streak(days)) }
            )

        suspend fun claimWeeklyChallenge(weekStartStr: String): Boolean =
            guard.claim(
                tryClaim = { store.tryClaimWeeklyChallenge(weekStartStr) },
                onClaimed = { points.adjustPoints(5, BonusNoteKeys.weekly(weekStartStr)) }
            )
    }

    @Test
    fun doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit() = runTest {
        val fx = Fixture()
        val releaseEdit = CompletableDeferred<Unit>()
        fx.store.onEnterEdit = { releaseEdit.await() }

        // 第一击：进入 CAS 临界区后停在"落盘"挂起点 —— 旧缺陷里这一刻 UI 的 claimed
        // 集合（collectAsState）仍是空，按钮依旧可点。
        val firstStrike = launch { fx.claimMilestone(days = 7, bonus = 15) }
        testScheduler.advanceUntilIdle()
        assertTrue(fx.guard.isClaimingBonus, "第一击在飞，isClaimingBonus 必须为 true（按钮禁用的同一状态源）")

        // 第二击：并发的另一个协程（双击 = 两个 onClick 协程）
        val secondStrike = launch { fx.claimMilestone(days = 7, bonus = 15) }
        testScheduler.advanceUntilIdle()
        assertTrue(secondStrike.isCompleted, "第二击必须被互斥当场拒绝并立即结束，而不是排队等锁后补发第二份分")

        assertEquals(0, fx.pointDao.records.size, "第一击尚未入账时，账本必须为空")
        assertEquals(1, fx.store.editInvocations, "第二击不得触碰 claimed 存储")

        releaseEdit.complete(Unit)
        firstStrike.join()

        val records = fx.pointDao.records
        assertEquals(1, records.size, "D-09 核心断言：双击只入账一次")
        assertEquals(15, records.single().points)
        assertEquals(PointRecordType.ADJUSTMENT, records.single().type)
        assertEquals(BonusNoteKeys.streak(7), records.single().note, "note 是 D-12 业务键 `bonus_streak:7`（键形态见 PointRecordBonusNoteTest）")
        assertFalse(fx.guard.isClaimingBonus, "在飞结束后状态必须复位，按钮恢复可点")
    }

    @Test
    fun sequentialReclaimIsRejectedByCas() = runTest {
        val fx = Fixture()
        // 同一协程内顺序重入（含"陈旧 UI 状态下再点一次"：第一击已完成、DataStore flow 还没回流）
        assertTrue(fx.claimMilestone(days = 7, bonus = 15), "首次领取必须赢下 CAS 并入账")
        assertFalse(fx.claimMilestone(days = 7, bonus = 15), "已置位后的再领取必须被 CAS 拒绝")

        assertEquals(1, fx.pointDao.records.size, "同键重入只留一条账")
        assertEquals(2, fx.store.editInvocations, "第二次应走到 CAS 判定（由置位结果拦截）")
    }

    @Test
    fun inFlightDisablesAllThreeEntries() = runTest {
        val fx = Fixture()
        val releaseEdit = CompletableDeferred<Unit>()
        fx.store.onEnterEdit = { releaseEdit.await() }

        // 里程碑在飞时，每周挑战入口（同一 guard）也必须被拒 —— 对应三处按钮
        // enabled = !isClaimingBonus 同时变灰（要求 3：视觉禁用与防抖同源）。
        val milestone = launch { fx.claimMilestone(days = 3, bonus = 5) }
        testScheduler.advanceUntilIdle()
        assertTrue(fx.guard.isClaimingBonus)
        assertFalse(fx.claimWeeklyChallenge("2099-W1"), "另一处入口在飞期间同样不得进入")
        assertEquals(0, fx.pointDao.records.size)

        releaseEdit.complete(Unit)
        milestone.join()

        // 在飞结束：每周挑战恢复可领，两类奖励各自只入账一次（结构相同，一并钉死）
        assertFalse(fx.guard.isClaimingBonus)
        assertTrue(fx.claimWeeklyChallenge("2099-W1"))
        assertFalse(fx.claimWeeklyChallenge("2099-W1"), "同一周重复领取被 CAS 拒绝")

        val bonusRecords = fx.pointDao.records.filter { it.points > 0 }
        assertEquals(2, bonusRecords.size, "里程碑 +5 与每周挑战 +5 各一条，双击/重发均为 0 条")
        assertEquals(listOf(5, 5), bonusRecords.map { it.points })
    }

    @Test
    fun depositThrows_claimStaysSetAndNoDoubleDeposit() = runTest {
        val fx = Fixture()
        var ledgerDown = true
        val won = fx.guard.claim(
            tryClaim = { fx.store.tryClaimDailyExcellentBonus("2099-01-02") },
            onClaimed = {
                if (ledgerDown) error("账本写入失败（理论不可达路径）")
                fx.points.adjustPoints(2, BonusNoteKeys.excellent("2099-01-02"))
            }
        )
        ledgerDown = false

        assertFalse(won, "入账抛异常时 claim 报告失败，但异常必须被吞住，不得击穿 UI 协程作用域")
        assertEquals(0, fx.pointDao.records.size)
        assertFalse(fx.guard.isClaimingBonus, "finally 必须复位锁与在飞状态 —— 不留下永久禁用的按钮")

        // 要求 2 的语义：置位不回滚（append-only 口径）。该奖励作废，重试也不会双发。
        assertFalse(
            fx.guard.claim(
                tryClaim = { fx.store.tryClaimDailyExcellentBonus("2099-01-02") },
                onClaimed = { fx.points.adjustPoints(2, BonusNoteKeys.excellent("2099-01-02")) }
            ),
            "claimed 不回滚 → 同键重试必须继续被 CAS 拒绝（宁可作废，不可双发）"
        )
        assertEquals(0, fx.pointDao.records.size)
    }
}

/**
 * D-09 存储层直证：真 [UserPreferences]（okio 文件版 DataStore，装置同
 * [com.hstudy.app.i18n.LanguagePreferenceTest]：`Platform.init` 指到 build/ 下，
 * 进程内单例文件与真实数据目录隔离）。
 *
 * DataStore 的 edit 修改器串行执行 —— 三处 tryClaim* 的读取-判定-写入因此是原子的。
 * 每个用例用各自唯一的天数/周起始日/日期，避免单例文件下的用例串扰。
 *
 * v3.3.1 Task 8 注记：`dataStoreInstance` 的 produceFile 在进程内首次访问时定死目录，
 * 本类文件可能与 i18n/专注用例共享且跨运行持久。周/日用例是覆盖写语义可重复跑；
 * 里程碑集合是追加式持久键，固定天数在第二次运行时两侧 CAS 都会输（winners=0），
 * 故 streak 用例改为基于当前已领集合推导一个必然未领的天数，保证任意次重跑稳定。
 */
class UserPreferencesBonusClaimCasTest {

    private fun newPrefs(): UserPreferences {
        Platform.init(filesDir = "build/test-d09-prefs", cacheDir = "build/test-d09-prefs")
        return UserPreferences()
    }

    @Test
    fun tryClaimStreakMilestone_concurrentDoubleClaim_exactlyOneWins() = runTest {
        val prefs = newPrefs()
        // 追加式持久键：必须相对当前（可能跨运行残留的）已领集合取新天数，否则重跑必败
        val days = (prefs.claimedStreakMilestones.first().maxOrNull() ?: 0) + 91
        val first = async { prefs.tryClaimStreakMilestone(days) }
        val second = async { prefs.tryClaimStreakMilestone(days) }
        val winners = listOf(first.await(), second.await()).count { it }
        assertEquals(1, winners, "并发领取同一里程碑：恰好一个协程赢下 CAS（入账权只有一份）")
        assertTrue(prefs.claimedStreakMilestones.first().contains(days))
    }

    @Test
    fun tryClaimWeeklyChallenge_sameWeekOnce_newWeekAgain() = runTest {
        val prefs = newPrefs()
        assertTrue(prefs.tryClaimWeeklyChallenge("2099-01-05"))
        assertFalse(prefs.tryClaimWeeklyChallenge("2099-01-05"), "同一周重复领取必须 false")
        assertTrue(prefs.tryClaimWeeklyChallenge("2099-01-12"), "换周是新窗口（语义与原 claimWeeklyChallenge 覆盖写一致）")
        assertEquals("2099-01-12", prefs.weeklyChallengeClaimedWeek.first())
    }

    @Test
    fun tryClaimDailyExcellentBonus_concurrentSameDay_exactlyOneWins_nextDayOpensAgain() = runTest {
        val prefs = newPrefs()
        val first = async { prefs.tryClaimDailyExcellentBonus("2099-02-03") }
        val second = async { prefs.tryClaimDailyExcellentBonus("2099-02-03") }
        val winners = listOf(first.await(), second.await()).count { it }
        assertEquals(1, winners, "同日双击优秀奖励：恰好一个协程赢下 CAS")
        assertEquals("2099-02-03", prefs.dailyExcellentBonusDate.first())
        assertTrue(prefs.tryClaimDailyExcellentBonus("2099-02-04"), "次日恢复可领")
    }
}

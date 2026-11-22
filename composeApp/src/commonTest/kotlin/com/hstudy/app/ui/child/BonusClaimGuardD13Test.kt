package com.hstudy.app.ui.child

import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FakePointApprovalDao
import com.hstudy.app.data.repository.FakePointDao
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.platform.Platform
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
 * D-13（v3.3.2 裁决修复批 Task B）复习里程碑与幸运转盘防重入回归 —— [BonusClaimGuard] 层。
 *
 * 装置与 [BonusClaimGuardTest]（D-09）同型：真 [PointRepository] + 手写内存 Fake DAO
 * （账本断言看真实落库形状），claimed 存储用 [FakeD13ClaimStore] —— 以"互斥块内完成
 * 读取-判定-写入 + 可挂起的落盘窗口"复刻 DataStore.edit 的可观察语义（真 DataStore 的
 * CAS 由 [UserPreferencesD13CasTest] 直接验证），双击时序在 runTest 单一调度器上确定复现。
 *
 * 钉死的事：
 * 1. 复习里程碑（ChildMistakeListScreen 同构）在飞双击：第二击被互斥当场拒绝，账本只有一条
 *    （[reviewMilestone_doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit]）；
 * 2. 幸运转盘（ChildLuckyDrawScreen 同构）在飞双击：奖只发一份，同日资格只消耗一次
 *    （[luckyDraw_doubleClickWhileFirstInFlight_secondDrawDoesNotDeposit]）；
 * 3. 顺序重入被 CAS 拒绝（同档里程碑、同日抽奖），换日/新档恢复可领
 *    （[reviewMilestone_sequentialReclaimIsRejectedByCas] / [luckyDraw_sameDayReclaimRejected_nextDayOpensAgain]）；
 * 4. 入账时序为"先置位后入账"：CAS 未赢的协程对账本零触碰
 *    （各双击用例中 records/editInvocations 断言）。
 */
class BonusClaimGuardD13Test {

    /**
     * D-13 两枚 claim 键的测试替身：CAS 语义与 UserPreferences 的
     * tryClaimReviewStreakMilestone / tryClaimDailyLuckyDraw 一致，
     * [onEnterEdit] 钩子把"落盘"变成可控挂起点，模拟 DataStore flow 回流 UI 之前的双击窗口。
     */
    private class FakeD13ClaimStore {
        private val editMutex = Mutex()
        private val claimedReviewMilestones = mutableSetOf<String>()
        private var lastLuckyDraw: String? = null

        var editInvocations = 0
            private set

        /** 测试钩子：进入 CAS 临界区后先执行/挂起在这里，再判定写入。 */
        var onEnterEdit: (suspend () -> Unit)? = null

        private suspend fun edit(block: () -> Boolean): Boolean = editMutex.withLock {
            editInvocations++
            onEnterEdit?.invoke()
            block()
        }

        suspend fun tryClaimReviewStreakMilestone(days: Int): Boolean = edit {
            claimedReviewMilestones.add(days.toString())
        }

        suspend fun tryClaimDailyLuckyDraw(date: String): Boolean = edit {
            val won = lastLuckyDraw != date
            if (won) lastLuckyDraw = date
            won
        }
    }

    private class Fixture {
        val guard = BonusClaimGuard()
        val store = FakeD13ClaimStore()
        val pointDao = FakePointDao()
        val points = PointRepository(pointDao, FakePointApprovalDao())

        /** 与 ChildMistakeListScreen 里程碑按钮 onClick 同构（D-13 新形状：先 CAS 后置位再入账）。 */
        suspend fun claimReviewMilestone(days: Int, bonus: Int): Boolean =
            guard.claim(
                tryClaim = { store.tryClaimReviewStreakMilestone(days) },
                onClaimed = { points.adjustPoints(bonus, "连续复习${days}天里程碑奖励") }
            )

        /** 与 ChildLuckyDrawScreen 抽奖按钮 onClick 的后半段同构（动画 delay 不入 guard，发奖在 guard 内）。 */
        suspend fun drawLucky(today: String, won: Int): Boolean =
            guard.claim(
                tryClaim = { store.tryClaimDailyLuckyDraw(today) },
                onClaimed = { points.adjustPoints(won, "每日幸运抽奖") }
            )
    }

    @Test
    fun reviewMilestone_doubleClickWhileFirstInFlight_secondClaimDoesNotDeposit() = runTest {
        val fx = Fixture()
        val releaseEdit = CompletableDeferred<Unit>()
        fx.store.onEnterEdit = { releaseEdit.await() }

        // 第一击停在 CAS 临界区的"落盘"挂起点 —— 旧缺陷里这一刻 claimedReviewMilestones
        // 的 collectAsState 回流仍是旧集合，按钮依旧可点。
        val firstStrike = launch { fx.claimReviewMilestone(days = 7, bonus = 8) }
        testScheduler.advanceUntilIdle()
        assertTrue(fx.guard.isClaimingBonus, "第一击在飞，isClaimingBonus 必须为 true（按钮禁用的同一状态源）")

        // 第二击：双击 = 两个 onClick 协程
        val secondStrike = launch { fx.claimReviewMilestone(days = 7, bonus = 8) }
        testScheduler.advanceUntilIdle()
        assertTrue(secondStrike.isCompleted, "第二击必须被互斥当场拒绝并立即结束，而不是排队等锁后补发第二份分")

        assertEquals(0, fx.pointDao.records.size, "第一击尚未入账时，账本必须为空")
        assertEquals(1, fx.store.editInvocations, "第二击不得触碰 claimed 存储")

        releaseEdit.complete(Unit)
        firstStrike.join()

        val records = fx.pointDao.records
        assertEquals(1, records.size, "D-13 核心断言：复习里程碑双击只入账一次")
        assertEquals(8, records.single().points, "7 天档 bonus=8（档位表 3/7/14/30 → 3/8/15/30 未动）")
        assertEquals(PointRecordType.ADJUSTMENT, records.single().type)
        assertEquals("连续复习7天里程碑奖励", records.single().note, "note 保持 canonical 中文原样（D-12 未覆盖本入口）")
        assertFalse(fx.guard.isClaimingBonus, "在飞结束后状态必须复位，按钮恢复可点")
    }

    @Test
    fun reviewMilestone_sequentialReclaimIsRejectedByCas() = runTest {
        val fx = Fixture()
        assertTrue(fx.claimReviewMilestone(days = 14, bonus = 15), "首次领取必须赢下 CAS 并入账")
        assertFalse(fx.claimReviewMilestone(days = 14, bonus = 15), "已置位后的再领取（陈旧 UI 重试）必须被 CAS 拒绝")
        assertTrue(fx.claimReviewMilestone(days = 30, bonus = 30), "另一档位是新 CAS 键，不受 14 天档置位影响")

        val records = fx.pointDao.records
        assertEquals(2, records.size, "同档重入只留一条账：14 天档一条 + 30 天档一条")
        assertEquals(listOf(15, 30), records.map { it.points })
    }

    @Test
    fun luckyDraw_doubleClickWhileFirstInFlight_secondDrawDoesNotDeposit() = runTest {
        val fx = Fixture()
        val releaseEdit = CompletableDeferred<Unit>()
        fx.store.onEnterEdit = { releaseEdit.await() }

        // 转盘旧缺陷：canDraw/isDrawing 是纯 UI 软防抖，lastLuckyDraw flow 回流有延迟；
        // 两个在飞协程都动画结束进入发奖段时，只有赢下 CAS/guard 的一发入账。
        val firstDraw = launch { fx.drawLucky(today = "2099-08-02", won = 3) }
        testScheduler.advanceUntilIdle()
        assertTrue(fx.guard.isClaimingBonus, "第一次抽奖在飞（动画后发奖段），isClaimingBonus 必须为 true")

        val secondDraw = launch { fx.drawLucky(today = "2099-08-02", won = 5) }
        testScheduler.advanceUntilIdle()
        assertTrue(secondDraw.isCompleted, "第二次抽奖必须被互斥当场拒绝，不得排队补发")

        assertEquals(0, fx.pointDao.records.size)
        assertEquals(1, fx.store.editInvocations, "第二次抽奖不得触碰 lastLuckyDraw 存储")

        releaseEdit.complete(Unit)
        firstDraw.join()

        val records = fx.pointDao.records
        assertEquals(1, records.size, "D-13 核心断言：双击转盘只发一次奖")
        assertEquals(3, records.single().points)
        assertEquals("每日幸运抽奖", records.single().note, "note 保持 canonical 中文原样（Ruling 25 存量，D-12 未覆盖本入口）")
        assertFalse(fx.guard.isClaimingBonus)
    }

    @Test
    fun luckyDraw_sameDayReclaimRejected_nextDayOpensAgain() = runTest {
        val fx = Fixture()
        assertTrue(fx.drawLucky("2099-08-03", won = 1), "当日首抽必须赢下 CAS")
        assertFalse(fx.drawLucky("2099-08-03", won = 5), "同日重抽（换页重进/陈旧 UI）必须被 CAS 拒绝")
        assertEquals(1, fx.pointDao.records.size)
        assertTrue(fx.drawLucky("2099-08-04", won = 2), "换日是新一轮抽奖窗口（覆盖写语义不变）")

        val records = fx.pointDao.records
        assertEquals(2, records.size, "两天各一发奖；被拒的同日重抽对账本零触碰")
        assertEquals(listOf(1, 2), records.map { it.points })
        assertEquals(3, fx.store.editInvocations, "三次 onClick 都走到 CAS 判定（拦截发生在置位结果上）")
    }
}

/**
 * D-13 存储层直证：真 [UserPreferences]（okio 文件版 DataStore，装置同
 * [UserPreferencesBonusClaimCasTest]：`Platform.init` 指到 build/ 下，进程内单例 DataStore
 * 文件可能与 i18n/专注/D-09 用例共享且跨运行持久）。
 *
 * 幂等化教训（v3.3.1 Task 8）：追加式的 REVIEW_STREAK_MILESTONES_CLAIMED 用例必须相对当前
 * 已领集合推导新天数，否则重跑必败；LAST_LUCKY_DRAW 是覆盖写日期键，本类是唯一写入方，
 * 方法内先并发 A 再换新档 B 收尾，残留下次运行 A≠B 依然可赢，可重复跑。
 */
class UserPreferencesD13CasTest {

    private fun newPrefs(): UserPreferences {
        Platform.init(filesDir = "build/test-d09-prefs", cacheDir = "build/test-d09-prefs")
        return UserPreferences()
    }

    @Test
    fun tryClaimReviewStreakMilestone_concurrentDoubleClaim_exactlyOneWins() = runTest {
        val prefs = newPrefs()
        // 追加式持久键：必须相对当前（可能跨运行残留的）已领集合取新天数，否则重跑必败
        val days = (prefs.claimedReviewStreakMilestones.first().maxOrNull() ?: 0) + 97
        val first = async { prefs.tryClaimReviewStreakMilestone(days) }
        val second = async { prefs.tryClaimReviewStreakMilestone(days) }
        val winners = listOf(first.await(), second.await()).count { it }
        assertEquals(1, winners, "并发领取同一复习里程碑：恰好一个协程赢下 CAS（入账权只有一份）")
        assertTrue(prefs.claimedReviewStreakMilestones.first().contains(days))
        assertFalse(prefs.tryClaimReviewStreakMilestone(days), "已置位后的顺序再领取继续被拒")
    }

    @Test
    fun tryClaimDailyLuckyDraw_concurrentSameDay_exactlyOneWins_nextDayOpensAgain() = runTest {
        val prefs = newPrefs()
        val first = async { prefs.tryClaimDailyLuckyDraw("2099-08-05") }
        val second = async { prefs.tryClaimDailyLuckyDraw("2099-08-05") }
        val winners = listOf(first.await(), second.await()).count { it }
        assertEquals(1, winners, "同日双击抽奖：恰好一个协程赢下 CAS（发奖权只有一份）")
        assertEquals("2099-08-05", prefs.lastLuckyDraw.first())
        assertFalse(prefs.tryClaimDailyLuckyDraw("2099-08-05"), "同日重抽被 CAS 拒绝")
        assertTrue(prefs.tryClaimDailyLuckyDraw("2099-08-06"), "换日恢复可抽（与原 setLastLuckyDraw 覆盖写语义一致）")
    }
}

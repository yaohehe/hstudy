package com.hstudy.app.data.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-05/07（v3.3.1 指令 §4.2）番茄钟会话状态机的纯函数回归套件：
 * 全部注入假时钟（显式 nowMs 参数），**禁 sleep**。
 *
 * 钉死的公式（brief 要求 1"公式与测试都要钉住"）：
 * - `deadline(now) = start + planned*60_000 + pausedAccum + (暂停中 ? now - pauseStart : 0)`
 * - 暂停中剩余恒定（冻结）：`remaining = start + planned*60_000 + pausedAccum - pauseStart`，与 now 无关；
 * - 恢复时 pausedAccum 固化这段暂停，deadline 一次性顺延，之后照旧倒数。
 *
 * 恢复动作分支（要求 2/3）：RUNNING 跨导航续倒数 / FOCUS 到期补结算 / 已认领续跑入账 /
 * BREAK 过期静默清 / 暂停中**绝不**按真实流逝时间误到期。
 */
class PomodoroSessionStateTest {

    private val MIN = 60_000L

    private fun focus(start: Long, minutes: Int = 25, target: String = "自由学习") =
        PomodoroSessionState.startFocus(start, minutes, target)

    // ===== 要求 1：时间戳锚定 + 派生倒计时 =====

    @Test
    fun deadlineAndRemainingArePureFunctionsOfTimestamps() {
        val s = focus(1_000_000L)
        assertEquals(25 * MIN, s.deriveRemainingMs(1_000_000L), "起锚瞬间剩余=计划全量")
        assertEquals(1_000_000L + 25 * MIN, s.deadlineEpochMs(1_000_000L))
        // 跨导航恢复的数学本体：过了 5 分钟就只剩 20 分钟——不依赖任何内存态存活
        assertEquals(20 * MIN, s.deriveRemainingMs(1_000_000L + 5 * MIN))
        assertEquals(0L, s.deriveRemainingMs(1_000_000L + 99 * MIN), "过期后派生值下限 0，不出负数")
    }

    @Test
    fun expiryBoundaryExactlyAtDeadline() {
        val s = focus(0L)
        assertFalse(s.isExpired(25 * MIN - 1), "差 1ms 不算到期（不许提前结算）")
        assertEquals(0L, s.deriveRemainingMs(25 * MIN))
        assertTrue(s.isExpired(25 * MIN), "恰好到点即到期")
    }

    // ===== 要求 1：暂停=冻结 + 恢复=顺延（公式钉死） =====

    @Test
    fun pauseFreezesRemainingEvenPastOriginalDeadline() {
        val paused = focus(0L).paused(10 * MIN)
        assertEquals(15 * MIN, paused.deriveRemainingMs(10 * MIN))
        // 暂停 80 分钟、早已越过原始 deadline：剩余仍恒定 15 分钟（deadline 随 now 同步顺延）
        assertEquals(15 * MIN, paused.deriveRemainingMs(90 * MIN))
        assertFalse(paused.isExpired(90 * MIN), "暂停中的段不因真实流逝而到期")
        // paused 幂等：第二次暂停不重置暂停起点
        assertEquals(paused, focus(0L).paused(10 * MIN).paused(11 * MIN))
    }

    @Test
    fun resumeShiftsDeadlineByAccumulatedPausedMillis() {
        val resumed = focus(0L).paused(10 * MIN).resumed(40 * MIN) // 暂停了 30 分钟
        assertEquals(40 * MIN + 15 * MIN, resumed.deadlineEpochMs(41 * MIN), "deadline 顺延整段暂停时长")
        assertEquals(15 * MIN, resumed.deriveRemainingMs(40 * MIN), "恢复瞬间继承冻结值")
        assertEquals(14 * MIN, resumed.deriveRemainingMs(41 * MIN), "恢复后继续正常倒数")
        assertFalse(resumed.isExpired(54 * MIN))
        assertTrue(resumed.isExpired(55 * MIN))
        // resumed 幂等：未暂停时原样返回
        assertEquals(resumed, resumed.resumed(99 * MIN))
    }

    // ===== 要求 2/3：恢复动作逐分支 =====

    @Test
    fun restoreActionRunningAndExpiry() {
        val s = focus(0L)
        assertEquals(PomodoroRestoreAction.RESUME_TICKING, s.restoreAction(5 * MIN), "跨导航：未到期→继续倒数")
        assertEquals(PomodoroRestoreAction.SETTLE_FOCUS, s.restoreAction(25 * MIN), "到期→补结算")
        assertEquals(PomodoroRestoreAction.SETTLE_FOCUS, s.restoreAction(99 * MIN), "杀进程后隔天打开同样补结算")
    }

    @Test
    fun restoreActionBreakExpiryClearsSilently() {
        val b = PomodoroSessionState.startBreak(0L, 5, "自由学习")
        assertEquals(PomodoroRestoreAction.RESUME_TICKING, b.restoreAction(4 * MIN))
        assertEquals(PomodoroRestoreAction.CLEAR_EXPIRED_BREAK, b.restoreAction(5 * MIN), "过期休息段静默清除，绝不结算")
        assertEquals(PomodoroRestoreAction.CLEAR_EXPIRED_BREAK, b.restoreAction(60 * MIN))
    }

    @Test
    fun restoreActionSettledFocusIsResumeDeposit() {
        val settled = focus(0L).copy(settled = true, settleDate = "2099-01-01")
        // 已赢认领但未清会话（认领进程崩在"入账"窗口）：续跑入账，不再判到期、不再认领
        assertEquals(PomodoroRestoreAction.RESUME_DEPOSIT, settled.restoreAction(99 * MIN))
        val settledPaused = settled.paused(10 * MIN)
        // settled 后不再接受新暂停（paused 幂等返回自身）
        assertEquals(settled, settledPaused)
    }

    @Test
    fun focusCompletedToBreakReanchorsWithoutLeftoverPause() {
        val s = focus(0L, target = "攻克错题").paused(5 * MIN).resumed(50 * MIN)
        val b = s.focusCompletedToBreak(50 * MIN, breakMinutes = 5)
        assertEquals(PomodoroSessionPhase.BREAK, b.phase)
        assertEquals(50 * MIN, b.sessionStartEpochMs, "休息段重新锚定到专注到期时刻")
        assertEquals(5, b.plannedMinutes)
        assertEquals(0L, b.pausedAccumMs, "上一段的暂停累计不带入新段")
        assertEquals(null, b.pauseStartedAtEpochMs)
        assertEquals(false, b.settled, "休息段不结算")
        assertEquals("攻克错题", b.focusTarget, "目标跨段保留（结算/展示同口径）")
        assertEquals(5 * MIN, b.deriveRemainingMs(50 * MIN))
    }
}

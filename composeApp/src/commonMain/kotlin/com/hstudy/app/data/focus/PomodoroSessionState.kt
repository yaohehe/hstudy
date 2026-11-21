package com.hstudy.app.data.focus

import com.hstudy.app.i18n.CanonicalFocusText

/**
 * 番茄钟会话的**唯一权威状态源**（v3.3.1 D-05/07，指令 §4.2 设计口径）。
 *
 * 时间戳锚定：本类只存"段起点（epoch ms）+ 计划分钟数 + 累计暂停毫秒"，
 * 倒计时永远由 `deadline = sessionStart + plannedMinutes*60_000 (+ 暂停顺延) - now` 派生，
 * **绝不存"剩余秒数"**——内存态（remember）在按返回销毁页面或进程被杀时归零，
 * 墙钟时间戳才能跨导航、跨进程存活。
 *
 * 纯函数 + 时钟注入：所有判定/迁移都以 `nowMs` 为参，不读系统时钟——
 * 测试注入假时钟钉死公式（禁 sleep），组合层用 [com.hstudy.app.ui.dt.nowMillis]。
 *
 * 持久化在 `UserPreferences`（DataStore，照仓内 pomodoroTodayCount 先例逐键存），
 * 序列化/读取与 CAS 认领在同目录 [com.hstudy.app.data.preferences.UserPreferences]。
 */
enum class PomodoroSessionPhase { FOCUS, BREAK }

/** 恢复（进页面 / App 启动）时对持久化会话应做的动作（要求 2/3 的判定核心）。 */
enum class PomodoroRestoreAction {
    /** 仍在计时中（now < deadline）：保持运行态，进来继续倒数（不结算、不清除）。 */
    RESUME_TICKING,

    /** 专注段已到期且未结算：补结算（recordFocusSession + FOCUS bizKey checkIn）。 */
    SETTLE_FOCUS,

    /**
     * 专注段已结算（CAS 认领已赢）但会话未清——认领进程在"认领→入账"窗口内被杀。
     * 沿用其认领结果续跑可入账步骤（record/checkIn 都携带已落盘的 sessionId/bizKey），
     * 不再触发认领内的统计奖励。
     */
    RESUME_DEPOSIT,

    /** 休息段已过期：静默清除（休息不产生任何结算）。 */
    CLEAR_EXPIRED_BREAK,
}

data class PomodoroSessionState(
    /** 本段起点（墙钟 epoch ms）。跨进程可比较——单调时钟进程重启即失效，故不用它锚定。 */
    val sessionStartEpochMs: Long,
    /** 本段计划分钟数（FOCUS=专注时长，BREAK=休息时长）。 */
    val plannedMinutes: Int,
    val phase: PomodoroSessionPhase,
    /** 规范专注目标（CanonicalFocusText 口径，进 recordFocusSession 的 taskTitle）。 */
    val focusTarget: String = CanonicalFocusText.TARGET_FREE_STUDY,
    /** 本段累计暂停毫秒（恢复时把 deadline 顺延这么多——暂停语义的钉死公式）。 */
    val pausedAccumMs: Long = 0L,
    /** 非 null=处于暂停中（本段暂停起点）。冻结 = deadline 随 now 同步顺延，剩余恒定。 */
    val pauseStartedAtEpochMs: Long? = null,
    /** FOCUS 段结算认领是否已赢（先置位、后入账，D-09 同款；BREAK 段恒 false）。 */
    val settled: Boolean = false,
    /** 结算已落的 focus_sessions 行 id（0=未落）。崩溃续跑时复用它构造**同一条** bizKey。 */
    val recordedSessionId: Long = 0L,
    /** 结算认领当日（yyyy-MM-dd，claim 时落盘；bizKey 日期段跨零点重放保持不变）。 */
    val settleDate: String = "",
) {

    val isPaused: Boolean get() = pauseStartedAtEpochMs != null

    /**
     * 到期时刻（epoch ms）。暂停中：暂停时长实时计入顺延，
     * 于是 `deriveRemainingMs = deadline(now) - now = start + planned*60_000 + pausedAccum - pauseStart`
     * 与 now 无关——暂停期间剩余**恒定**（冻结）；恢复时 pausedAccum 固化这段暂停，deadline 一次性顺延。
     */
    fun deadlineEpochMs(nowMs: Long): Long =
        sessionStartEpochMs + plannedMinutes * 60_000L + pausedAccumMs +
            (if (pauseStartedAtEpochMs != null) nowMs - pauseStartedAtEpochMs!! else 0L)

    /** 派生剩余毫秒（下限 0）。倒计时展示值只允许从这里来。 */
    fun deriveRemainingMs(nowMs: Long): Long = (deadlineEpochMs(nowMs) - nowMs).coerceAtLeast(0L)

    fun isExpired(nowMs: Long): Boolean = deriveRemainingMs(nowMs) <= 0L

    /** 暂停：冻结本段（已在暂停中/已结算时幂等返回自身）。 */
    fun paused(nowMs: Long): PomodoroSessionState =
        if (isPaused || settled) this else copy(pauseStartedAtEpochMs = nowMs)

    /** 恢复：累计暂停毫秒 +=（now - 暂停起点），deadline 相应顺延（未在暂停中时幂等）。 */
    fun resumed(nowMs: Long): PomodoroSessionState =
        if (!isPaused) this else copy(
            pausedAccumMs = pausedAccumMs + (nowMs - pauseStartedAtEpochMs!!),
            pauseStartedAtEpochMs = null,
        )

    /** 专注段结算完成 → 同一条会话记录无缝转入休息段（重新锚定 start、清零暂停）。 */
    fun focusCompletedToBreak(nowMs: Long, breakMinutes: Int): PomodoroSessionState =
        copy(
            sessionStartEpochMs = nowMs,
            plannedMinutes = breakMinutes,
            phase = PomodoroSessionPhase.BREAK,
            pausedAccumMs = 0L,
            pauseStartedAtEpochMs = null,
            settled = false,
            recordedSessionId = 0L,
            settleDate = "",
        )

    /** 恢复时该做什么（要求 2/3 的纯函数判定核，测试逐分支钉死）。 */
    fun restoreAction(nowMs: Long): PomodoroRestoreAction = when {
        phase == PomodoroSessionPhase.FOCUS && settled -> PomodoroRestoreAction.RESUME_DEPOSIT
        phase == PomodoroSessionPhase.FOCUS && isExpired(nowMs) -> PomodoroRestoreAction.SETTLE_FOCUS
        phase == PomodoroSessionPhase.FOCUS -> PomodoroRestoreAction.RESUME_TICKING
        phase == PomodoroSessionPhase.BREAK && isExpired(nowMs) -> PomodoroRestoreAction.CLEAR_EXPIRED_BREAK
        else -> PomodoroRestoreAction.RESUME_TICKING
    }

    companion object {
        fun startFocus(nowMs: Long, plannedMinutes: Int, focusTarget: String): PomodoroSessionState =
            PomodoroSessionState(
                sessionStartEpochMs = nowMs,
                plannedMinutes = plannedMinutes,
                phase = PomodoroSessionPhase.FOCUS,
                focusTarget = focusTarget,
            )

        fun startBreak(nowMs: Long, plannedMinutes: Int, focusTarget: String): PomodoroSessionState =
            PomodoroSessionState(
                sessionStartEpochMs = nowMs,
                plannedMinutes = plannedMinutes,
                phase = PomodoroSessionPhase.BREAK,
                focusTarget = focusTarget,
            )
    }
}

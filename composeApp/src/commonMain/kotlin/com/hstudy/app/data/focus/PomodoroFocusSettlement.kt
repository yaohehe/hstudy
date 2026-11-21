package com.hstudy.app.data.focus

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.i18n.CanonicalFocusText
import kotlinx.coroutines.flow.first

/** 一次专注段结算的产物（UI 用它弹 snackbar：目标 + 分值；埋点调用点不在此处，要求 6）。 */
class PomodoroSettlementResult(
    val minutes: Int,
    val focusTarget: String,
    val points: Int,
    val sessionId: Long,
)

/**
 * 番茄钟专注段结算（v3.3.1 D-05/07，指令 §4.2）。
 *
 * 原 UI 完成回调里的"recordFocusSession → sessionId → FOCUS bizKey → checkIn"整段抽为共用函数，
 * 页面内完成与启动补结算走**同一条**路径（要求 3）：
 *
 * 1. **先置位、后入账**（D-09 同款 CAS）：[UserPreferences.tryClaimPomodoroFocusSettle] 在一个
 *    DataStore.edit 事务内核对"FOCUS 段、未结算、同一起锚点、deadline 已过"，赢下认领才同时
 *    落 settled/结算日，并把"今日番茄数 +1、宠物心情 +3"一并放进同一段临界区——
 *    双触发（页面内 + 启动补结算 / 快速重进页面）恰好一次入账与一次统计（要求 4）。
 * 2. **bizKey 幂等可续跑**：recordFocusSession 返回的 sessionId 与结算日当场持久化，
 *    认领后崩溃的续跑（[PomodoroRestoreAction.RESUME_DEPOSIT]）用同一条
 *    `FOCUS:$sessionId:$date` 补 checkIn，绝不产生第二笔待审单。
 * 3. 积分不变量与 bizKey 格式 `FOCUS:$sessionId:$date` 一字不动（红线）；
 *    description 继续走 [CanonicalFocusText.recordDescription]（存储边界，Ruling 25）。
 * 4. 本文件零埋点调用（数据层红线）：POMODORO_COMPLETE 仍只在宿主页面的完成回调里打点。
 */
object PomodoroFocusSettlement {

    /**
     * 结算一个已到期的 FOCUS 段。
     *
     * @param nextSession UI 路径传入结算后立刻要写入的下一段（BREAK）；启动补结算传 null（清会话）。
     *                    写入带终审 M-1 CAS 锚（expectedStart=本段起锚点）：结算在飞期间新会话已起锚则放弃。
     * @return 结算产物；null = 认领被并发的另一触发者赢走（本次不入账、不写 nextSession）。
     */
    suspend fun settleFocusSession(
        userPreferences: UserPreferences,
        focusRepository: FocusRepository,
        pointRepository: PointRepository,
        session: PomodoroSessionState,
        nowMs: Long,
        nextSession: PomodoroSessionState? = null,
    ): PomodoroSettlementResult? {
        require(session.phase == PomodoroSessionPhase.FOCUS) { "只能结算专注段" }

        val claimed = if (session.settled) {
            // 崩溃续跑：上一进程已赢下认领（统计奖励已在其认领事务内完成），本次只续跑可幂等重放的两步。
            true
        } else {
            userPreferences.tryClaimPomodoroFocusSettle(session.sessionStartEpochMs, nowMs)
        }
        if (!claimed) return null

        val minutes = session.plannedMinutes
        val points = minutes / 5
        val sessionId = if (session.recordedSessionId != 0L) {
            session.recordedSessionId
        } else {
            val newId = focusRepository.recordFocusSession(minutes, session.focusTarget, 0)
            userPreferences.markPomodoroFocusRecordId(session.sessionStartEpochMs, newId)
            newId
        }
        val checkInDate = session.settleDate.ifBlank { PointRepository.getTodayString() }
        if (points > 0) {
            pointRepository.checkIn(
                bizKey = "FOCUS:$sessionId:$checkInDate",
                actionType = "FOCUS_COMPLETE",
                description = CanonicalFocusText.recordDescription(session.focusTarget, minutes),
                points = points,
                date = checkInDate,
            )
        }
        // 终审 M-1：末步整组写会话带 CAS 锚——认领到写回之间孩子"跳过呼吸立即起新番茄"时，
        // 现存会话起锚点已 ≠ 本段 sessionStartEpochMs，savePomodoroSession 放弃写入保新会话。
        userPreferences.savePomodoroSession(nextSession, expectedStartEpochMs = session.sessionStartEpochMs)
        return PomodoroSettlementResult(minutes, session.focusTarget, points, sessionId)
    }

    /**
     * 启动补结算（要求 3）：AppGraph bootstrap 时检查持久化会话——
     * FOCUS 到期未结算 → 走与页面完成同一条结算路径；已认领未入账完 → 续跑；
     * BREAK 过期 → 静默清；仍在计时 → 原样保留（进页面继续倒数）。
     */
    suspend fun recoverStaleSession(
        userPreferences: UserPreferences,
        focusRepository: FocusRepository,
        pointRepository: PointRepository,
        nowMs: Long,
    ) {
        val session = userPreferences.pomodoroSession.first() ?: return
        when (session.restoreAction(nowMs)) {
            PomodoroRestoreAction.SETTLE_FOCUS,
            PomodoroRestoreAction.RESUME_DEPOSIT,
            -> settleFocusSession(userPreferences, focusRepository, pointRepository, session, nowMs)

            PomodoroRestoreAction.CLEAR_EXPIRED_BREAK ->
                // 终审 M-1 同款：清过期 BREAK 也带本段起锚 CAS——bootstrap Room 首查期间孩子已起
                // 新会话时，静默清会误删新锚，放弃并告警。
                userPreferences.savePomodoroSession(null, expectedStartEpochMs = session.sessionStartEpochMs)

            PomodoroRestoreAction.RESUME_TICKING -> Unit
        }
    }
}

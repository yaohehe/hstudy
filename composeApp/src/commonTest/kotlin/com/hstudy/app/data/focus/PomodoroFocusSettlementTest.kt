package com.hstudy.app.data.focus

import com.hstudy.app.data.local.dao.DailyFocusMinutes
import com.hstudy.app.data.local.dao.FocusDao
import com.hstudy.app.data.local.entity.DailyPlanEntity
import com.hstudy.app.data.local.entity.FocusSessionEntity
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FakePointApprovalDao
import com.hstudy.app.data.repository.FakePointDao
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D-05/07（v3.3.1 指令 §4.2）跨进程补结算与幂等的存储层直证套件。
 *
 * 装置沿用 [com.hstudy.app.ui.child.UserPreferencesBonusClaimCasTest] 的 RA9 约定：
 * 真 [UserPreferences]（okio 文件版 DataStore，`Platform.init` 指到 build/ 下）+
 * 真 [PointRepository]/[FocusRepository] + 手写内存 Fake DAO；时钟全部显式传参（nowMs），禁 sleep。
 *
 * 钉死的事（要求 3/4）：
 * 1. FOCUS 到期 → 补结算恰好一笔：recordFocusSession + `FOCUS:$sessionId:$date` 待审单 +
 *    今日番茄数/宠物心情随认领一次性入账，会话清除；
 * 2. 双触发（顺序与并发）只入账一笔——CAS 败者返回 null，不产生第二条专注记录/待审单；
 * 3. now < deadline 的运行态会话原样保留（进页面继续倒数）；暂停中的段不被真实流逝时间误结算；
 * 4. BREAK 过期静默清除，零记录；
 * 5. 认领后崩溃的续跑（RESUME_DEPOSIT）复用已落盘 sessionId/settleDate 构造**同一条** bizKey，
 *    不重复 record、不重复认领统计。
 *
 * 每个用例开头 `reset()` 清会话键组，避免真 DataStore 单例文件下的用例串扰；
 * 番茄计数一律断"增量"而不是绝对值（同文件里当日计数跨用例累计）。
 */
class PomodoroFocusSettlementTest {

    private val min = 60_000L
    private val t0 = 1_700_000_000_000L

    private class Fixture {
        val prefs = UserPreferences()
        val focusDao = FakeFocusDao()
        val pointDao = FakePointDao()
        val approvalDao = FakePointApprovalDao()
        val focusRepo = FocusRepository(focusDao)
        val pointRepo = PointRepository(pointDao, approvalDao)
    }

    private fun newFixture() = Fixture().also { Platform.init(filesDir = "build/test-d05-prefs", cacheDir = "build/test-d05-prefs") }

    @Test
    fun expiredFocusSession_settlesExactlyOneEntryAndClears() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        val session = PomodoroSessionState.startFocus(t0, 25, "自由学习")
        fx.prefs.savePomodoroSession(session)
        val countBefore = fx.prefs.pomodoroTodayCount.first()

        val result = PomodoroFocusSettlement.settleFocusSession(
            fx.prefs, fx.focusRepo, fx.pointRepo, session, nowMs = t0 + 26 * min
        )

        assertNotNull(result)
        assertEquals(25, result.minutes)
        assertEquals(5, result.points)
        assertEquals(1L, result.sessionId)
        // 专注记录：时长/目标/日期与原 UI 路径同形
        val recorded = fx.focusDao.sessions.single()
        assertEquals(25, recorded.durationMinutes)
        assertEquals("自由学习", recorded.taskTitle)
        // 待审单：bizKey 格式 FOCUS:$sessionId:$date 一字不动（红线），走 checkIn 权威入口
        val approval = fx.approvalDao.approvals.single()
        assertEquals("FOCUS:1:${PointRepository.getTodayString()}", approval.bizKey)
        assertEquals("FOCUS_COMPLETE", approval.actionType)
        assertEquals(5, approval.requestedPoints)
        // 统计奖励随认领恰好一次
        assertEquals(countBefore + 1, fx.prefs.pomodoroTodayCount.first())
        // settled 后清会话
        assertNull(fx.prefs.pomodoroSession.first())
    }

    @Test
    fun doubleTrigger_sequentialRecover_onlyOneEntry() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        fx.prefs.savePomodoroSession(PomodoroSessionState.startFocus(t0, 25, "自由学习"))
        val countBefore = fx.prefs.pomodoroTodayCount.first()

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 30 * min)
        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 30 * min)

        assertEquals(1, fx.focusDao.sessions.size, "双触发只允许一条专注记录")
        assertEquals(1, fx.approvalDao.approvals.size, "双触发只允许一笔待审入账")
        assertEquals(countBefore + 1, fx.prefs.pomodoroTodayCount.first(), "今日番茄数只 +1")
        assertNull(fx.prefs.pomodoroSession.first())
    }

    @Test
    fun doubleTrigger_sameStaleSnapshot_claimLoserReturnsNull() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        val session = PomodoroSessionState.startFocus(t0, 25, "自由学习")
        fx.prefs.savePomodoroSession(session)

        val first = PomodoroFocusSettlement.settleFocusSession(fx.prefs, fx.focusRepo, fx.pointRepo, session, t0 + 26 * min)
        val second = PomodoroFocusSettlement.settleFocusSession(fx.prefs, fx.focusRepo, fx.pointRepo, session, t0 + 26 * min)

        assertNotNull(first)
        assertNull(second, "CAS 败者必须空手而归（不入账、不写 nextSession）")
        assertEquals(1, fx.approvalDao.approvals.size)
    }

    @Test
    fun doubleTrigger_concurrentRecover_exactlyOneWins() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        fx.prefs.savePomodoroSession(PomodoroSessionState.startFocus(t0, 25, "自由学习"))
        val countBefore = fx.prefs.pomodoroTodayCount.first()

        val a = async { PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 30 * min) }
        val b = async { PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 30 * min) }
        a.await(); b.await()

        assertEquals(1, fx.focusDao.sessions.size)
        assertEquals(1, fx.approvalDao.approvals.size)
        assertEquals(countBefore + 1, fx.prefs.pomodoroTodayCount.first())
    }

    @Test
    fun recover_nowBeforeDeadline_keepsSessionRunning() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        fx.prefs.savePomodoroSession(PomodoroSessionState.startFocus(t0, 25, "自由学习"))

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 10 * min)

        val stored = fx.prefs.pomodoroSession.first()
        assertNotNull(stored, "运行态会话原样保留（页面进来继续倒数）")
        assertEquals(t0, stored.sessionStartEpochMs)
        assertEquals(0, fx.focusDao.sessions.size)
        assertEquals(0, fx.approvalDao.approvals.size)
    }

    @Test
    fun recover_pausedSession_notFalselyExpiredByWallClock() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        // 10 分钟时暂停：剩余冻结 15 分钟，真实时间流过 90 分钟也**不**到期
        fx.prefs.savePomodoroSession(PomodoroSessionState.startFocus(t0, 25, "自由学习").paused(t0 + 10 * min))

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 90 * min)

        val stored = fx.prefs.pomodoroSession.first()
        assertNotNull(stored, "暂停段未被误结算")
        assertEquals(t0 + 10 * min, stored.pauseStartedAtEpochMs)
        assertEquals(0, fx.approvalDao.approvals.size)
    }

    @Test
    fun recover_expiredBreak_clearsSilently() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        fx.prefs.savePomodoroSession(PomodoroSessionState.startBreak(t0, 5, "自由学习"))
        val countBefore = fx.prefs.pomodoroTodayCount.first()

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 6 * min)

        assertNull(fx.prefs.pomodoroSession.first(), "过期 BREAK 直接静默清")
        assertEquals(0, fx.focusDao.sessions.size, "休息不产生任何结算")
        assertEquals(0, fx.approvalDao.approvals.size)
        assertEquals(countBefore, fx.prefs.pomodoroTodayCount.first())
    }

    @Test
    fun crashAfterClaim_resumeDeposit_recordsWithSavedDate_andNoBonusRepeat() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        // 上一进程赢下认领（settled+settleDate 已落盘）但在 record 前被杀
        fx.prefs.savePomodoroSession(
            PomodoroSessionState.startFocus(t0, 25, "自由学习").copy(settled = true, settleDate = "2099-01-01")
        )
        val countBefore = fx.prefs.pomodoroTodayCount.first()

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 100 * min)

        assertEquals(1, fx.focusDao.sessions.size, "续跑补上 recordFocusSession")
        val approval = fx.approvalDao.approvals.single()
        assertEquals("FOCUS:1:2099-01-01", approval.bizKey, "bizKey 用认领时落盘的结算日，重放不变形")
        assertEquals(countBefore, fx.prefs.pomodoroTodayCount.first(), "统计奖励只属于认领事务，续跑不重复")
        assertNull(fx.prefs.pomodoroSession.first())
    }

    /**
     * 终审 M-1：结算在飞时新会话起锚，新锚必须存活。
     *
     * 竞态形状（AppGraph bootstrap 冷启动补结算：claim CAS 后到末步写回之间隔着 Room 首查/
     * checkIn 数百 ms，孩子"跳过呼吸立即起新番茄"；RESUME_DEPOSIT 路径窗口更宽）：
     * 用 [FakeFocusDao.onBeforeInsertSession] 钩子在结算执行的第一个挂起点（recordFocusSession
     * 落库前）写入**新起锚会话**，模拟覆盖窗口；末步 `savePomodoroSession(null, expectedStart=旧锚)`
     * 必须 CAS 放弃——旧段入账照常一笔，新会话原样存活，不再被静默清空。
     */
    @Test
    fun settlementInFlight_newSessionAnchoredMidway_newAnchorSurvives() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        val oldSession = PomodoroSessionState.startFocus(t0, 25, "自由学习")
        fx.prefs.savePomodoroSession(oldSession)
        val newStart = t0 + 26 * min
        // 结算在飞窗口：认领已赢、入账未完——孩子起新番茄（新锚落库，走 UI 同款无锚写入）
        fx.focusDao.onBeforeInsertSession = {
            fx.prefs.savePomodoroSession(PomodoroSessionState.startFocus(newStart, 25, "自由学习"))
        }

        val result = PomodoroFocusSettlement.settleFocusSession(
            fx.prefs, fx.focusRepo, fx.pointRepo, oldSession, nowMs = newStart
        )

        assertNotNull(result, "结算方入账侧照常完成（sessionId/bizKey 幂等），返回产物")
        val stored = fx.prefs.pomodoroSession.first()
        assertNotNull(stored, "新会话锚必须存活——结算末步不得无条件覆盖（M-1）")
        assertEquals(newStart, stored.sessionStartEpochMs)
        assertEquals(1, fx.approvalDao.approvals.size, "旧段仍只入账一笔待审")
        assertEquals("FOCUS:1:${PointRepository.getTodayString()}", fx.approvalDao.approvals.single().bizKey)
    }

    @Test
    fun crashAfterRecordBeforeCheckIn_resumeReusesSameSessionIdAndBizKey() = runTest {
        val fx = newFixture()
        fx.prefs.savePomodoroSession(null)
        // record 已落（sessionId=7 已持久化）、checkIn 前被杀：重放不得产生第二条专注记录，
        // bizKey 与原路**同一条**（要求 4"补结算路径必须同 bizKey"）。
        fx.prefs.savePomodoroSession(
            PomodoroSessionState
                .startFocus(t0, 25, "自由学习")
                .copy(settled = true, settleDate = "2099-01-02", recordedSessionId = 7L)
        )

        PomodoroFocusSettlement.recoverStaleSession(fx.prefs, fx.focusRepo, fx.pointRepo, t0 + 100 * min)

        assertEquals(0, fx.focusDao.sessions.size, "已落盘的 record 不重插")
        assertEquals("FOCUS:7:2099-01-02", fx.approvalDao.approvals.single().bizKey)
        assertNull(fx.prefs.pomodoroSession.first())
    }

    /** [FocusDao] 的内存 Fake（RA9，形态同 [com.hstudy.app.data.repository.FakePointDao]）。 */
    private class FakeFocusDao : FocusDao {
        val sessions = mutableListOf<FocusSessionEntity>()
        val plans = mutableListOf<DailyPlanEntity>()
        private var nextSessionId = 1L
        private var nextPlanId = 1L

        /** 终审 M-1 竞态注入钩子：insertSession 落库前执行（模拟结算在飞窗口内的并发写）。 */
        var onBeforeInsertSession: (suspend () -> Unit)? = null

        override suspend fun insertSession(session: FocusSessionEntity): Long {
            onBeforeInsertSession?.invoke()
            val id = nextSessionId++
            sessions.add(session.copy(id = id))
            return id
        }

        override fun getAllSessions(): Flow<List<FocusSessionEntity>> = MutableStateFlow(sessions.toList())

        override fun getSessionsByDate(date: String): Flow<List<FocusSessionEntity>> =
            MutableStateFlow(sessions.filter { it.date == date })

        override fun getTotalFocusMinutesForDate(date: String): Flow<Int> =
            MutableStateFlow(sessions.filter { it.date == date }.sumOf { it.durationMinutes })

        override fun getTotalFocusMinutes(): Flow<Int> = MutableStateFlow(sessions.sumOf { it.durationMinutes })

        override fun getSessionsSince(since: Long): Flow<List<FocusSessionEntity>> =
            MutableStateFlow(sessions.filter { it.completedAt >= since })

        override fun getDailyFocusMinutesSince(since: Long): Flow<List<DailyFocusMinutes>> =
            MutableStateFlow(
                sessions.filter { it.completedAt >= since }
                    .groupBy { it.date }
                    .map { (date, list) -> DailyFocusMinutes(date, list.sumOf { it.durationMinutes }) }
            )

        override suspend fun insertPlan(plan: DailyPlanEntity): Long {
            val id = nextPlanId++
            plans.add(plan.copy(id = id))
            return id
        }

        override suspend fun updatePlan(plan: DailyPlanEntity) {
            val index = plans.indexOfFirst { it.id == plan.id }
            if (index >= 0) plans[index] = plan
        }

        override suspend fun deletePlan(id: Long) {
            plans.removeAll { it.id == id }
        }

        override fun getPlansByDate(date: String): Flow<List<DailyPlanEntity>> =
            MutableStateFlow(plans.filter { it.date == date }.sortedBy { it.startHour * 60 + it.startMinute })

        override suspend fun setPlanCompleted(id: Long, completed: Boolean) {
            val index = plans.indexOfFirst { it.id == id }
            if (index >= 0) plans[index] = plans[index].copy(isCompleted = completed)
        }
    }
}

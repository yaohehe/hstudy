package com.hstudy.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.PointApprovalEntity
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.time.FixedAppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Executable split-store reconciliation coverage without requiring an Android device. */
class DailyTargetRepositoryReconciliationTest {

    @Test
    fun repeatedDoneRepairsTargetApprovalCreatedBeforeDoneWriteFailed(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        fixture.store.failWhen = { prefs -> prefs.doneTargets.contains(TARGET) }

        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isFailure)
        assertEquals(listOf(TARGET_KEY), fixture.approvals.approvals.map { it.bizKey })
        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())
        assertNull(fixture.preferences.dailyTargetBonusDate.first())

        fixture.store.failWhen = { false }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)

        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())
        assertEquals(setOf(TARGET_KEY, BONUS_KEY), fixture.approvals.approvals.map { it.bizKey }.toSet())
        assertEquals(2, fixture.approvals.approvals.size)
    }

    @Test
    fun repeatedDoneRepairsMissingTargetApprovalAfterRoomFailure(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        fixture.approvalGate.failInsertBizKey = TARGET_KEY

        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isFailure)
        assertTrue(fixture.approvals.approvals.isEmpty())
        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())

        fixture.approvalGate.failInsertBizKey = null
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)

        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())
        assertEquals(setOf(TARGET_KEY, BONUS_KEY), fixture.approvals.approvals.map { it.bizKey }.toSet())
    }

    @Test
    fun repeatedDoneRepairsBonusApprovalAfterDoneWasAlreadyPersisted(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        fixture.approvalGate.failInsertBizKey = BONUS_KEY

        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isFailure)
        assertEquals(listOf(TARGET_KEY), fixture.approvals.approvals.map { it.bizKey })
        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertNull(fixture.preferences.dailyTargetBonusDate.first())

        fixture.approvalGate.failInsertBizKey = null
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)

        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())
        assertEquals(setOf(TARGET_KEY, BONUS_KEY), fixture.approvals.approvals.map { it.bizKey }.toSet())
        assertEquals(2, fixture.approvals.approvals.size)
    }

    @Test
    fun repeatedDoneRepairsBonusClaimAfterApprovalWasAlreadyCreated(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        fixture.store.failWhen = { it[UserPreferences.DAILY_TARGET_BONUS_DATE] == DATE }

        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isFailure)
        assertEquals(setOf(TARGET_KEY, BONUS_KEY), fixture.approvals.approvals.map { it.bizKey }.toSet())
        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertNull(fixture.preferences.dailyTargetBonusDate.first())

        fixture.store.failWhen = { false }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)

        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())
        assertEquals(2, fixture.approvals.approvals.size)
    }

    @Test
    fun repeatedUndoRepairsDoneWriteAfterTargetUndoAlreadySucceeded(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        fixture.store.failWhen = { TARGET !in it.doneTargets }

        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isFailure)
        assertEquals(ApprovalStatus.CANCELLED, fixture.approvals.approval(TARGET_KEY).status)
        assertEquals(ApprovalStatus.PENDING, fixture.approvals.approval(BONUS_KEY).status)
        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())

        fixture.store.failWhen = { false }
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)

        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())
        assertEquals("", fixture.preferences.dailyTargetBonusDate.first())
        assertTrue(fixture.approvals.approvals.all { it.status == ApprovalStatus.CANCELLED })
        assertEquals(2, fixture.approvals.approvals.size)
    }

    @Test
    fun repeatedUndoRepairsRoomTargetUndoFailureBeforeDoneWrite(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        fixture.approvalGate.failCancelId = fixture.approvals.approval(TARGET_KEY).id

        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isFailure)
        assertEquals(ApprovalStatus.PENDING, fixture.approvals.approval(TARGET_KEY).status)
        assertEquals(setOf(TARGET), fixture.preferences.dailyTargetsDone.first())
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())

        fixture.approvalGate.failCancelId = null
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)

        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())
        assertEquals("", fixture.preferences.dailyTargetBonusDate.first())
        assertTrue(fixture.approvals.approvals.all { it.status == ApprovalStatus.CANCELLED })
    }

    @Test
    fun repeatedUndoRepairsBonusUndoAfterDoneWasAlreadyPersisted(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        fixture.approvalGate.failCancelId = fixture.approvals.approval(BONUS_KEY).id

        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isFailure)
        assertEquals(ApprovalStatus.CANCELLED, fixture.approvals.approval(TARGET_KEY).status)
        assertEquals(ApprovalStatus.PENDING, fixture.approvals.approval(BONUS_KEY).status)
        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())

        fixture.approvalGate.failCancelId = null
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)

        assertEquals("", fixture.preferences.dailyTargetBonusDate.first())
        assertTrue(fixture.approvals.approvals.all { it.status == ApprovalStatus.CANCELLED })
        assertEquals(2, fixture.approvals.approvals.size)
    }

    @Test
    fun repeatedUndoRepairsClaimClearAfterBonusUndoAlreadySucceeded(): Unit = runBlocking {
        val fixture = Fixture().also { it.initialize() }
        assertTrue(fixture.repository.setDone(DATE, TARGET, true).isSuccess)
        fixture.store.failWhen = { it[UserPreferences.DAILY_TARGET_BONUS_DATE] == "" }

        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isFailure)
        assertTrue(fixture.preferences.dailyTargetsDone.first().isEmpty())
        assertTrue(fixture.approvals.approvals.all { it.status == ApprovalStatus.CANCELLED })
        assertEquals(DATE, fixture.preferences.dailyTargetBonusDate.first())

        fixture.store.failWhen = { false }
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)
        assertTrue(fixture.repository.setDone(DATE, TARGET, false).isSuccess)

        assertEquals("", fixture.preferences.dailyTargetBonusDate.first())
        assertEquals(2, fixture.approvals.approvals.size)
    }

    private class Fixture {
        val store = FaultInjectingDataStore()
        val preferences = UserPreferences(store)
        val approvals = FakePointApprovalDao()
        val approvalGate = ApprovalFailureGate(approvals)
        val points = PointRepository(FakePointDao(), approvalGate)
        val repository = DailyTargetRepository(preferences, points, CLOCK)

        suspend fun initialize() {
            preferences.setDailyTargets(DATE, setOf(TARGET))
            check(preferences.dailyTargetBonusDate.first() == null)
        }
    }

    private class ApprovalFailureGate(
        private val delegate: PointApprovalDao,
    ) : PointApprovalDao by delegate {
        var failInsertBizKey: String? = null
        var failCancelId: Long? = null

        override suspend fun insert(approval: PointApprovalEntity): Long {
            if (approval.bizKey == failInsertBizKey) error("injected approval insert failure")
            return delegate.insert(approval)
        }

        override suspend fun cancelPending(id: Long, reviewedAt: Long): Int {
            if (id == failCancelId) error("injected approval cancellation failure")
            return delegate.cancelPending(id, reviewedAt)
        }
    }

    private class FaultInjectingDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val updateMutex = Mutex()
        override val data: Flow<Preferences> = state
        var failWhen: (Preferences) -> Boolean = { false }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            updateMutex.withLock {
                val updated = transform(state.value)
                check(!failWhen(updated)) { "injected DataStore write failure" }
                state.value = updated
                updated
            }
    }

    private companion object {
        const val DATE = "2026-11-25"
        const val TARGET = "homework"
        const val TARGET_KEY = "TARGET:$TARGET:$DATE"
        const val BONUS_KEY = "TARGET_ALL:$DATE"
        val CLOCK = FixedAppClock(
            LocalDate(2026, 11, 25).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
    }
}

private val Preferences.doneTargets: Set<String>
    get() = this[UserPreferences.DAILY_TARGETS_DONE]
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

private fun FakePointApprovalDao.approval(bizKey: String): PointApprovalEntity =
    approvals.single { it.bizKey == bizKey }

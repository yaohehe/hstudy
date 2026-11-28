package com.hstudy.app.data.focus

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.preferences.PomodoroWriteExpectation
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.data.repository.FocusRepository
import com.hstudy.app.data.repository.PointRepository
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDataStore
import com.hstudy.app.testutil.testDatabase
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroStaleSnapshotTest {

    @Test
    fun settledSessionRejectsStalePauseAndResumeSnapshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "pomodoro-stale-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val start = 1_700_000_000_000L
            val original = PomodoroSessionState.startFocus(start, 25, "自由学习")
            assertTrue(preferences.savePomodoroSession(original))
            val stale = preferences.pomodoroSession.first()!!
            val focusRepository = FocusRepository(db.focusDao())
            val pointRepository = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val now = start + 26 * 60_000L

            val result = PomodoroFocusSettlement.settleFocusSession(
                userPreferences = preferences,
                focusRepository = focusRepository,
                pointRepository = pointRepository,
                session = stale,
                nowMs = now,
                // Simulate a crash after the explicit settlement write but before the UI can clear it.
                nextSession = stale.copy(settled = true, settleDate = PointRepository.getTodayString()),
            )
            assertNotNull(result)
            val settled = preferences.pomodoroSession.first()!!
            assertTrue(settled.settled)
            assertTrue("settlement must advance the persisted version", settled.version > stale.version)

            val expectation = PomodoroWriteExpectation(
                startEpochMs = stale.sessionStartEpochMs,
                settled = stale.settled,
                version = stale.version,
            )
            assertFalse(
                preferences.savePomodoroSession(
                    stale.paused(start + 60_000L),
                    expectation,
                )
            )
            assertFalse(
                preferences.savePomodoroSession(
                    stale.copy(pauseStartedAtEpochMs = start + 60_000L).resumed(start + 120_000L),
                    expectation,
                )
            )
            assertFalse("unconditional UI writes cannot resurrect a settled session", preferences.savePomodoroSession(stale.paused(now)))

            val stored = preferences.pomodoroSession.first()!!
            assertTrue(stored.settled)
            assertEquals(settled.version, stored.version)
            assertEquals(start, stored.sessionStartEpochMs)
            assertEquals(1, focusRepository.getAllSessions().first().size)
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun concurrentResumeDepositWithSameSettledSnapshotCreatesOneFocusRowAndApproval() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "pomodoro-resume-deposit-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val start = 1_700_000_000_000L
            val running = PomodoroSessionState.startFocus(start, 25, "自由学习")
            assertTrue(preferences.savePomodoroSession(running))
            val settled = running.copy(settled = true, settleDate = "2099-01-01")
            assertTrue(
                preferences.savePomodoroSession(
                    settled,
                    PomodoroWriteExpectation(start, false, running.version),
                )
            )
            val snapshot = preferences.pomodoroSession.first()!!
            assertEquals(0L, snapshot.recordedSessionId)
            val focusRepository = FocusRepository(db.focusDao())
            val pointRepository = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))

            val results = coroutineScope {
                listOf(
                    async {
                        PomodoroFocusSettlement.settleFocusSession(
                            preferences, focusRepository, pointRepository, snapshot, start + 26 * 60_000L,
                        )
                    },
                    async {
                        PomodoroFocusSettlement.settleFocusSession(
                            preferences, focusRepository, pointRepository, snapshot, start + 26 * 60_000L,
                        )
                    },
                ).awaitAll()
            }

            assertNotNull(results[0])
            assertNotNull(results[1])
            assertEquals(results[0]!!.sessionId, results[1]!!.sessionId)
            val focusRows = focusRepository.getAllSessions().first()
            assertEquals(1, focusRows.size)
            assertEquals(results[0]!!.sessionId, focusRows.single().id)
            assertEquals("POMODORO:$start", focusRows.single().sessionKey)
            val approvals = db.pointApprovalDao().getAllApprovals().first()
            assertEquals(1, approvals.size)
            assertEquals("FOCUS:${results[0]!!.sessionId}:2099-01-01", approvals.single().bizKey)
            assertNull(preferences.pomodoroSession.first())
        } finally {
            db.close()
            storeFile.delete()
        }
    }

    @Test
    fun pauseAndResumeRequireMatchingVersionAndAdvanceIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "pomodoro-version-${UUID.randomUUID()}"
        val storeFile = context.preferencesDataStoreFile(storeName)
        val db = testDatabase(context)
        try {
            val preferences = UserPreferences(testDataStore(context, storeName))
            val start = 1_700_000_000_000L
            assertTrue(preferences.savePomodoroSession(PomodoroSessionState.startFocus(start, 25, "自由学习")))
            val running = preferences.pomodoroSession.first()!!

            assertTrue(
                preferences.savePomodoroSession(
                    running.paused(start + 1_000L),
                    PomodoroWriteExpectation(start, false, running.version),
                )
            )
            val paused = preferences.pomodoroSession.first()!!
            assertEquals(start + 1_000L, paused.pauseStartedAtEpochMs)
            assertEquals(running.version + 1L, paused.version)

            assertFalse(
                preferences.savePomodoroSession(
                    running.resumed(start + 2_000L),
                    PomodoroWriteExpectation(start, false, running.version),
                )
            )
            assertTrue(
                preferences.savePomodoroSession(
                    paused.resumed(start + 3_000L),
                    PomodoroWriteExpectation(start, false, paused.version),
                )
            )
            val resumed = preferences.pomodoroSession.first()!!
            assertEquals(null, resumed.pauseStartedAtEpochMs)
            assertEquals(2_000L, resumed.pausedAccumMs)
            assertEquals(paused.version + 1L, resumed.version)
        } finally {
            db.close()
            storeFile.delete()
        }
    }
}

package com.hstudy.app.data.focus

import com.hstudy.app.data.preferences.MemoryTrainingState
import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryTrainingStateTest {

    @Test
    fun showExpiryOnlyTransitionsTheMatchingShowingRound() {
        val showing = MemoryTrainingState(
            phase = MemoryTrainingState.Phase.SHOWING,
            level = 4,
            sequence = "1234",
            showUntilEpochMs = 100L,
        )

        assertEquals(showing.copy(phase = MemoryTrainingState.Phase.INPUT), showing.afterShow("1234", 100L))
        assertEquals(showing, showing.afterShow("9999", 100L))
        val newerRound = showing.copy(showUntilEpochMs = 200L)
        assertEquals(newerRound, newerRound.afterShow("1234", 100L))
    }

    @Test
    fun startRoundOnlyTransitionsTheExpectedPhaseAndSequence() {
        val result = MemoryTrainingState(
            phase = MemoryTrainingState.Phase.RESULT,
            level = 4,
            sequence = "1234",
            bestLevel = 4,
            lastCorrect = true,
        )
        val started = result.startRound(
            sequence = "5678",
            level = 5,
            showUntilEpochMs = 300L,
            expectedPhase = MemoryTrainingState.Phase.RESULT,
            expectedSequence = "1234",
        )

        assertEquals(
            MemoryTrainingState(
                phase = MemoryTrainingState.Phase.SHOWING,
                level = 5,
                sequence = "5678",
                bestLevel = 4,
                lastCorrect = false,
                showUntilEpochMs = 300L,
            ),
            started,
        )
        assertEquals(
            started,
            started.startRound("9999", 6, 400L, MemoryTrainingState.Phase.RESULT, "1234"),
        )
        val changedIdle = MemoryTrainingState(sequence = "9876")
        assertEquals(
            changedIdle,
            changedIdle.startRound("5555", 4, 500L, MemoryTrainingState.Phase.IDLE, ""),
        )
    }

    @Test
    fun dataStoreTransformsAreSerializedAndRejectStaleScreenTransitions() = runTest {
        Platform.init(filesDir = "build/test-memory-prefs", cacheDir = "build/test-memory-prefs")
        val preferences = UserPreferences()
        preferences.saveMemoryTrainingState(MemoryTrainingState())

        val changed = coroutineScope {
            (1..20).map {
                async {
                    preferences.updateMemoryTrainingState { current ->
                        current.copy(bestLevel = current.bestLevel + 1)
                    }
                }
            }.awaitAll()
        }
        assertEquals(List(20) { true }, changed)
        assertEquals(20, preferences.memoryTrainingState.first()!!.bestLevel)

        assertTrue(
            preferences.updateMemoryTrainingState { current ->
                current.startRound("1234", 4, 100L, MemoryTrainingState.Phase.IDLE, "")
            }
        )
        assertTrue(
            preferences.updateMemoryTrainingState { current -> current.afterShow("1234", 100L) }
        )
        assertFalse(
            preferences.updateMemoryTrainingState { current -> current.afterShow("1234", 100L) }
        )
        assertTrue(
            preferences.updateMemoryTrainingState { current -> current.answer("1234", "1234") }
        )
        assertFalse(
            preferences.updateMemoryTrainingState { current -> current.answer("1234", "1234") }
        )
        val result = preferences.memoryTrainingState.first()!!
        assertEquals(MemoryTrainingState.Phase.RESULT, result.phase)
        assertEquals(20, result.bestLevel)
        assertTrue(result.lastCorrect)
    }

    @Test
    fun answerOnlyTransitionsTheMatchingInputRoundOnce() {
        val input = MemoryTrainingState(
            phase = MemoryTrainingState.Phase.INPUT,
            level = 4,
            sequence = "1234",
            bestLevel = 3,
        )
        val correct = input.answer("1234", "1234")

        assertEquals(
            input.copy(
                phase = MemoryTrainingState.Phase.RESULT,
                bestLevel = 4,
                lastCorrect = true,
            ),
            correct,
        )
        assertEquals(correct, correct.answer("1234", "1234"))
        assertEquals(correct, correct.answer("1234", "9999"))
        assertEquals(
            input.copy(phase = MemoryTrainingState.Phase.RESULT, bestLevel = 3, lastCorrect = false),
            input.answer("9999", "1234"),
        )
    }
}

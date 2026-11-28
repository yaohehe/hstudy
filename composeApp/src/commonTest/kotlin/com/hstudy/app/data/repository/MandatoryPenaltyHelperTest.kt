package com.hstudy.app.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class MandatoryPenaltyHelperTest {

    @Test
    fun cappedPenaltyReturnsActualDeductionWhenPenaltyExceedsBalance() {
        assertEquals(10, MandatoryPenaltyHelper.cappedPenalty(balance = 10, penalty = 25))
    }

    @Test
    fun cappedPenaltyNeverReturnsNegativeDeduction() {
        assertEquals(0, MandatoryPenaltyHelper.cappedPenalty(balance = -5, penalty = 25))
        assertEquals(0, MandatoryPenaltyHelper.cappedPenalty(balance = 10, penalty = -3))
    }

    @Test
    fun cappedPenaltyKeepsAffordablePenaltyUnchanged() {
        assertEquals(5, MandatoryPenaltyHelper.cappedPenalty(balance = 10, penalty = 5))
        assertEquals(10, MandatoryPenaltyHelper.cappedPenalty(balance = 10, penalty = 10))
    }
}

package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.HabitDao
import com.hstudy.app.data.local.entity.AchievementEntity
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.HabitChallengeEntity
import com.hstudy.app.data.local.entity.ProofStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 回审 P1-1：习惯打卡撤销跨午夜对称性。
 * undoCheckInChallenge 锚定存储的 lastCheckInDate（今天/昨天窗口），不再依赖点击时刻的今天。
 * Fake DAO 只建模 updateChallenge，其余成员为不可达桩。
 */
class HabitRepositoryUndoTest {

    private class FakeHabitDao : HabitDao {
        var updated: HabitChallengeEntity? = null
        override suspend fun insertChallenge(challenge: HabitChallengeEntity): Long = error("unused")
        override suspend fun updateChallenge(challenge: HabitChallengeEntity) { updated = challenge }
        override suspend fun deleteChallenge(id: Long) = error("unused")
        override fun getActiveChallenges(): Flow<List<HabitChallengeEntity>> = flowOf(emptyList())
        override fun getAllChallenges(): Flow<List<HabitChallengeEntity>> = flowOf(emptyList())
        override suspend fun getChallengeById(id: Long): HabitChallengeEntity? = null
        override suspend fun incrementCompletedDays(id: Long) = error("unused")
        override suspend fun completeChallenge(id: Long) = error("unused")
        override fun getCompletedChallengeCount(): Flow<Int> = flowOf(0)
        override suspend fun insertAchievement(achievement: AchievementEntity): Long = error("unused")
        override fun getAllAchievements(): Flow<List<AchievementEntity>> = flowOf(emptyList())
        override suspend fun getAchievementByType(type: String): AchievementEntity? = null
        override fun getAchievementCount(): Flow<Int> = flowOf(0)
        override suspend fun insertProof(proof: CompletionProofEntity): Long = error("unused")
        override suspend fun updateProof(proof: CompletionProofEntity) = error("unused")
        override fun getPendingProofs(): Flow<List<CompletionProofEntity>> = flowOf(emptyList())
        override fun getAllProofs(): Flow<List<CompletionProofEntity>> = flowOf(emptyList())
        override suspend fun getProofForTaskOnDate(taskId: Long, date: String): CompletionProofEntity? = null
        override fun getPendingProofCount(): Flow<Int> = flowOf(0)
        override suspend fun reviewProof(id: Long, status: ProofStatus, reviewedAt: Long, points: Int) = error("unused")
    }

    private fun daysAgo(n: Int): String = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
        .minus(DatePeriod(days = n))
        .toString()

    private fun challengeCheckedInOn(date: String) = HabitChallengeEntity(
        id = 1, title = "t", startDate = daysAgo(3),
        completedDays = 3, lastCheckInDate = date
    )

    @Test
    fun undo_rollbacksCheckInMadeYesterday() = runTest {
        val dao = FakeHabitDao()
        HabitRepository(dao).undoCheckInChallenge(challengeCheckedInOn(daysAgo(1)))
        assertEquals(2, dao.updated?.completedDays)
        assertEquals("", dao.updated?.lastCheckInDate)
    }

    @Test
    fun undo_rollbacksCheckInMadeToday() = runTest {
        val dao = FakeHabitDao()
        HabitRepository(dao).undoCheckInChallenge(challengeCheckedInOn(daysAgo(0)))
        assertEquals(2, dao.updated?.completedDays)
    }

    @Test
    fun undo_rejectsCheckInOlderThanYesterday() = runTest {
        val dao = FakeHabitDao()
        HabitRepository(dao).undoCheckInChallenge(challengeCheckedInOn(daysAgo(2)))
        assertNull(dao.updated)
    }

    @Test
    fun undo_ignoresChallengeWithoutStoredDate() = runTest {
        val dao = FakeHabitDao()
        HabitRepository(dao).undoCheckInChallenge(challengeCheckedInOn(""))
        assertNull(dao.updated)
    }
}

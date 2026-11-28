package com.hstudy.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hstudy.app.data.local.dao.MistakeDao
import com.hstudy.app.data.local.dao.PointApprovalDao
import com.hstudy.app.data.local.dao.PointDao
import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.entity.ApprovalStatus
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.DailyAssignmentEntity
import com.hstudy.app.data.local.entity.HabitChallengeEntity
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.ProofStatus
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.roomTransactionRunner
import com.hstudy.app.testutil.testDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitMistakeRewardAtomicityTest {

    @Test
    fun concurrentProofApproveAndRejectOnlyTransitionsPendingOnce() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val proofId = db.habitDao().insertProof(CompletionProofEntity(taskId = 1L, photoPath = "proof.jpg"))
            val repo = ProofRepository(db.habitDao())

            val results = withTimeout(30_000) {
                coroutineScope {
                    val approve = async { repo.approveProof(proofId, 9) }
                    val reject = async { repo.rejectProof(proofId) }
                    listOf(approve.await(), reject.await()).sorted()
                }
            }

            assertEquals(listOf(0, 1), results)
            val proof = db.habitDao().getProofForTaskOnDate(1L, "")!!
            assertTrue(proof.status == ProofStatus.APPROVED || proof.status == ProofStatus.REJECTED)
            assertNotNull(proof.reviewedAt)
            if (proof.status == ProofStatus.APPROVED) assertEquals(9, proof.pointsAwarded)
        } finally {
            db.close()
        }
    }

    @Test
    fun staleHabitSnapshotCannotRegressNewerProgress() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val stale = HabitChallengeEntity(
                title = "阅读",
                startDate = "2026-01-01",
                totalDays = 10,
                completedDays = 0,
                lastCheckInDate = "2000-01-01"
            )
            val id = db.habitDao().insertChallenge(stale)
            val snapshot = stale.copy(id = id)
            val today = todayString()
            assertEquals(
                1,
                db.habitDao().incrementChallengeProgress(id, expectedCompletedDays = 0, newCompletedDays = 2, lastCheckInDate = today)
            )

            HabitRepository(db.habitDao()).checkInChallenge(snapshot)

            val stored = db.habitDao().getChallengeById(id)!!
            assertEquals(2, stored.completedDays)
            assertEquals(today, stored.lastCheckInDate)
        } finally {
            db.close()
        }
    }

    @Test
    fun staleHabitUndoCannotRegressNewerProgress() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val stale = HabitChallengeEntity(
                title = "撤销进度竞争",
                startDate = "2026-01-01",
                totalDays = 5,
                completedDays = 2,
                lastCheckInDate = todayString()
            )
            val id = db.habitDao().insertChallenge(stale)
            val snapshot = stale.copy(id = id)
            assertEquals(
                1,
                db.habitDao().incrementChallengeProgress(
                    id,
                    expectedCompletedDays = 2,
                    newCompletedDays = 3,
                    lastCheckInDate = todayString()
                )
            )
            val before = db.habitDao().getChallengeById(id)!!

            assertFalse(HabitRepository(db.habitDao()).undoCheckInChallenge(snapshot))

            val stored = db.habitDao().getChallengeById(id)!!
            assertEquals(before, stored)
            assertEquals(3, stored.completedDays)
            assertEquals(todayString(), stored.lastCheckInDate)
            assertTrue(stored.isActive)
            assertFalse(stored.isCompleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun staleHabitUndoCannotClearNewerCheckInDateOrCompletionFlags() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val current = HabitChallengeEntity(
                title = "撤销日期竞争",
                startDate = "2026-01-01",
                totalDays = 4,
                completedDays = 3,
                isActive = false,
                isCompleted = true,
                lastCheckInDate = todayString()
            )
            val id = db.habitDao().insertChallenge(current)
            val staleSnapshot = current.copy(
                id = id,
                isActive = true,
                isCompleted = false,
                lastCheckInDate = yesterdayString()
            )
            val before = db.habitDao().getChallengeById(id)!!

            assertFalse(HabitRepository(db.habitDao()).undoCheckInChallenge(staleSnapshot))

            val stored = db.habitDao().getChallengeById(id)!!
            assertEquals(before, stored)
            assertEquals(3, stored.completedDays)
            assertEquals(todayString(), stored.lastCheckInDate)
            assertFalse(stored.isActive)
            assertTrue(stored.isCompleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun habitUndoCasDecrementsAndRestoresIncompleteState() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val challenge = HabitChallengeEntity(
                title = "撤销完成日",
                startDate = "2026-01-01",
                totalDays = 3,
                completedDays = 3,
                isActive = false,
                isCompleted = true,
                lastCheckInDate = todayString()
            )
            val id = db.habitDao().insertChallenge(challenge)

            assertTrue(HabitRepository(db.habitDao()).undoCheckInChallenge(challenge.copy(id = id)))

            val stored = db.habitDao().getChallengeById(id)!!
            assertEquals(2, stored.completedDays)
            assertEquals("", stored.lastCheckInDate)
            assertTrue(stored.isActive)
            assertFalse(stored.isCompleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun finalHabitCheckInWritesProgressAndCompletionTogether() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val id = db.habitDao().insertChallenge(
                HabitChallengeEntity(
                    title = "完成挑战",
                    startDate = "2026-01-01",
                    totalDays = 21,
                    completedDays = 20,
                    lastCheckInDate = "2000-01-01"
                )
            )

            val result = HabitRepository(db.habitDao()).checkInChallenge(db.habitDao().getChallengeById(id)!!)

            assertEquals(HabitRepository.CheckInResult.CHALLENGE_COMPLETED, result)
            val stored = db.habitDao().getChallengeById(id)!!
            assertEquals(21, stored.completedDays)
            assertEquals(todayString(), stored.lastCheckInDate)
            assertFalse(stored.isActive)
            assertTrue(stored.isCompleted)
        } finally {
            db.close()
        }
    }

    @Test
    fun twentyConcurrentPerfectDayUnlocksCreateOneCanonicalAchievement() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val repo = HabitRepository(db.habitDao())
            val results = withTimeout(30_000) {
                coroutineScope {
                    (1..20).map {
                        async { repo.tryUnlockAchievement("perfect_day", "完美一天", "全部完成", "🌟") }
                    }.awaitAll()
                }
            }

            assertEquals(1, results.count { it })
            val achievements = db.habitDao().getAllAchievements().first()
            assertEquals(1, achievements.size)
            assertEquals("perfect_day", achievements.single().canonicalType)
        } finally {
            db.close()
        }
    }

    @Test
    fun mistakeReviewRollsBackAllThreeTablesAndRetryWritesOneOfEach() = runBlocking {
        for (failureStage in listOf("history", "approval")) {
            val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
            try {
                val mistakeId = db.mistakeDao().insert(
                    MistakeEntity(subject = MistakeSubject.MATH, title = "错题-$failureStage")
                )
                val runner = roomTransactionRunner(db)
                val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
                val failingDao = object : MistakeDao by db.mistakeDao() {
                    override suspend fun insertReview(review: com.hstudy.app.data.local.entity.MistakeReviewEntity): Long {
                        if (failureStage == "history") error("injected mistake history failure")
                        return db.mistakeDao().insertReview(review)
                    }
                }
                val failingApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                    override suspend fun insert(approval: com.hstudy.app.data.local.entity.PointApprovalEntity): Long =
                        error("injected mistake approval failure")
                }
                val failingPointRepo = PointRepository(db.pointDao(), failingApprovalDao, runner)
                val failingRepo = MistakeRepository(failingDao, failingPointRepo, runner)

                val failure = runCatching { failingRepo.reviewMistake(mistakeId, points = 3, note = "复习") }.exceptionOrNull()
                assertNotNull(failure)
                assertTrue(failure!!.message!!.startsWith("injected mistake"))

                assertEquals(0, db.mistakeDao().getMistakeById(mistakeId)!!.reviewCount)
                assertEquals(0, db.mistakeDao().getReviewsForMistake(mistakeId).first().size)
                assertEquals(0, db.pointApprovalDao().getAllApprovals().first().size)

                MistakeRepository(db.mistakeDao(), pointRepo, runner).reviewMistake(mistakeId, points = 3, note = "复习")

                assertEquals(1, db.mistakeDao().getMistakeById(mistakeId)!!.reviewCount)
                assertEquals(1, db.mistakeDao().getReviewsForMistake(mistakeId).first().size)
                assertEquals(1, db.pointApprovalDao().getAllApprovals().first().size)
                assertEquals(0, db.pointDao().getAllPointRecords().first().size)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun failedVariationApprovalRollsBackMistakeUpdate() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val mistakeId = db.mistakeDao().insert(
                MistakeEntity(subject = MistakeSubject.MATH, title = "举一反三回滚")
            )
            val mistake = db.mistakeDao().getMistakeById(mistakeId)!!
            val runner = roomTransactionRunner(db)
            val failingApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                override suspend fun insert(approval: com.hstudy.app.data.local.entity.PointApprovalEntity): Long =
                    error("injected variation approval failure")
            }
            val pointRepo = PointRepository(db.pointDao(), failingApprovalDao, runner)
            val repo = MistakeRepository(db.mistakeDao(), pointRepo, runner)

            val failure = runCatching { repo.saveVariation(mistake, "换一种解法") }.exceptionOrNull()

            assertEquals("injected variation approval failure", failure?.message)
            val stored = db.mistakeDao().getMistakeById(mistakeId)!!
            assertFalse(stored.hasVariation)
            assertEquals("", stored.variationNote)
            assertEquals(0, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedHabitApprovalRollsBackProgress() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val id = db.habitDao().insertChallenge(
                HabitChallengeEntity(
                    title = "习惯打卡回滚",
                    startDate = "2026-01-01",
                    totalDays = 10,
                    completedDays = 0,
                    lastCheckInDate = "2000-01-01",
                    dailyPoints = 5,
                )
            )
            val snapshot = db.habitDao().getChallengeById(id)!!
            val runner = roomTransactionRunner(db)
            val failingApprovalDao = object : PointApprovalDao by db.pointApprovalDao() {
                override suspend fun insert(approval: com.hstudy.app.data.local.entity.PointApprovalEntity): Long =
                    error("injected habit approval failure")
            }
            val pointRepo = PointRepository(db.pointDao(), failingApprovalDao, runner)
            val repo = HabitRepository(db.habitDao(), pointRepo, runner)

            val failure = runCatching { repo.checkInChallenge(snapshot) }.exceptionOrNull()

            assertEquals("injected habit approval failure", failure?.message)
            assertEquals(snapshot, db.habitDao().getChallengeById(id))
            assertEquals(0, db.pointApprovalDao().getAllApprovals().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedHabitReversalRollsBackUndoProgress() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val today = todayString()
            val id = db.habitDao().insertChallenge(
                HabitChallengeEntity(
                    title = "习惯撤销回滚",
                    startDate = "2026-01-01",
                    totalDays = 10,
                    completedDays = 1,
                    lastCheckInDate = today,
                    dailyPoints = 5,
                )
            )
            val snapshot = db.habitDao().getChallengeById(id)!!
            val runner = roomTransactionRunner(db)
            val setupPoints = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            setupPoints.checkIn("HABIT:$id:$today", "HABIT_CHECKIN", "习惯打卡", 5, date = today)
            val approvalId = setupPoints.getAllApprovals().first().single().id
            setupPoints.approveAndAward(approvalId)
            val failingPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long =
                    error("injected habit reversal failure")
            }
            val pointRepo = PointRepository(failingPointDao, db.pointApprovalDao(), runner)
            val repo = HabitRepository(db.habitDao(), pointRepo, runner)

            val failure = runCatching { repo.undoCheckInChallengeAndPoints(snapshot) }.exceptionOrNull()

            assertEquals("injected habit reversal failure", failure?.message)
            assertEquals(snapshot, db.habitDao().getChallengeById(id))
            assertEquals(ApprovalStatus.APPROVED, db.pointApprovalDao().getById(approvalId)?.status)
            assertEquals(listOf(5), db.pointDao().getAllPointRecords().first().map { it.points })
        } finally {
            db.close()
        }
    }

    @Test
    fun failedAssignmentReversalRollsBackCompletionReset() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val completedAt = Clock.System.now().toEpochMilliseconds()
            val id = db.dailyAssignmentDao().insert(
                DailyAssignmentEntity(
                    title = "布置任务撤销回滚",
                    points = 5,
                    assignedDate = todayString(),
                    isCompleted = true,
                    completedAt = completedAt,
                )
            )
            val snapshot = db.dailyAssignmentDao().getAssignmentById(id)!!
            val date = PointRepository.completionDateStringOf(completedAt)
            val runner = roomTransactionRunner(db)
            val setupPoints = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            setupPoints.checkIn("ASSIGN:$id:$date", "ASSIGNMENT", "布置任务", 5, date = date)
            val approvalId = setupPoints.getAllApprovals().first().single().id
            setupPoints.approveAndAward(approvalId)
            val failingPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long =
                    error("injected assignment reversal failure")
            }
            val pointRepo = PointRepository(failingPointDao, db.pointApprovalDao(), runner)
            val repo = AssignmentRepository(db.dailyAssignmentDao(), runner, pointRepo)

            val failure = runCatching { repo.uncompleteAssignment(id) }.exceptionOrNull()

            assertEquals("injected assignment reversal failure", failure?.message)
            assertEquals(snapshot, db.dailyAssignmentDao().getAssignmentById(id))
            assertEquals(ApprovalStatus.APPROVED, db.pointApprovalDao().getById(approvalId)?.status)
            assertEquals(listOf(5), db.pointDao().getAllPointRecords().first().map { it.points })
        } finally {
            db.close()
        }
    }

    @Test
    fun failedRewardDebitLeavesNoExchangeOrDebitRow() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val rewardId = db.rewardDao().insertReward(RewardEntity(name = "测试奖励", pointsCost = 10))
            fund(db, 20)
            val failingPointDao = object : PointDao by db.pointDao() {
                override suspend fun insertPointRecord(record: PointRecordEntity): Long =
                    error("injected reward debit failure")
            }
            val pointRepo = PointRepository(failingPointDao, db.pointApprovalDao(), roomTransactionRunner(db))
            val repo = RewardRepository(db.rewardDao(), pointRepo, roomTransactionRunner(db))

            val failure = runCatching { repo.exchangeReward(rewardId, 10, "测试奖励") }.exceptionOrNull()

            assertEquals("injected reward debit failure", failure?.message)
            assertEquals(0, db.rewardDao().getAllExchanges().first().size)
            assertEquals(20, pointRepo.getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun failedRewardExchangeInsertRollsBackDebit() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val rewardId = db.rewardDao().insertReward(RewardEntity(name = "测试奖励", pointsCost = 10))
            fund(db, 20)
            val failingRewardDao = object : RewardDao by db.rewardDao() {
                override suspend fun insertExchange(exchange: com.hstudy.app.data.local.entity.RewardExchangeEntity): Long =
                    error("injected reward exchange failure")
            }
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repo = RewardRepository(failingRewardDao, pointRepo, roomTransactionRunner(db))

            val failure = runCatching { repo.exchangeReward(rewardId, 10, "测试奖励") }.exceptionOrNull()

            assertEquals("injected reward exchange failure", failure?.message)
            assertEquals(0, db.rewardDao().getAllExchanges().first().size)
            assertEquals(20, pointRepo.getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun insufficientBalanceWritesNeitherExchangeNorDebit() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val rewardId = db.rewardDao().insertReward(RewardEntity(name = "测试奖励", pointsCost = 10))
            fund(db, 5)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), roomTransactionRunner(db))
            val repo = RewardRepository(db.rewardDao(), pointRepo, roomTransactionRunner(db))

            assertEquals(-1L, repo.exchangeReward(rewardId, 10, "测试奖励"))
            assertEquals(0, db.rewardDao().getAllExchanges().first().size)
            assertEquals(5, pointRepo.getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentRewardExchangesCannotOverdrawBalance() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val rewardId = db.rewardDao().insertReward(RewardEntity(name = "测试奖励", pointsCost = 10))
            fund(db, 10)
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val repo = RewardRepository(db.rewardDao(), pointRepo, runner)

            val results = withTimeout(30_000) {
                coroutineScope {
                    (1..2).map { async { repo.exchangeReward(rewardId, 10, "测试奖励") } }.awaitAll()
                }
            }

            assertEquals(1, results.count { it > 0 })
            assertEquals(1, results.count { it == -1L })
            assertEquals(1, db.rewardDao().getAllExchanges().first().size)
            assertEquals(0, pointRepo.getTotalPoints().first())
            assertEquals(1, db.pointDao().getAllPointRecords().first().count { it.points == -10 })
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentPresetInitializationKeepsOneCanonicalSetAndCustomReward() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val custom = RewardEntity(
                name = "自定义奖励",
                description = "用户创建",
                pointsCost = 77,
                iconEmoji = "🧩",
                category = "其他",
                sortOrder = 99,
                createdAt = 123L
            )
            val customId = db.rewardDao().insertReward(custom)
            val storedCustom = db.rewardDao().getRewardById(customId)!!
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val repo = RewardRepository(db.rewardDao(), pointRepo, runner)

            withTimeout(30_000) {
                coroutineScope {
                    val first = async { repo.initDefaultRewards() }
                    val second = async { repo.initDefaultRewards() }
                    listOf(first.await(), second.await())
                }
            }

            val rewards = db.rewardDao().getAllActiveRewards().first()
            val presets = rewards.filter { it.presetKey != null }
            assertEquals(27, presets.size)
            assertEquals(27, presets.map { it.presetKey }.toSet().size)
            assertTrue(presets.all { it.presetKey == it.name })
            assertEquals(storedCustom, db.rewardDao().getRewardById(customId))
            assertEquals(28, rewards.size)

            val firstPresetId = presets.first().id
            repo.initDefaultRewards()
            assertEquals(28, db.rewardDao().getAllActiveRewards().first().size)
            assertEquals(firstPresetId, db.rewardDao().getRewardById(firstPresetId)!!.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun reseedingPreservesEditedKeyedPresetRowAndIdentity() = runBlocking {
        val db = testDatabase(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val runner = roomTransactionRunner(db)
            val pointRepo = PointRepository(db.pointDao(), db.pointApprovalDao(), runner)
            val repo = RewardRepository(db.rewardDao(), pointRepo, runner)
            repo.initDefaultRewards()
            val original = db.rewardDao().getAllActiveRewards().first()
                .single { it.presetKey == "看一集动画片" }
            val edited = original.copy(
                name = "家长编辑奖励",
                description = "编辑后的说明",
                pointsCost = 91,
                iconEmoji = "✨",
                category = "自定义",
                sortOrder = 88
            )
            repo.updateReward(edited)
            assertEquals(edited, db.rewardDao().getRewardById(original.id))

            repo.ensureDefaultRewards()

            val stored = db.rewardDao().getRewardById(original.id)!!
            assertEquals(edited, stored)
            assertEquals(original.id, stored.id)
            assertEquals("看一集动画片", stored.presetKey)
            val rewards = db.rewardDao().getAllActiveRewards().first()
            assertEquals(27, rewards.size)
            assertEquals(27, rewards.map { it.presetKey }.toSet().size)
        } finally {
            db.close()
        }
    }

    private suspend fun fund(db: com.hstudy.app.data.local.AppDatabase, points: Int) {
        db.pointDao().insertPointRecord(
            PointRecordEntity(type = PointRecordType.ADJUSTMENT, points = points, note = "test funding")
        )
    }

    private fun todayString(): String = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

    private fun yesterdayString(): String = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .minus(1, DateTimeUnit.DAY)
        .toString()
}

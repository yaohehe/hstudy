package com.hstudy.app.data.local.dao

import androidx.room.*
import com.hstudy.app.data.local.entity.HabitChallengeEntity
import com.hstudy.app.data.local.entity.AchievementEntity
import com.hstudy.app.data.local.entity.CompletionProofEntity
import com.hstudy.app.data.local.entity.ProofStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    // ===== 习惯挑战 =====
    @Insert
    suspend fun insertChallenge(challenge: HabitChallengeEntity): Long

    @Update
    suspend fun updateChallenge(challenge: HabitChallengeEntity)

    @Query("DELETE FROM habit_challenges WHERE id = :id")
    suspend fun deleteChallenge(id: Long)

    @Query("SELECT * FROM habit_challenges WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveChallenges(): Flow<List<HabitChallengeEntity>>

    @Query("SELECT * FROM habit_challenges ORDER BY createdAt DESC")
    fun getAllChallenges(): Flow<List<HabitChallengeEntity>>

    @Query("SELECT * FROM habit_challenges WHERE id = :id")
    suspend fun getChallengeById(id: Long): HabitChallengeEntity?

    @Query("UPDATE habit_challenges SET completedDays = completedDays + 1 WHERE id = :id")
    suspend fun incrementCompletedDays(id: Long)

    /**
     * Field-level compare-and-set for a check-in. Completion flags change in the same statement,
     * and a stale snapshot cannot overwrite progress written by a newer check-in.
     */
    @Query(
        "UPDATE habit_challenges SET " +
            "completedDays = :newCompletedDays, lastCheckInDate = :lastCheckInDate, " +
            "isActive = CASE WHEN :newCompletedDays >= totalDays THEN 0 ELSE isActive END, " +
            "isCompleted = CASE WHEN :newCompletedDays >= totalDays THEN 1 ELSE isCompleted END " +
            "WHERE id = :id AND completedDays = :expectedCompletedDays"
    )
    suspend fun incrementChallengeProgress(
        id: Long,
        expectedCompletedDays: Int,
        newCompletedDays: Int,
        lastCheckInDate: String
    ): Int

    /**
     * Field-level compare-and-set for undoing the stored check-in date. A stale snapshot cannot
     * decrement newer progress or clear a newer date; completion flags change only when it wins.
     */
    @Query(
        "UPDATE habit_challenges SET " +
            "completedDays = CASE WHEN completedDays > 0 THEN completedDays - 1 ELSE 0 END, " +
            "lastCheckInDate = '', isActive = 1, isCompleted = 0 " +
            "WHERE id = :id AND completedDays = :expectedCompletedDays " +
            "AND lastCheckInDate = :expectedLastCheckInDate"
    )
    suspend fun undoChallengeCheckIn(
        id: Long,
        expectedCompletedDays: Int,
        expectedLastCheckInDate: String
    ): Int

    @Query("UPDATE habit_challenges SET isActive = 0, isCompleted = 1 WHERE id = :id")
    suspend fun completeChallenge(id: Long)

    @Query("SELECT COUNT(*) FROM habit_challenges WHERE isCompleted = 1")
    fun getCompletedChallengeCount(): Flow<Int>

    // ===== 成就 =====
    /** Canonical achievements are unique by canonicalType; historical unkeyed rows remain valid. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity): Long

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE type = :type LIMIT 1")
    suspend fun getAchievementByType(type: String): AchievementEntity?

    @Query("SELECT COUNT(*) FROM achievements")
    fun getAchievementCount(): Flow<Int>

    // ===== 完成证明 =====
    @Insert
    suspend fun insertProof(proof: CompletionProofEntity): Long

    @Update
    suspend fun updateProof(proof: CompletionProofEntity)

    @Query("SELECT * FROM completion_proofs WHERE status = 'PENDING' ORDER BY submittedAt DESC")
    fun getPendingProofs(): Flow<List<CompletionProofEntity>>

    @Query("SELECT * FROM completion_proofs ORDER BY submittedAt DESC")
    fun getAllProofs(): Flow<List<CompletionProofEntity>>

    @Query("SELECT * FROM completion_proofs WHERE taskId = :taskId AND date = :date ORDER BY submittedAt DESC LIMIT 1")
    suspend fun getProofForTaskOnDate(taskId: Long, date: String): CompletionProofEntity?

    @Query("SELECT COUNT(*) FROM completion_proofs WHERE status = 'PENDING'")
    fun getPendingProofCount(): Flow<Int>

    /** Only a PENDING proof may reach a terminal state; returns the CAS affected-row count. */
    @Query(
        "UPDATE completion_proofs SET status = :status, reviewedAt = :reviewedAt, pointsAwarded = :points " +
            "WHERE id = :id AND status = 'PENDING'"
    )
    suspend fun reviewProof(id: Long, status: ProofStatus, reviewedAt: Long, points: Int = 0): Int
}

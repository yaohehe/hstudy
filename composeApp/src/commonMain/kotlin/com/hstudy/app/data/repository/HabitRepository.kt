package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.HabitDao
import com.hstudy.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class HabitRepository(private val habitDao: HabitDao) {

    // ===== 习惯挑战 =====
    suspend fun addChallenge(challenge: HabitChallengeEntity): Long = habitDao.insertChallenge(challenge)

    suspend fun updateChallenge(challenge: HabitChallengeEntity) = habitDao.updateChallenge(challenge)

    suspend fun deleteChallenge(id: Long) = habitDao.deleteChallenge(id)

    fun getActiveChallenges(): Flow<List<HabitChallengeEntity>> = habitDao.getActiveChallenges()

    fun getAllChallenges(): Flow<List<HabitChallengeEntity>> = habitDao.getAllChallenges()

    suspend fun getChallengeById(id: Long): HabitChallengeEntity? = habitDao.getChallengeById(id)

    suspend fun incrementDay(id: Long) = habitDao.incrementCompletedDays(id)

    suspend fun completeChallenge(id: Long) = habitDao.completeChallenge(id)

    fun getCompletedChallengeCount(): Flow<Int> = habitDao.getCompletedChallengeCount()

    /**
     * 打卡习惯挑战 - 增加一天，如果达到目标则标记完成
     * @return CheckInResult: ALREADY_CHECKED_IN(今日已打卡) / CHALLENGE_COMPLETED(挑战完成) / SUCCESS(打卡成功)
     */
    enum class CheckInResult { ALREADY_CHECKED_IN, CHALLENGE_COMPLETED, SUCCESS }

    suspend fun checkInChallenge(challenge: HabitChallengeEntity): CheckInResult {
        val today = todayString()
        // 防止当天重复打卡
        if (challenge.lastCheckInDate == today) {
            return CheckInResult.ALREADY_CHECKED_IN
        }
        val newDays = challenge.completedDays + 1
        return if (newDays >= challenge.totalDays) {
            // 挑战完成：同时记录打卡日期
            habitDao.updateChallenge(challenge.copy(completedDays = newDays, lastCheckInDate = today))
            habitDao.completeChallenge(challenge.id)
            CheckInResult.CHALLENGE_COMPLETED
        } else {
            habitDao.updateChallenge(challenge.copy(completedDays = newDays, lastCheckInDate = today))
            CheckInResult.SUCCESS
        }
    }

    /** 判断挑战今日是否已打卡 */
    fun isCheckedInToday(challenge: HabitChallengeEntity): Boolean {
        val today = todayString()
        return challenge.lastCheckInDate == today
    }

    /**
     * 撤销今日打卡（Antigravity 指令 P1-01，撤销闭环）：回退一天并清掉 lastCheckInDate；
     * 若撤销的是"完成日"那一次打卡（CHALLENGE_COMPLETED 分支），同时把挑战重新置为进行中。
     * 积分侧的取消/扣回由调用方紧接着走 [PointRepository.undoCheckIn]
     * （bizKey=`HABIT:id:date`），保持撤销对称（不变量 4）。
     */
    suspend fun undoCheckInChallenge(challenge: HabitChallengeEntity) {
        // 回审 P1-1：撤销锚定**存储的打卡日**而非点击时刻的今天——
        // 页面跨午夜未关时（23:59 打卡、00:01 撤销），今天已不是打卡日，旧守卫会静默 return。
        // 允许 今天/昨天 两日窗口（跨午夜对称），更早的打卡日不允许经此入口回退。
        val stored = challenge.lastCheckInDate
        if (stored.isEmpty()) return
        val today = todayString()
        val yesterday = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
            .minus(1, DateTimeUnit.DAY)
            .toString()
        if (stored != today && stored != yesterday) return
        habitDao.updateChallenge(
            challenge.copy(
                completedDays = (challenge.completedDays - 1).coerceAtLeast(0),
                lastCheckInDate = "",
                isActive = true,
                isCompleted = false
            )
        )
    }

    // ===== 成就 =====
    suspend fun addAchievement(achievement: AchievementEntity): Long = habitDao.insertAchievement(achievement)

    fun getAllAchievements(): Flow<List<AchievementEntity>> = habitDao.getAllAchievements()

    suspend fun getAchievementByType(type: String): AchievementEntity? = habitDao.getAchievementByType(type)

    fun getAchievementCount(): Flow<Int> = habitDao.getAchievementCount()

    /**
     * 尝试解锁成就（如果未解锁过）
     */
    suspend fun tryUnlockAchievement(type: String, title: String, description: String, emoji: String): Boolean {
        val existing = habitDao.getAchievementByType(type)
        if (existing == null) {
            habitDao.insertAchievement(
                AchievementEntity(type = type, title = title, description = description, emoji = emoji)
            )
            return true
        }
        return false
    }

    // ===== 完成证明 =====
    suspend fun addProof(proof: CompletionProofEntity): Long = habitDao.insertProof(proof)

    fun getPendingProofs(): Flow<List<CompletionProofEntity>> = habitDao.getPendingProofs()

    fun getAllProofs(): Flow<List<CompletionProofEntity>> = habitDao.getAllProofs()

    fun getPendingProofCount(): Flow<Int> = habitDao.getPendingProofCount()

    suspend fun approveProof(id: Long, points: Int) =
        habitDao.reviewProof(id, ProofStatus.APPROVED, Clock.System.now().toEpochMilliseconds(), points)

    suspend fun rejectProof(id: Long) =
        habitDao.reviewProof(id, ProofStatus.REJECTED, Clock.System.now().toEpochMilliseconds())
}
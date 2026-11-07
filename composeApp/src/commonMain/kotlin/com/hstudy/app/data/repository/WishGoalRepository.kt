package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.WishGoalDao
import com.hstudy.app.data.local.entity.PointRecordEntity
import com.hstudy.app.data.local.entity.PointRecordType
import com.hstudy.app.data.local.entity.WishGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class WishGoalRepository(
    private val wishGoalDao: WishGoalDao,
    private val pointDao: com.hstudy.app.data.local.dao.PointDao
) {

    fun getAllGoals(): Flow<List<WishGoalEntity>> = wishGoalDao.getAllGoals()

    fun getActiveGoals(): Flow<List<WishGoalEntity>> = wishGoalDao.getActiveGoals()

    suspend fun getGoalById(id: Long): WishGoalEntity? = wishGoalDao.getGoalById(id)

    suspend fun addGoal(goal: WishGoalEntity): Long = wishGoalDao.insert(goal)

    suspend fun updateGoal(goal: WishGoalEntity) = wishGoalDao.update(goal)

    suspend fun deleteGoal(id: Long) = wishGoalDao.delete(id)

    /**
     * 存入积分到心愿存钱罐：
     * 从总积分中扣除，累加到 savedPoints，并写入 PointRecord（扣除类型）
     * @return 是否存入成功（积分不足时返回 false）
     */
    suspend fun depositPoints(goalId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val goal = wishGoalDao.getGoalById(goalId) ?: return false

        // 检查当前总积分是否足够
        val total = pointDao.getTotalPoints().first()
        if (total < amount) return false

        val weekStart = getWeekStartTimestamp()

        // 1. 扣除积分（写入负数积分记录）
        pointDao.insertPointRecord(
            PointRecordEntity(
                points = -amount,
                type = PointRecordType.ADJUSTMENT,
                note = "存入心愿存钱罐：${goal.name}",
                weekStart = weekStart
            )
        )

        // 2. 累加到心愿目标
        wishGoalDao.addSavedPoints(goalId, amount)

        // 3. 检查是否达成目标
        val updated = wishGoalDao.getGoalById(goalId)
        if (updated != null && updated.savedPoints >= updated.targetPoints && updated.isActive) {
            wishGoalDao.markCompleted(goalId, Clock.System.now().toEpochMilliseconds())
        }
        return true
    }

    companion object {
        /** 获取本周起始时间戳（周一 00:00） */
        private fun getWeekStartTimestamp(): Long = weekStartTimestamp()
    }
}
package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.RewardDao
import com.hstudy.app.data.local.entity.RewardEntity
import com.hstudy.app.data.local.entity.RewardExchangeEntity
import kotlinx.coroutines.flow.Flow

class RewardRepository(private val rewardDao: RewardDao) {

    fun getAllActiveRewards(): Flow<List<RewardEntity>> = rewardDao.getAllActiveRewards()

    suspend fun getActiveRewardCount(): Int = rewardDao.getActiveRewardCount()

    /** 确保预设奖励存在：奖励表为空时自动补全（惠及老用户/被清空场景） */
    suspend fun ensureDefaultRewards() {
        if (rewardDao.getActiveRewardCount() == 0) {
            initDefaultRewards()
        }
    }

    suspend fun getRewardById(id: Long): RewardEntity? = rewardDao.getRewardById(id)

    suspend fun addReward(reward: RewardEntity): Long = rewardDao.insertReward(reward)

    suspend fun updateReward(reward: RewardEntity) = rewardDao.updateReward(reward)

    suspend fun deleteReward(id: Long) = rewardDao.softDeleteReward(id)

    suspend fun exchangeReward(rewardId: Long, pointsSpent: Int, note: String = ""): Long {
        return rewardDao.insertExchange(
            RewardExchangeEntity(
                rewardId = rewardId,
                pointsSpent = pointsSpent,
                note = note
            )
        )
    }

    fun getAllExchanges(): Flow<List<RewardExchangeEntity>> = rewardDao.getAllExchanges()

    /**
     * 初始化默认奖励（首次启动时调用）
     */
    suspend fun initDefaultRewards() {
        val defaults = listOf(
            // ===== 娱乐类 =====
            RewardEntity(name = "看一集动画片", description = "看一集喜欢的动画片", pointsCost = 80, iconEmoji = "📺", category = "娱乐", sortOrder = 1),
            RewardEntity(name = "打游戏30分钟", description = "玩30分钟喜欢的游戏", pointsCost = 150, iconEmoji = "🎮", category = "娱乐", sortOrder = 2),
            RewardEntity(name = "打游戏60分钟", description = "畅享60分钟游戏时间", pointsCost = 280, iconEmoji = "🕹️", category = "娱乐", sortOrder = 3),
            RewardEntity(name = "玩桌游一次", description = "和家人玩一次桌游", pointsCost = 200, iconEmoji = "🎲", category = "娱乐", sortOrder = 4),
            RewardEntity(name = "看一场电影", description = "影院或家庭影院看一场电影", pointsCost = 500, iconEmoji = "🎬", category = "娱乐", sortOrder = 5),
            // ===== 美食类 =====
            RewardEntity(name = "买一份零食", description = "买一份喜欢的零食小吃", pointsCost = 100, iconEmoji = "🍿", category = "美食", sortOrder = 6),
            RewardEntity(name = "买一个冰淇淋", description = "买一个冰淇淋或小蛋糕", pointsCost = 120, iconEmoji = "🍦", category = "美食", sortOrder = 7),
            RewardEntity(name = "自己点一次外卖", description = "自己选一家店点外卖", pointsCost = 300, iconEmoji = "🥡", category = "美食", sortOrder = 8),
            RewardEntity(name = "选一家餐厅吃大餐", description = "选一家喜欢的餐厅吃大餐", pointsCost = 600, iconEmoji = "🍕", category = "美食", sortOrder = 9),
            // ===== 出行类 =====
            RewardEntity(name = "骑自行车兜风", description = "出去骑自行车兜风", pointsCost = 150, iconEmoji = "🚲", category = "出行", sortOrder = 10),
            RewardEntity(name = "去图书馆/书店", description = "去图书馆或书店逛一逛", pointsCost = 200, iconEmoji = "📖", category = "出行", sortOrder = 11),
            RewardEntity(name = "去公园玩半天", description = "去公园或游乐场玩半天", pointsCost = 400, iconEmoji = "🌳", category = "出行", sortOrder = 12),
            RewardEntity(name = "周末短途旅行", description = "和家人一次周末短途旅行", pointsCost = 1500, iconEmoji = "🏖️", category = "出行", sortOrder = 13),
            // ===== 特权类 =====
            RewardEntity(name = "免一次家务", description = "可以免除一次家务", pointsCost = 100, iconEmoji = "🧹", category = "特权", sortOrder = 14),
            RewardEntity(name = "选择今晚菜品", description = "决定今晚吃什么", pointsCost = 150, iconEmoji = "🍽️", category = "特权", sortOrder = 15),
            RewardEntity(name = "晚睡30分钟", description = "可以晚睡30分钟", pointsCost = 180, iconEmoji = "🌙", category = "特权", sortOrder = 16),
            RewardEntity(name = "晚睡60分钟", description = "可以晚睡1小时", pointsCost = 320, iconEmoji = "🌃", category = "特权", sortOrder = 17),
            RewardEntity(name = "当一天小家长", description = "决定一天的家庭活动安排", pointsCost = 800, iconEmoji = "👑", category = "特权", sortOrder = 18),
            // ===== 实物类 =====
            RewardEntity(name = "买一套卡片", description = "集齐一套喜欢的卡片", pointsCost = 250, iconEmoji = "🃏", category = "实物", sortOrder = 19),
            RewardEntity(name = "买一本课外书", description = "去书店选一本喜欢的课外书", pointsCost = 300, iconEmoji = "📚", category = "实物", sortOrder = 20),
            RewardEntity(name = "买文具/小玩具", description = "买一个喜欢的文具或小玩具", pointsCost = 400, iconEmoji = "🧸", category = "实物", sortOrder = 21),
            RewardEntity(name = "买一件喜欢的衣服", description = "预算内买一件喜欢的衣服", pointsCost = 1200, iconEmoji = "👕", category = "实物", sortOrder = 22),
            // ===== 社交类 =====
            RewardEntity(name = "和朋友视频30分钟", description = "和好朋友视频通话30分钟", pointsCost = 150, iconEmoji = "📱", category = "社交", sortOrder = 23),
            RewardEntity(name = "邀请朋友来家玩", description = "邀请好朋友来家里玩", pointsCost = 500, iconEmoji = "🏠", category = "社交", sortOrder = 24),
            RewardEntity(name = "兴趣班体验课", description = "参加一次兴趣班体验课", pointsCost = 600, iconEmoji = "🎨", category = "社交", sortOrder = 25),
            // ===== 高阶愿望 =====
            RewardEntity(name = "实现一个小愿望", description = "实现一个合理的小愿望", pointsCost = 1800, iconEmoji = "⭐", category = "特权", sortOrder = 26),
            RewardEntity(name = "神秘大奖", description = "爸爸妈妈准备的神秘大奖", pointsCost = 2000, iconEmoji = "🎁", category = "实物", sortOrder = 27),
        )
        defaults.forEach { rewardDao.insertReward(it) }
    }
}

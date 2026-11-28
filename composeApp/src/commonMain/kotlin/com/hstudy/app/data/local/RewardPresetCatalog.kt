package com.hstudy.app.data.local

import com.hstudy.app.data.local.entity.RewardEntity

/** Fields that identify an untouched canonical reward preset during historical migration. */
internal data class RewardPresetDefinition(
    val name: String,
    val description: String,
    val pointsCost: Int,
    val iconEmoji: String,
    val category: String,
    val sortOrder: Int
) {
    fun toEntity(): RewardEntity = RewardEntity(
        name = name,
        description = description,
        pointsCost = pointsCost,
        iconEmoji = iconEmoji,
        category = category,
        sortOrder = sortOrder,
        presetKey = name
    )
}

/**
 * Single source of truth for default reward seeding and v14 historical canonicalization.
 *
 * Mutable fields such as isActive and createdAt are intentionally excluded from the signature.
 */
internal object RewardPresetCatalog {
    val all: List<RewardPresetDefinition> = listOf(
        // ===== 娱乐类 =====
        RewardPresetDefinition("看一集动画片", "看一集喜欢的动画片", 80, "📺", "娱乐", 1),
        RewardPresetDefinition("打游戏30分钟", "玩30分钟喜欢的游戏", 150, "🎮", "娱乐", 2),
        RewardPresetDefinition("打游戏60分钟", "畅享60分钟游戏时间", 280, "🕹️", "娱乐", 3),
        RewardPresetDefinition("玩桌游一次", "和家人玩一次桌游", 200, "🎲", "娱乐", 4),
        RewardPresetDefinition("看一场电影", "影院或家庭影院看一场电影", 500, "🎬", "娱乐", 5),
        // ===== 美食类 =====
        RewardPresetDefinition("买一份零食", "买一份喜欢的零食小吃", 100, "🍿", "美食", 6),
        RewardPresetDefinition("买一个冰淇淋", "买一个冰淇淋或小蛋糕", 120, "🍦", "美食", 7),
        RewardPresetDefinition("自己点一次外卖", "自己选一家店点外卖", 300, "🥡", "美食", 8),
        RewardPresetDefinition("选一家餐厅吃大餐", "选一家喜欢的餐厅吃大餐", 600, "🍕", "美食", 9),
        // ===== 出行类 =====
        RewardPresetDefinition("骑自行车兜风", "出去骑自行车兜风", 150, "🚲", "出行", 10),
        RewardPresetDefinition("去图书馆/书店", "去图书馆或书店逛一逛", 200, "📖", "出行", 11),
        RewardPresetDefinition("去公园玩半天", "去公园或游乐场玩半天", 400, "🌳", "出行", 12),
        RewardPresetDefinition("周末短途旅行", "和家人一次周末短途旅行", 1500, "🏖️", "出行", 13),
        // ===== 特权类 =====
        RewardPresetDefinition("免一次家务", "可以免除一次家务", 100, "🧹", "特权", 14),
        RewardPresetDefinition("选择今晚菜品", "决定今晚吃什么", 150, "🍽️", "特权", 15),
        RewardPresetDefinition("晚睡30分钟", "可以晚睡30分钟", 180, "🌙", "特权", 16),
        RewardPresetDefinition("晚睡60分钟", "可以晚睡1小时", 320, "🌃", "特权", 17),
        RewardPresetDefinition("当一天小家长", "决定一天的家庭活动安排", 800, "👑", "特权", 18),
        // ===== 实物类 =====
        RewardPresetDefinition("买一套卡片", "集齐一套喜欢的卡片", 250, "🃏", "实物", 19),
        RewardPresetDefinition("买一本课外书", "去书店选一本喜欢的课外书", 300, "📚", "实物", 20),
        RewardPresetDefinition("买文具/小玩具", "买一个喜欢的文具或小玩具", 400, "🧸", "实物", 21),
        RewardPresetDefinition("买一件喜欢的衣服", "预算内买一件喜欢的衣服", 1200, "👕", "实物", 22),
        // ===== 社交类 =====
        RewardPresetDefinition("和朋友视频30分钟", "和好朋友视频通话30分钟", 150, "📱", "社交", 23),
        RewardPresetDefinition("邀请朋友来家玩", "邀请好朋友来家里玩", 500, "🏠", "社交", 24),
        RewardPresetDefinition("兴趣班体验课", "参加一次兴趣班体验课", 600, "🎨", "社交", 25),
        // ===== 高阶愿望 =====
        RewardPresetDefinition("实现一个小愿望", "实现一个合理的小愿望", 1800, "⭐", "特权", 26),
        RewardPresetDefinition("神秘大奖", "爸爸妈妈准备的神秘大奖", 2000, "🎁", "实物", 27)
    )
}

package com.hstudy.app.i18n

/**
 * 家长奖励商店管理页（ui/parent/ParentRewardManageScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用而非新建：空态标题「奖励商店还是空的」= [ParentHomeStrings.setupRewardsMissing]、
 * 载入提示「✅ 已载入预设奖励」= [ParentHomeStrings.presetRewardsLoadedMessage]
 * （家长首页快速配置助手已译，逐字节同值同语义）；「删除」= [CommonStrings.deleteContentDescription]。
 * 「编辑」无共享字段，本表新建 [editContentDescription]（与 W1 MistakeDetailStrings、
 * W2 ParentTaskManageStrings 同值同译，跨表测试钉住；上收 CommonStrings 是 W9 sweep 候选）。
 * 标题「奖励商店管理」与菜单 [ParentHomeStrings.menuRewardStoreTitle]（"奖励商店"）差一个「管理」，
 * 是**两个值**，本表自建 [title]。
 * 奖励名/描述/分类是 canonical 种子（W0 注册表已收录 27 名 + 27 描述 + 7 分类），
 * 渲染点包 [localizedPresetText]；家长自建内容未命中则原样。`initDefaultRewards()` 落库调用不动。
 * [costValue] 英文 one/other 用 [pointsWord]。
 */
data class ParentRewardManageStrings(
    val title: String,
    val addContentDescription: String,
    val emptyHint: String,
    val loadPresetsButton: String,
    val addOwnHint: String,
    val editContentDescription: String,
    val costValue: (cost: Int) -> String,
)

val ParentRewardManageStringsZh = ParentRewardManageStrings(
    title = "奖励商店管理",                                                    // :34
    addContentDescription = "添加奖励",                                        // :38
    emptyHint = "点击下方按钮一键载入 27 个常见奖励（看电影、零食、玩耍、满足心愿等），之后再逐个调整积分即可。", // :65
    loadPresetsButton = "✨ 一键载入预设奖励",                                   // :73
    addOwnHint = "或点击右下角 + 自行添加",                                      // :74
    editContentDescription = "编辑",                                           // :115
    costValue = { cost -> "$cost 积分" },                                     // :113
)

val ParentRewardManageStringsEn = ParentRewardManageStrings(
    title = "Reward store management",
    addContentDescription = "Add reward",
    emptyHint = "Tap the button below to load 27 common rewards (movies, snacks, playtime, granting wishes, and more) in one go, then adjust their point costs one by one.",
    loadPresetsButton = "✨ Load preset rewards",
    addOwnHint = "Or tap + at the bottom right to add your own",
    editContentDescription = "Edit",
    costValue = { cost -> "$cost ${pointsWord(cost)}" },
)

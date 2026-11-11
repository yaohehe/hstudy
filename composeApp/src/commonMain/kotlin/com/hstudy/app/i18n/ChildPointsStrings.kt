package com.hstudy.app.i18n

/**
 * 孩子端积分明细页（ui/child/ChildPointsScreen.kt）文案表（阶段 2 W4）。
 *
 * 页标题「积分明细」复用 [ChildHomeStrings.entryPointsDetailTitle]（孩子首页入口同值）。
 * 记录行优先显示账本 note（动态数据，原样）；note 为空时按 [PointRecordType] 回退到本表
 * 五条类型标签（与家长端 ParentPointsStrings.type* 文案不同值，不跨表复用）。
 * 数量词一律 [pointsWord]。
 */
data class ChildPointsStrings(
    val balanceTitle: String,
    val balanceValue: (points: Int) -> String,
    val pendingNote: (points: Int) -> String,
    val recordsTitle: String,
    val emptyRecords: String,
    val typeTaskComplete: String,
    val typeBonus: String,
    val typeAdjustment: String,
    val typeRewardExchange: String,
    val typeReversal: String,
)

val ChildPointsStringsZh = ChildPointsStrings(
    balanceTitle = "总积分余额",                                                // :49
    balanceValue = { points -> "⭐ $points 分" },                              // :50
    pendingNote = { points -> "⏳ 待家长审核 +$points 分（通过后计入余额）" },    // :55
    recordsTitle = "积分变动记录",                                             // :65
    emptyRecords = "还没有积分记录哦，快去打卡吧！",                              // :74
    typeTaskComplete = "完成任务",                                            // :105
    typeBonus = "获得奖励",                                                   // :106
    typeAdjustment = "家长调整",                                              // :107
    typeRewardExchange = "兑换奖励",                                          // :108
    typeReversal = "撤销扣回",                                                // :109
)

val ChildPointsStringsEn = ChildPointsStrings(
    balanceTitle = "Total points balance",
    balanceValue = { points -> "⭐ $points ${pointsWord(points)}" },
    pendingNote = { points -> "⏳ Pending parent approval: +$points ${pointsWord(points)} (added to your balance once approved)" },
    recordsTitle = "Point history",
    emptyRecords = "No point records yet — go check in!",
    typeTaskComplete = "Task completed",
    typeBonus = "Bonus earned",
    typeAdjustment = "Parent adjustment",
    typeRewardExchange = "Reward redeemed",
    typeReversal = "Reversed",
)

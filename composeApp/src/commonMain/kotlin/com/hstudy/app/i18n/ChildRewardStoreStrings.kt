package com.hstudy.app.i18n

/**
 * 孩子端奖励商店页（ui/child/ChildRewardStoreScreen.kt）文案表（阶段 2 W4）。
 *
 * 复用而非新建：页标题「奖励商店」= [ChildHomeStrings.entryRewardStoreTitle]（孩子首页入口同值），
 * 空态标题「奖励商店还是空的」= [ParentHomeStrings.setupRewardsMissing]（W3 已核逐字节同值），
 * 「取消」= [CommonStrings.cancelButton]。
 * 奖励名/描述/分类是 canonical（W0 注册表 27 名 + 27 描述 + 7 分类），渲染点包 [localizedPresetText]；
 * 家长自建内容未命中原样。分类分组键 categoryOrder/categoryEmoji 是落库值（Ruling 25），留在页面。
 * **落库字面量**：`spendPoints(cost, "兑换：${reward.name}")` 的 note 是账本数据，保持 canonical
 * 中文原样、不进本表（等价闸门 Task 5 登记披露"兑换：⟨$⟩"）。
 * 兑换记录行渲染的 exchange.note 是兑换时冻结的 canonical 奖励名（动态数据），同样不本地化。
 * [bannerAffordable] 的英文 reward/rewards 用行内条件（无既有复数 helper，不新增）；
 * 其余数量词一律 [pointsWord]。
 */
data class ChildRewardStoreStrings(
    val bannerAffordable: (points: Int, count: Int) -> String,
    val bannerKeepEarning: (points: Int) -> String,
    val nextGoalMessage: (need: Int, emoji: String, name: String) -> String,
    val categoryTitle: (category: String) -> String,
    val historyTitle: String,
    val exchangedFallback: String,
    val spentValue: (points: Int) -> String,
    val emptyHint: String,
    val confirmExchange: String,
    val costNeeded: (cost: Int) -> String,
    val currentBalance: (points: Int) -> String,
    val redeemSuccess: (name: String) -> String,
    val insufficientPoints: String,
    val redeemButton: String,
    val shortBy: (diff: Int) -> String,
    val priceLabel: (cost: Int) -> String,
)

val ChildRewardStoreStringsZh = ChildRewardStoreStrings(
    bannerAffordable = { points, count -> "🎉 你有 $points 分，现在可以兑换 $count 个奖励啦！" }, // :68
    bannerKeepEarning = { points -> "💪 你有 $points 分，完成任务赚积分来兑换吧！" },            // :69
    nextGoalMessage = { need, emoji, name -> "再攒 $need 分就能兑换「$emoji $name」啦～" },       // :74
    categoryTitle = { category -> "${category}奖励" },                                        // :107
    historyTitle = "我的兑换记录",                                                             // :157
    exchangedFallback = "已兑换",                                                              // :166
    spentValue = { points -> "-${points}分" },                                                 // :167
    emptyHint = "请爸爸妈妈到「奖励商店管理」里点一下“一键载入预设奖励”，就能出现好多奖品啦！",   // :187
    confirmExchange = "确认兑换",                                                              // :204/:235
    costNeeded = { cost -> "需要 $cost 积分" },                                                // :212
    currentBalance = { points -> "当前余额：$points 积分" },                                   // :213
    redeemSuccess = { name -> "🎉 兑换成功！$name" },                                          // :227
    insufficientPoints = "积分不足哦，继续加油！💪",                                           // :230
    redeemButton = "兑换",                                                                     // :304
    shortBy = { diff -> "还差 $diff 分" },                                                     // :304
    priceLabel = { cost -> "⭐ $cost 积分" },                                                  // :295
)

val ChildRewardStoreStringsEn = ChildRewardStoreStrings(
    bannerAffordable = { points, count ->
        "🎉 You have $points ${pointsWord(points)} — enough to redeem $count ${if (count == 1) "reward" else "rewards"}!"
    },
    bannerKeepEarning = { points -> "💪 You have $points ${pointsWord(points)} — complete tasks to earn more and redeem rewards!" },
    nextGoalMessage = { need, emoji, name -> "Just $need more ${pointsWord(need)} to redeem “$emoji $name”!" },
    categoryTitle = { category -> "$category rewards" },
    historyTitle = "My redemptions",
    exchangedFallback = "Redeemed",
    spentValue = { points -> "-$points ${pointsWord(points)}" },
    emptyHint = "Ask your parents to open “Reward store management” and tap “Load preset rewards” — then lots of prizes will appear!",
    confirmExchange = "Confirm redemption",
    costNeeded = { cost -> "Costs $cost ${pointsWord(cost)}" },
    currentBalance = { points -> "Current balance: $points ${pointsWord(points)}" },
    redeemSuccess = { name -> "🎉 Redeemed! $name" },
    insufficientPoints = "Not enough points yet — keep going! 💪",
    redeemButton = "Redeem",
    shortBy = { diff -> "$diff ${pointsWord(diff)} short" },
    priceLabel = { cost -> "⭐ $cost ${pointsWord(cost)}" },
)

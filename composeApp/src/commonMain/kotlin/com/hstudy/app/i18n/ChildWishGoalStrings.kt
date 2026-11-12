package com.hstudy.app.i18n

/**
 * 孩子端心愿存钱罐页（ui/wish/ChildWishGoalScreen.kt）文案表（阶段 2 W6）。
 *
 * 复用而非新建：取消 = [CommonStrings.cancelButton]。
 * `goal.name` / `goal.description` 是 canonical 存储值（3 个示例心愿由 W6 注册表译出、家长自建原样），
 * 渲染时包 [localizedPresetText]；[celebrate] / [depositSuccess] / [depositDialogTitle] 的 name
 * 实参同样是"已按注册表本地化过"的显示名，句式由本表负责。写入路径
 * （`depositPoints(goal.id, amount)` / `getGoalById`）与进度/数字格式（`"$percent%"`、快捷金额）不动。
 * 英文单复数用 [pointsWord]；[estDays] 的 day/days 用行内条件（与 W5 同型）。
 */
data class ChildWishGoalStrings(
    val title: String,
    val introTitle: String,
    val introBody: String,
    val emptyTitle: String,
    val emptyHint: String,
    val completedSection: String,
    val primaryBadge: String,
    val progressValue: (saved: Int, target: Int) -> String,
    val remaining: (n: Int) -> String,
    val milestone75: String,
    val milestone50: String,
    val milestone25: String,
    val milestoneStart: String,
    val estDays: (days: Int) -> String,
    val estPending: String,
    val depositButton: (available: Int) -> String,
    val celebrate: (name: String) -> String,
    val depositSuccess: (amount: Int, name: String) -> String,
    val insufficient: String,
    val completedLine: (saved: Int) -> String,
    val depositDialogTitle: (name: String) -> String,
    val depositDialogInfo: (available: Int, remaining: Int) -> String,
    val amountLabel: String,
    val allRemaining: String,
    val invalidAmount: String,
    val confirmDeposit: String,
)

val ChildWishGoalStringsZh = ChildWishGoalStrings(
    title = "🐷 心愿存钱罐",                                                    // :54
    introTitle = "💡 什么是心愿存钱罐？",                                        // :71
    introBody = "设定一个大目标，把积分一点点存进去，攒够就能兑换啦！学会耐心等待，收获更大的惊喜～", // :73
    emptyTitle = "还没有心愿目标",                                              // :87
    emptyHint = "请爸爸妈妈帮你设定一个心愿吧！",                                 // :88
    completedSection = "🎉 已达成的心愿",                                       // :107
    primaryBadge = "👑主目标",                                                 // :193
    progressValue = { saved, target -> "$saved / $target 分" },                // :225
    remaining = { n -> "还差 $n 分达成心愿" },                                  // :229
    milestone75 = "🔥 已完成75%，胜利在望，冲刺！",                              // :234
    milestone50 = "🌟 已经过半啦！继续保持，心愿越来越近！",                      // :235
    milestone25 = "💪 已完成25%，好的开始是成功的一半！",                         // :236
    milestoneStart = "🌱 已迈出第一步，加油攒积分！",                             // :237
    estDays = { days -> "📅 照最近的速度，大约再攒 $days 天就能实现啦！" },       // :248
    estPending = "📅 开始赚积分后，这里会告诉你大概几天能实现～",                  // :252
    depositButton = { available -> "💰 存入积分（当前可用 $available 分）" },    // :262
    celebrate = { name -> "🎉 心愿达成！\n$name" },                            // :132
    depositSuccess = { amount, name -> "✅ 已存入 $amount 积分到「$name」" },    // :136
    insufficient = "积分不足，无法存入",                                        // :139
    completedLine = { saved -> "已达成 · 共存入 $saved 分" },                   // :282
    depositDialogTitle = { name -> "💰 存入积分到「$name」" },                   // :304
    depositDialogInfo = { available, remaining ->
        "当前可用积分：$available 分\n还差 $remaining 分达成心愿"
    },                                                                        // :307
    amountLabel = "存入积分数量",                                               // :312
    allRemaining = "全部差额",                                                 // :324
    invalidAmount = "积分不足或数量无效",                                        // :330
    confirmDeposit = "存入",                                                   // :336
)

val ChildWishGoalStringsEn = ChildWishGoalStrings(
    title = "🐷 Wish Jar",
    introTitle = "💡 What is the Wish Jar?",
    introBody = "Set a big goal and save your points into it bit by bit — once you've saved enough, you can redeem it! Learn to wait patiently and enjoy a bigger surprise.",
    emptyTitle = "No wishes yet",
    emptyHint = "Ask your parents to set up a wish for you!",
    completedSection = "🎉 Achieved wishes",
    primaryBadge = "👑 Main goal",
    progressValue = { saved, target -> "$saved / $target ${pointsWord(target)}" },
    remaining = { n -> "$n ${pointsWord(n)} to go" },
    milestone75 = "🔥 75% done — victory is in sight. Sprint!",
    milestone50 = "🌟 Over halfway there! Keep going — your wish is getting closer!",
    milestone25 = "💪 25% done — a good start is half the battle!",
    milestoneStart = "🌱 First step taken — keep saving points!",
    estDays = { days -> "📅 At your recent pace, about $days more ${if (days == 1) "day" else "days"} to go!" },
    estPending = "📅 Once you start earning points, we'll show roughly how many days it will take",
    depositButton = { available -> "💰 Save points ($available available)" },
    celebrate = { name -> "🎉 Wish achieved!\n$name" },
    depositSuccess = { amount, name -> "✅ Saved $amount ${pointsWord(amount)} into “$name”" },
    insufficient = "Not enough points to save",
    completedLine = { saved -> "Achieved · $saved ${pointsWord(saved)} saved" },
    depositDialogTitle = { name -> "💰 Save points into “$name”" },
    depositDialogInfo = { available, remaining ->
        "Available: $available ${pointsWord(available)}\n$remaining ${pointsWord(remaining)} to go"
    },
    amountLabel = "Amount to save",
    allRemaining = "All remaining",
    invalidAmount = "Not enough points or invalid amount",
    confirmDeposit = "Save",
)

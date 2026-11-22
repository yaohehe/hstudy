package com.hstudy.app.i18n

/**
 * 孩子端积分明细页（ui/child/ChildPointsScreen.kt）文案表（阶段 2 W4）。
 *
 * 页标题「积分明细」复用 [ChildHomeStrings.entryPointsDetailTitle]（孩子首页入口同值）。
 * 记录行优先显示账本 note（动态数据，原样）；note 为空时按 [PointRecordType] 回退到本表
 * 六条类型标签（与家长端 ParentPointsStrings.type* 文案不同值，不跨表复用；v3.3 W2 P2-01 增 typePenalty）。
 * 数量词一律 [pointsWord]。
 *
 * **v3.3.1 D-08 新增的第四种形态**：note 是纯 ASCII 业务键（`wish_deposit:<id>` / `wish_refund:<id>`）时，
 * 既不原样显示也不回退类型标签，而由 [wishDeposit]/[wishRefund] 派生"存入心愿/心愿退回 + 心愿名"，
 * 心愿已删除（回查不到名字）时用 [wishDepositGeneric]/[wishRefundGeneric]。
 * 库里的 note 从此不含展示文案，英文用户不会在孩子账本里看到中文。
 *
 * **v3.3.2 D-12 把同一形态扩展到三处里程碑 bonus**：`bonus_excellent:<yyyy-MM-dd>` /
 * `bonus_weekly:<weekStart>` / `bonus_streak:<days>`（ADJUSTMENT 记录）由
 * [com.hstudy.app.ui.common.bonusNoteDisplayText] 按 [bonusExcellent]/[bonusWeekly]/[bonusStreak]
 * 派生；中文值与 D-12 前直写 DB 的原文逐字节相同，旧中文记录仍原样 passthrough（append-only 不改写）。
 *
 * v3.3 W4（指令 §3.3.4）新增**资产看板 + 时间轴分组 + 条目语义标签**文案，家长端积分管理页
 * （ParentPointsScreen）跨表复用同一批字段（同一中文值一个英文出处，沿 balanceTitle/菜单标题复用先例）：
 * - [balanceSubtitle]：余额大数字下的"当前可用积分"副标（与 ChildWishGoalStrings 的长串"当前可用积分：…"
 *   是两个值，不冲突）；[totalEarnedValue]：🌟 累计获得行（账本正分合计，只读展示，不参与余额判等）；
 * - sectionToday/Yesterday/WeekEarlier/History：时间轴分组标题。`今天` 与 [MistakeListStrings.forecastLabels]
 *   首元素同值同译（Today），由 crossTableVocabularyPinned 钉住；`历史记录` 与
 *   [ParentApprovalStrings.historyTab] 刻意差 🕰️ 前缀成为**不同值**（沿 typePenalty 差前缀先例，不重开同值槽）；
 * - [tagCheckInUndo]：REVERSAL 条目右侧 [打卡撤销] 小标签词面（emoji/方括号装饰在页面侧加，不受 i18n 约束；
 *   PENALTY 的 [未完成扣分] 标签沿用 W2 的 typePenalty，不新建）。
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
    val typePenalty: String,
    // W4 §3.3.4-1 资产看板
    val balanceSubtitle: String,
    val totalEarnedValue: (points: Int) -> String,
    // W4 §3.3.4-2 时间轴分组标题
    val sectionToday: String,
    val sectionYesterday: String,
    val sectionWeekEarlier: String,
    val sectionHistory: String,
    // W4 §3.3.4-3 REVERSAL 条目 [打卡撤销] 标签词面
    val tagCheckInUndo: String,
    // v3.3.1 D-08：心愿存入/退回流水的派生文案（note 只有业务键，文案在库里 → 已禁止）
    val wishDeposit: (goalName: String) -> String,
    val wishRefund: (goalName: String) -> String,
    val wishDepositGeneric: String,
    val wishRefundGeneric: String,
    // v3.3.2 D-12：三处里程碑 bonus 的派生文案（note 只存 bonus_* 业务键；
    // 中文值与 D-12 前直写 DB 的原文逐字节相同，由 ChildRewardStoreCopyTest 钉住，
    // 新旧记录在中文界面词面一致；英文界面从此不再在账本里念出旧中文）
    val bonusExcellent: String,
    val bonusWeekly: String,
    val bonusStreak: (days: Int) -> String,
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
    typePenalty = "未完成扣分",
    // W4 §3.3.4 资产看板 / 时间轴 / REVERSAL 标签（新写文案，两语由 copy-test 钉死）
    balanceSubtitle = "当前可用积分",
    totalEarnedValue = { points -> "🌟 累计获得 $points 分" },
    sectionToday = "今天",
    sectionYesterday = "昨天",
    sectionWeekEarlier = "本周更早",
    sectionHistory = "🕰️ 历史记录",
    tagCheckInUndo = "打卡撤销",
    // D-08：心愿两型流水（goalName 由明细页回查 wish_goals 后包 localizedPresetText 传入）
    wishDeposit = { goalName -> "存入心愿「$goalName」" },
    wishRefund = { goalName -> "心愿退回「$goalName」" },
    wishDepositGeneric = "存入心愿",
    wishRefundGeneric = "心愿退回",
    // D-12：三条 bonus 流水的渲染派生。中文与迁移前直写 DB 的原文**逐字节相同**
    // （ChildHomeStrings.kt 的 CanonicalChildHomeText 旧三常量，见 ChildRewardStoreCopyTest 逐字钉死），
    // 中文界面里新旧记录词面一致；库里只存 bonus_* 键。
    bonusExcellent = "今日学习表现优秀奖励",
    bonusWeekly = "每周挑战奖励：本周打卡5天",
    bonusStreak = { days -> "连续打卡${days}天里程碑奖励" },
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
    typePenalty = "Daily-task penalty",
    // W4 §3.3.4 (new redesign copy; "累计 = Total" follows the Ruling 44 vocabulary ruling)
    balanceSubtitle = "Points available now",
    totalEarnedValue = { points -> "🌟 Total earned: $points ${pointsWord(points)}" },
    sectionToday = "Today",
    sectionYesterday = "Yesterday",
    sectionWeekEarlier = "Earlier this week",
    sectionHistory = "🕰️ History",
    tagCheckInUndo = "Check-in undo",
    // D-08：心愿两型流水（goalName 由明细页回查 wish_goals、包 localizedPresetText 后传入；
    // 心愿已删除时回查不到，落到下面两条通用文案）
    wishDeposit = { goalName -> "Saved into wish “$goalName”" },
    wishRefund = { goalName -> "Wish refund for “$goalName”" },
    wishDepositGeneric = "Wish deposit",
    wishRefundGeneric = "Wish refund",
    // D-12：bonus 三键派生（中文值=迁移前 DB 原文；英文为新写词面，两语由
    // ChildRewardStoreCopyTest 逐字钉死）。英文 one/other 走 days==1 分支
    // （沿 childHome.streakMilestoneTitle 的 §8.3 先例；里程碑档位实际 ≥3，1 天是防御形态）。
    bonusExcellent = "Excellent study bonus for today",
    bonusWeekly = "Weekly challenge bonus: 5 check-ins this week",
    bonusStreak = { days ->
        "Streak milestone bonus: $days ${if (days == 1) "day" else "days"} in a row"
    },
)

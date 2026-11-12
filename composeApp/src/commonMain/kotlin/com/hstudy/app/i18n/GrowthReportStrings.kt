package com.hstudy.app.i18n

/**
 * 成长报告页（ui/report/GrowthReportScreen.kt）文案表（阶段 2 W6）。
 *
 * 六个统计项 = 标签 + 数值 + 副标题；数值格式走本表函数（[lifetimeValue] / [balanceSub] /
 * [streakValue] / [todayValue] / [exchangesValue] / [wishesValue]），英文单复数用
 * [pointsWord] / [timesWord] 与行内 day/days（W5/W6 同型）。
 * [reportText] 是分享到家族群的整段文本（7 参）。`petStage` 实参来自页面 `petStageName(lifetime)`，
 * 其 8 个阶段名（宠物蛋 / 刚破壳 / 小黄鸡 / 小猫咪 / 小狐狸 / 小狮子 / 独角兽 / 小神龙）
 * **属 W7 的迁移范围**：本波 `petStageName()` 与阶段名列表原样不动，分享文本原样嵌入，
 * 因此英文分享文本里仍会出现中文阶段名（计划 Ruling 3，W6 披露项）。
 * 中文八行照录迁移前页面的 `buildString`（`appendLine` 逐行换行，末行也带 `"\n"`，
 * 中文态分享文本逐字节不变——等价闸门按行钉回页面原文）；英文按新文案单串给出（无尾随换行）。
 * 行尾注释里的 `:行号` 是迁移前 GrowthReportScreen.kt 的坐标。
 */
data class GrowthReportStrings(
    val title: String,
    val heading: String,
    val subtitle: String,
    val shareButton: String,
    val shareHint: String,
    val lifetimeLabel: String,
    val streakLabel: String,
    val petLabel: String,
    val todayTasksLabel: String,
    val exchangesLabel: String,
    val wishesLabel: String,
    val lifetimeValue: (n: Int) -> String,
    val balanceSub: (n: Int) -> String,
    val streakValue: (n: Int) -> String,
    val streakSubPositive: String,
    val streakSubZero: String,
    val petSub: String,
    val todayValue: (done: Int, total: Int) -> String,
    val todaySubAll: String,
    val todaySubMore: String,
    val exchangesValue: (n: Int) -> String,
    val exchangesSub: String,
    val wishesValue: (n: Int) -> String,
    val wishesSub: String,
    val reportText: (
        lifetime: Int,
        streak: Int,
        petStage: String,
        done: Int,
        total: Int,
        exchanges: Int,
        wishes: Int,
    ) -> String,
)

val GrowthReportStringsZh = GrowthReportStrings(
    title = "📊 成长报告",                                                        // :71
    heading = "🌱 孩子的成长足迹",                                                // :84
    subtitle = "每一份坚持，都值得被看见",                                          // :85
    shareButton = "📤 分享成长报告到家族群",                                       // :119
    shareHint = "把孩子的进步分享给家人，一起见证成长～",                            // :121
    lifetimeLabel = "累计获得积分",                                               // :96
    streakLabel = "当前连续打卡",                                                 // :97
    petLabel = "学习宠物",                                                       // :98
    todayTasksLabel = "今日完成任务",                                             // :99
    exchangesLabel = "已兑换奖励",                                                // :100
    wishesLabel = "已达成心愿",                                                   // :101
    lifetimeValue = { n -> "$n 分" },                                            // :96
    balanceSub = { n -> "当前余额 $n 分" },                                      // :96
    streakValue = { n -> "$n 天" },                                              // :97
    streakSubPositive = "坚持就是胜利！",                                          // :97
    streakSubZero = "今天开始打卡吧",                                              // :97
    petSub = "累计积分越多，宠物越强大",                                            // :98
    todayValue = { done, total -> "$done/$total 个" },                           // :99
    todaySubAll = "今天全部完成，太棒了！",                                         // :99
    todaySubMore = "继续加油",                                                    // :99
    exchangesValue = { n -> "$n 次" },                                           // :100
    exchangesSub = "用努力换来的奖励最甜",                                          // :100
    wishesValue = { n -> "$n 个" },                                              // :101
    wishesSub = "攒钱实现大梦想",                                                  // :101
    reportText = { lifetime, streak, petStage, done, total, exchanges, wishes ->  // :59
        buildString {
            appendLine("📚 【HStudy 学习成长报告】")                              // :60
            appendLine("🌟 累计获得积分：$lifetime 分")                           // :61
            appendLine("🔥 当前连续打卡：$streak 天")                             // :62
            appendLine("🐾 学习宠物：$petStage")                                 // :63
            appendLine("✅ 今日完成任务：$done/$total 个")                         // :64
            appendLine("🎁 已兑换奖励：$exchanges 次")                            // :65
            appendLine("🐷 已达成心愿：$wishes 个")                               // :66
            appendLine("—— 每一份坚持都值得被看见 ❤️")                            // :67
        }
    },
)

val GrowthReportStringsEn = GrowthReportStrings(
    title = "📊 Growth report",
    heading = "🌱 Your child's growth footprints",
    subtitle = "Every bit of persistence deserves to be seen",
    shareButton = "📤 Share the growth report with the family",
    shareHint = "Share your child's progress with the family — witness the growth together",
    lifetimeLabel = "Total points earned",
    streakLabel = "Current check-in streak",
    petLabel = "Study pet",
    todayTasksLabel = "Tasks done today",
    exchangesLabel = "Rewards redeemed",
    wishesLabel = "Wishes achieved",
    lifetimeValue = { n -> "$n ${pointsWord(n)}" },
    balanceSub = { n -> "Current balance: $n ${pointsWord(n)}" },
    streakValue = { n -> "$n ${if (n == 1) "day" else "days"}" },
    streakSubPositive = "Persistence is victory!",
    streakSubZero = "Start checking in today",
    petSub = "The more points you earn, the stronger the pet",
    todayValue = { done, total -> "$done/$total" },
    todaySubAll = "All done today — awesome!",
    todaySubMore = "Keep going",
    exchangesValue = { n -> "$n ${timesWord(n)}" },
    exchangesSub = "Rewards earned through effort taste sweetest",
    wishesValue = { n -> "$n" },
    wishesSub = "Save up to realize a big dream",
    reportText = { lifetime, streak, petStage, done, total, exchanges, wishes ->
        "📚 [HStudy Growth Report]\n🌟 Total points earned: $lifetime\n" +
            "🔥 Current check-in streak: $streak ${if (streak == 1) "day" else "days"}\n" +
            "🐾 Study pet: $petStage\n" +
            "✅ Tasks done today: $done/$total\n" +
            "🎁 Rewards redeemed: $exchanges\n" +
            "🐷 Wishes achieved: $wishes\n" +
            "— Every bit of persistence deserves to be seen ❤️"
    },
)

package com.hstudy.app.i18n

/**
 * 孩子端每日学习目标页（ui/target/ChildDailyTargetScreen.kt）文案表（阶段 2 W5）。
 *
 * 8 个日常目标标题是页面内预设 canonical（写进积分账本 description），留在页面、
 * 渲染包 [localizedPresetText]（注册表 W5 组已收录，T1 落地）。
 * 6 个复盘标签是落偏好 canonical（saveDailyReflection 存中文），渲染时逐个包注册表后
 * 传入 [reflectionTags]（分隔符随语言：ZH「、」/ EN ", "）。
 * 5 个星级评语是纯渲染 → 本表字段（星数本身落偏好，不含文案）。
 * [selfRating] 的星星符号 "⭐" 两语相同。
 */
data class ChildDailyTargetStrings(
    val title: String,
    val setGoalsHint: String,
    val recommendTitle: String,
    val recommendSubtitle: String,
    val adoptAllButton: String,
    val pickGoalsTitle: String,
    val needOneGoal: String,
    val goalsSet: String,
    val setGoalsButton: (count: Int) -> String,
    val todayProgressTitle: String,
    val allDonePraise: String,
    val allDoneBonus: String,
    val goalDonePending: (title: String) -> String,
    val goalUndone: (title: String) -> String,
    val resetGoalsButton: String,
    val reflectionTitle: String,
    val selfRating: (stars: Int) -> String,
    val reflectionTags: (tags: List<String>) -> String,
    val reflectionDone: String,
    val reflectionPrompt: String,
    val starLabel1: String,
    val starLabel2: String,
    val starLabel3: String,
    val starLabel4: String,
    val starLabel5: String,
    val rateFirst: String,
    val reflectionSaved: String,
    val saveReflectionButton: String,
)

val ChildDailyTargetStringsZh = ChildDailyTargetStrings(
    title = "🎯 每日学习目标",                                                  // :78
    setGoalsHint = "💡 自己定目标，主动学习更有效！挑选今天想完成的目标（建议 3-5 个）。", // :93
    recommendTitle = "🤖 智能推荐",                                             // :105
    recommendSubtitle = "根据你的学习数据生成",                                  // :107
    adoptAllButton = "✨ 一键采纳推荐目标",                                      // :127
    pickGoalsTitle = "选择今日学习目标",                                         // :132
    needOneGoal = "请至少选择一个目标",                                          // :148
    goalsSet = "✅ 今日目标已设定，加油！",                                      // :153
    setGoalsButton = { count -> "🎯 设定今日目标（${count}个）" },               // :159
    todayProgressTitle = "📅 今日目标达成",                                      // :172
    allDonePraise = "🎉 太棒了！今日目标全部达成，你真是个自律的学习者！",          // :183
    allDoneBonus = "🎉 今日目标全部达成！额外奖励 +10 分（待审核）",              // :230
    goalDonePending = { title -> "✅ 「$title」完成 +2分（待家长审核）" },        // :232
    goalUndone = { title -> "↩️ 已取消「$title」" },                            // :244
    resetGoalsButton = "✏️ 重新设定今日目标",                                    // :265
    reflectionTitle = "🔍 每日复盘",                                            // :273
    // Ruling 20(b)：页面迁移前是 `"今日自评：" + "⭐".repeat(n)`（:276），中文字面量只到冒号为止、
    // 星星由页面另接。表侧按逐字节拷贝同样把 `"⭐".repeat(n)` 留在字面量外，否则等价闸门 strict 层
    // 会看到页面模板 "今日自评：" 与表侧 "今日自评：⟨$⟩" 差一个数值位（返回值不变，测试钉死）。
    selfRating = { stars -> "今日自评：" + "⭐".repeat(stars) },                 // :276
    reflectionTags = { tags -> "我的反思：${tags.joinToString("、")}" },         // :279
    reflectionDone = "✅ 已完成今日复盘，反思让学习更高效！",                      // :282
    reflectionPrompt = "今天学得怎么样？给自己打个分吧～",                        // :285
    starLabel1 = "需努力",                                                      // :302
    starLabel2 = "还不错",                                                      // :302
    starLabel3 = "不错哦",                                                      // :302
    starLabel4 = "很棒",                                                        // :302
    starLabel5 = "太棒了",                                                      // :302
    rateFirst = "请先给自己打个分吧",                                            // :323
    reflectionSaved = "✅ 复盘完成！反思让你进步更快",                            // :327
    saveReflectionButton = "📝 完成今日复盘",                                    // :333
)

val ChildDailyTargetStringsEn = ChildDailyTargetStrings(
    title = "🎯 Daily learning goals",
    setGoalsHint = "💡 Set your own goals — taking charge makes learning more effective! Pick what you want to finish today (3–5 recommended).",
    recommendTitle = "🤖 Smart recommendations",
    recommendSubtitle = "Generated from your learning data",
    adoptAllButton = "✨ Adopt all recommended goals",
    pickGoalsTitle = "Choose today's learning goals",
    needOneGoal = "Pick at least one goal",
    goalsSet = "✅ Today's goals are set — go for it!",
    setGoalsButton = { count -> "🎯 Set today's goals ($count)" },
    todayProgressTitle = "📅 Today's goal progress",
    allDonePraise = "🎉 Amazing — all of today's goals are done. You're a truly self-disciplined learner!",
    allDoneBonus = "🎉 All goals achieved! Bonus +10 points (awaiting approval)",
    goalDonePending = { title -> "✅ “$title” done +2 points (awaiting parent approval)" },
    goalUndone = { title -> "↩️ “$title” unchecked" },
    resetGoalsButton = "✏️ Reset today's goals",
    reflectionTitle = "🔍 Daily reflection",
    selfRating = { stars -> "Today's self-rating: ${"⭐".repeat(stars)}" },
    reflectionTags = { tags -> "My reflections: ${tags.joinToString(", ")}" },
    reflectionDone = "✅ Reflection saved — reflecting makes learning more effective!",
    reflectionPrompt = "How did today go? Give yourself a rating!",
    starLabel1 = "Needs work",
    starLabel2 = "Not bad",
    starLabel3 = "Good",
    starLabel4 = "Great",
    starLabel5 = "Excellent",
    rateFirst = "Pick a star rating first",
    reflectionSaved = "✅ Reflection complete — reflecting helps you improve faster",
    saveReflectionButton = "📝 Save today's reflection",
)

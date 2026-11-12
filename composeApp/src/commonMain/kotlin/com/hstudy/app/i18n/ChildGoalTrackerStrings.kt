package com.hstudy.app.i18n

/**
 * 孩子端目标追踪页（ui/habit/ChildGoalTrackerScreen.kt）文案表（阶段 2 W5）。
 * 四条"每日目标建议"与四条鼓励语是纯渲染文案；完成率/数量均为数字格式，不本地化。
 * [tasksCompleted] 英文单复数用行内条件。
 */
data class ChildGoalTrackerStrings(
    val title: String,
    val completionRateTitle: String,
    val tasksCompleted: (done: Int, total: Int) -> String,
    val streakLabel: String,
    val todayDoneLabel: String,
    val todayTotalLabel: String,
    val dailyGoalsTitle: String,
    val suggestionAllTasks: String,
    val suggestionThreeBasic: String,
    val suggestionPomodoro: String,
    val suggestionReview: String,
    val encouragePerfect: String,
    val encourageHigh: String,
    val encourageMid: String,
    val encourageStart: String,
)

val ChildGoalTrackerStringsZh = ChildGoalTrackerStrings(
    title = "📈 目标追踪",                                                      // :49
    completionRateTitle = "今日目标完成率",                                      // :61
    tasksCompleted = { done, total -> "$done / $total 任务已完成" },             // :65
    streakLabel = "连续打卡天数",                                               // :85
    todayDoneLabel = "今日完成",                                                // :86
    todayTotalLabel = "今日总任务",                                             // :87
    dailyGoalsTitle = "📋 每日学习目标",                                        // :94
    suggestionAllTasks = "完成当天所有学习任务",                                  // :98
    suggestionThreeBasic = "至少完成3个基础任务",                                // :99
    suggestionPomodoro = "番茄钟专注至少25分钟",                                  // :100
    suggestionReview = "复习1道错题",                                           // :101
    encouragePerfect = "🎉 完美！今日目标全部达成！",                             // :123
    encourageHigh = "💪 快完成了，再接再厉！",                                    // :124
    encourageMid = "👍 不错的进展，继续加油！",                                   // :125
    encourageStart = "🚀 开始行动吧，每一步都算数！",                             // :126
)

val ChildGoalTrackerStringsEn = ChildGoalTrackerStrings(
    title = "📈 Goal tracker",
    completionRateTitle = "Today's completion rate",
    tasksCompleted = { done, total -> "$done / $total ${if (total == 1) "task" else "tasks"} completed" },
    streakLabel = "Check-in streak (days)",
    todayDoneLabel = "Done today",
    todayTotalLabel = "Total tasks today",
    dailyGoalsTitle = "📋 Daily learning goals",
    suggestionAllTasks = "Finish all of today's learning tasks",
    suggestionThreeBasic = "Complete at least 3 basic tasks",
    suggestionPomodoro = "Focus for at least 25 minutes with Pomodoro",
    suggestionReview = "Review 1 mistake",
    encouragePerfect = "🎉 Perfect — all of today's goals achieved!",
    encourageHigh = "💪 Almost there — keep it up!",
    encourageMid = "👍 Good progress — keep going!",
    encourageStart = "🚀 Get started — every step counts!",
)

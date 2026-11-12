package com.hstudy.app.i18n

/**
 * 周报总览页（ui/weekly/WeeklyTrackerScreen.kt）文案表（阶段 2 W6）。
 *
 * 表格行的任务名 `task.title` 是 canonical 任务标题（种子/模板/家长自建），渲染时包
 * [localizedPresetText]：预设译出、自建原样；写入与打卡查询路径（`getCompletedTaskIdsForDate` 等）不动。
 * 纯数值模板（`"+$weeklyPoints"`、`"⭐ $totalPoints"`、`"+$dayPoints"` 与空单元格的 `-`）
 * 以及打卡对勾/空格子不是文案，留在页面。
 * [weekdayNames] 固定周一到周日七项，与页面 `dayIcons` 七枚一一对应；两张卡片的表头共用这一份，
 * 页面不再保留 `dayNames` 常量。行尾注释里的 `:行号` 是迁移前 WeeklyTrackerScreen.kt 的坐标。
 */
data class WeeklyTrackerStrings(
    val titleParent: String,
    val titleChild: String,
    val weeklyPointsLabel: String,
    val totalBalanceLabel: String,
    val basicsTitle: String,
    val enhanceTitle: String,
    val habitTitle: String,
    val dailySummaryTitle: String,
    val pointsRowLabel: String,
    val noTasks: String,
    val weekdayNames: List<String>,
)

val WeeklyTrackerStringsZh = WeeklyTrackerStrings(
    titleParent = "周报总览",                                                     // :73
    titleChild = "本周打卡",                                                      // :73
    weeklyPointsLabel = "本周获得积分",                                            // :95
    totalBalanceLabel = "总积分余额",                                              // :100
    basicsTitle = "📝 基础学习任务",                                              // :110
    enhanceTitle = "📈 提升学习任务",                                             // :123
    habitTitle = "🏆 习惯与态度奖励",                                             // :136
    dailySummaryTitle = "📊 每日积分汇总",                                        // :248
    pointsRowLabel = "💰 积分",                                                   // :273
    noTasks = "暂无任务",                                                         // :233
    weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),   // :26
)

val WeeklyTrackerStringsEn = WeeklyTrackerStrings(
    titleParent = "Weekly report overview",
    titleChild = "This week's check-ins",
    weeklyPointsLabel = "Points earned this week",
    totalBalanceLabel = "Total points balance",
    basicsTitle = "📝 Basic learning tasks",
    enhanceTitle = "📈 Advanced learning tasks",
    habitTitle = "🏆 Habits & attitude rewards",
    dailySummaryTitle = "📊 Daily points summary",
    pointsRowLabel = "💰 Points",
    noTasks = "No tasks",
    weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
)

package com.hstudy.app.i18n

/**
 * 孩子端今日时间线页（ui/schedule/ChildScheduleTimelineScreen.kt）文案表（阶段 2 W5）。
 * 日程标题与关联任务标题在渲染点包 [localizedPresetText]（预设译出、自建原样）。
 */
data class ChildScheduleTimelineStrings(
    val title: String,
    val nowLabel: (time: String) -> String,
    val subtitle: String,
    val emptyTitle: String,
    val emptyHint: String,
    val nowBadge: String,
    val linkedTaskLine: (title: String) -> String,
)

val ChildScheduleTimelineStringsZh = ChildScheduleTimelineStrings(
    title = "📅 今日时间线",                                                    // :47
    nowLabel = { time -> "现在 $time" },                                        // :68
    subtitle = "看看今天还有哪些安排吧～",                                        // :70
    emptyTitle = "今天没有日程安排",                                             // :86
    emptyHint = "轻松的一天，自由安排吧！",                                       // :87
    nowBadge = "👈 现在",                                                       // :162
    linkedTaskLine = { title -> "🔗 $title · 完成后记得打卡哦" },               // :170
)

val ChildScheduleTimelineStringsEn = ChildScheduleTimelineStrings(
    title = "📅 Today's timeline",
    nowLabel = { time -> "Now $time" },
    subtitle = "Here's what's left today",
    emptyTitle = "No schedule today",
    emptyHint = "A free day — plan it your way!",
    nowBadge = "👈 Now",
    linkedTaskLine = { title -> "🔗 $title · remember to check in when done" },
)

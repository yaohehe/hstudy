package com.hstudy.app.i18n

/**
 * 日程闹钟通知文案（ui/schedule/ScheduleAlarmHelper.kt，spec §8）：
 * 发出时 `currentDisplayLanguage()` → [stringsFor] 取词，日程标题走 [localizedPresetText]
 * （预设示例译出、家长自建原样）。已排程通知不追更；日程闹钟按日重排，切语言后最迟次日生效。
 */
data class ScheduleAlarmStrings(
    val timeUp: (emoji: String, title: String) -> String,
    val rememberCheckIn: String,
)

val ScheduleAlarmStringsZh = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title 时间到啦！" },                     // :64
    rememberCheckIn = "完成后记得打卡哦～",                                      // :65
)

val ScheduleAlarmStringsEn = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title — time's up!" },
    rememberCheckIn = "Remember to check in when you're done!",
)

package com.hstudy.app.i18n

/**
 * 日程闹钟通知文案（ui/schedule/ScheduleAlarmHelper.kt，spec §8）：
 * 发出时 `currentDisplayLanguage()` → [stringsFor] 取词，日程标题走 [localizedPresetText]
 * （预设示例译出、家长自建原样）。已排程通知不追更，且本地化副本只存活到下次启动/开机前
 * （Android 通知标题恒为渠道默认中文、启动/开机由 AndroidReminderBootstrap 重注册 canonical 中文；
 * 详见 ScheduleAlarmHelper 的 KDoc）：切语言后要在管理页重新保存日程才刷新文案。
 */
data class ScheduleAlarmStrings(
    val timeUp: (emoji: String, title: String) -> String,
    val rememberCheckIn: String,
    /** 正文分隔符：ZH 为空串（保持与迁移前逐字节一致），EN 为空格（两句之间必须有间隔）。 */
    val bodySeparator: String,
)

val ScheduleAlarmStringsZh = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title 时间到啦！" },                     // :64
    rememberCheckIn = "完成后记得打卡哦～",                                      // :65
    bodySeparator = "",
)

val ScheduleAlarmStringsEn = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title — time's up!" },
    rememberCheckIn = "Remember to check in when you're done!",
    bodySeparator = " ",
)

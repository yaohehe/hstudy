package com.hstudy.app.i18n

/**
 * 日程闹钟通知文案（ui/schedule/ScheduleAlarmHelper.kt，spec §8）：
 * 发出时 `currentDisplayLanguage()` → [stringsFor] 取词，日程标题走 [localizedPresetText]
 * （预设示例译出、家长自建原样）。已排程通知不追更（文案在排程时冻结），
 * 切语言后要在管理页重新保存日程（或重新启停提醒）才刷新。
 *
 * 发出点（W9 起全部接线）：
 * - 管理页保存/启停：[ScheduleAlarmHelper] 排程时取词并冻结进 `ScheduledReminder`；
 * - 启动/开机重注册：`androidMain/AndroidReminderBootstrap.rescheduleAll()` 按当前语言取词
 *   （正文组合与 [ScheduleAlarmHelper.notificationBody] 同一规则）；
 * - 到点弹通知：`androidMain/ScheduleAlarmReceiver` 在 **EN 态**用排程时冻结的 `EXTRA_TITLE`
 *   当通知标题（ZH 态不覆盖，沿用渠道默认「HStudy 日程提醒」，字节与迁移前一致）；
 *   排程元数据缺失（历史/外部 PendingIntent）时回落到 [alarmTitle]。
 *
 * [alarmTitle] 是 W9 Task 2 新增的唯一字段：它取代 `platform/ScheduleAlarmReceiver.kt` 里的
 * 兜底字面量"日程提醒"（中文值与迁移前逐字节一致；英文给的是通知标题短语，不是完整句子）。
 */
data class ScheduleAlarmStrings(
    val timeUp: (emoji: String, title: String) -> String,
    val rememberCheckIn: String,
    /** 正文分隔符：ZH 为空串（保持与迁移前逐字节一致），EN 为空格（两句之间必须有间隔）。 */
    val bodySeparator: String,
    /** 通知标题兜底（`EXTRA_TITLE` 缺失时用；EN 态同时进标题覆盖）；ZH 态通知标题恒为渠道默认。 */
    val alarmTitle: String,
)

val ScheduleAlarmStringsZh = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title 时间到啦！" },                     // :64
    rememberCheckIn = "完成后记得打卡哦～",                                      // :65
    bodySeparator = "",
    alarmTitle = "日程提醒",                                                     // ScheduleAlarmReceiver.kt:14
)

val ScheduleAlarmStringsEn = ScheduleAlarmStrings(
    timeUp = { emoji, title -> "$emoji $title — time's up!" },
    rememberCheckIn = "Remember to check in when you're done!",
    bodySeparator = " ",
    alarmTitle = "Schedule reminder",
)

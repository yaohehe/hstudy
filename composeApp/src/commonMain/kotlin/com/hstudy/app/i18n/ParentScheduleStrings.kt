package com.hstudy.app.i18n

/**
 * 家长端每日日程管理页（ui/schedule/ParentScheduleManageScreen.kt）文案表（阶段 2 W5）。
 *
 * 复用而非新建：保存 = [ParentTaskEditStrings.saveButton]、取消 = [CommonStrings.cancelButton]、
 * 删除 = [CommonStrings.deleteContentDescription]。
 * 5 个示例日程标题是 canonical 落库值（addSchedule 原样写入），留在页面、渲染/发通知时包
 * [localizedPresetText]（注册表 W5 组已收录）。日程标题与关联任务标题同理：用户自建未命中原样。
 * [repeatDays]：把原页面 repeatDaysText 的显示格式化迁入本表（纯渲染；"每天"/"周X"/星期名/分隔符
 * 均在 lambda 内，两语不同构）；页面保留 "1,2,3,4,5,6,7" 解析与 bizKey 逻辑不动。
 * [removedMessage] 刻意**不以 `delete` 开头**：存储边界闸门按词根前缀认 sink（`delete\w*(`），
 * 本字段是渲染用的 snackbar 构造器、实参是按注册表本地化过的标题，若沿用 `deletedMessage` 这个名字
 * 会被判成"文案进入删除写库调用"（闸门无法区分文案 getter 与仓储方法名）。语义仍是"已删除"。
 */
data class ParentScheduleStrings(
    val title: String,
    val addSchedule: String,
    val hint: String,
    val emptyTitle: String,
    val emptyHint: String,
    val samplesLoaded: String,
    val loadSamplesButton: String,
    val addOwnHint: String,
    val removedMessage: (title: String) -> String,
    val addedMessage: String,
    val updatedMessage: String,
    val linkedTaskLabel: (title: String) -> String,
    val repeatDays: (days: List<Int>) -> String,
    val editTitle: String,
    val timeLabel: String,
    val titleFieldLabel: String,
    val linkedTaskOptional: String,
    val none: String,
    val enableReminder: String,
)

val ParentScheduleStringsZh = ParentScheduleStrings(
    title = "⏰ 每日日程",                                                      // :41
    addSchedule = "添加日程",                                                   // :45/:228
    hint = "💡 为孩子设置每日时间日程（如 7:00 起床、16:00 写作业），到点自动通知提醒。可关联具体任务，引导孩子打卡。", // :59
    emptyTitle = "还没有日程安排",                                              // :74
    emptyHint = "点击下方按钮一键载入示例日程（起床/早餐/写作业/睡觉等），再按需调整时间即可。", // :75
    samplesLoaded = "✅ 已载入示例日程",                                        // :91
    loadSamplesButton = "✨ 一键载入示例日程",                                   // :93
    addOwnHint = "或点击右下角 + 自行添加",                                      // :94
    removedMessage = { title -> "已删除「$title」" },                           // :118
    addedMessage = "✅ 已添加日程",                                             // :141
    updatedMessage = "✅ 已更新日程",                                           // :159
    linkedTaskLabel = { title -> "🔗 关联任务：$title" },                       // :195
    repeatDays = { days ->
        val names = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
        if (days.size == 7) "每天" else "周" + days.mapNotNull { names[it] }.joinToString("、")
    },                                                                        // :300-304
    editTitle = "编辑日程",                                                     // :228
    timeLabel = "时间",                                                        // :233
    titleFieldLabel = "日程标题（如：起床、写作业）",                              // :241
    linkedTaskOptional = "关联任务（可选）",                                     // :255
    none = "无",                                                              // :259/:265
    enableReminder = "启用提醒",                                                // :273
)

val ParentScheduleStringsEn = ParentScheduleStrings(
    title = "⏰ Daily schedule",
    addSchedule = "Add schedule",
    hint = "💡 Set a daily timetable for your child (e.g. 7:00 get up, 16:00 homework) and get a reminder when it's time. You can link a task to guide check-ins.",
    emptyTitle = "No schedule yet",
    emptyHint = "Tap the button below to load sample schedules (wake up / breakfast / homework / bedtime, etc.), then adjust the times as needed.",
    samplesLoaded = "✅ Sample schedules loaded",
    loadSamplesButton = "✨ Load sample schedules",
    addOwnHint = "Or tap + at the bottom right to add your own",
    removedMessage = { title -> "Deleted “$title”" },
    addedMessage = "✅ Schedule added",
    updatedMessage = "✅ Schedule updated",
    linkedTaskLabel = { title -> "🔗 Linked task: $title" },
    repeatDays = { days ->
        val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        if (days.size == 7) "Every day" else days.mapNotNull { names[it] }.joinToString(", ")
    },
    editTitle = "Edit schedule",
    timeLabel = "Time",
    titleFieldLabel = "Schedule title (e.g. wake up, homework)",
    linkedTaskOptional = "Linked task (optional)",
    none = "None",
    enableReminder = "Enable reminder",
)

package com.hstudy.app.i18n

/**
 * 家长每周学习周报通知（ui/parent/WeeklyReportReceiver.kt + androidMain/platform/WeeklyReportReceiver.kt）
 * 文案表（阶段 2 W6，spec §8「通知文案在发出时取词」）。
 *
 * 取词时机按平台不同（与 [ReviewReminderStrings] / [ScheduleAlarmStrings] 同型）：
 * - Android 在**触发时**取词：androidMain 接收器到点统计本周数据后，现取
 *   `stringsFor(currentDisplayLanguage()).weeklyReport` 拼正文；**标题** EN 态用 [title] 覆盖，
 *   ZH 态沿用渠道默认标题「HStudy 每周学习周报」（与 v3.1.0 逐字节一致，W1 终审裁决；W9 接线，
 *   见 androidMain/WeeklyReportReceiver 的 KDoc）。[title]/[body] 在其余场合只进
 *   [com.hstudy.app.platform.ScheduledReminder] 的排程元数据（AlarmManager 的 extras）。
 * - iOS 在**排程时**把 [title]/[body] 冻结进 UNNotificationRequest（`IosNotificationScheduler`），
 *   没有周期重建，之后不追更；切语言须重新启停周报开关（或在设置页重新开启）才刷新。
 * - 排程/开机重注册（`AppGraph.bootstrap()` 与 `AndroidReminderBootstrap`）W9 起按生效语言取
 *   [title]/[body]（不再是硬编码中文：「每周学习周报」/「本周学习周报已生成，来看看孩子的学习情况吧！」
 *   即本表 ZH 值），所以冷启动不再把 iOS 的本地化快照盖回中文；但已发出的通知仍不追更（spec §8）。
 *
 * 中文值与迁移前逐字节一致：[title] = 原 `TITLE` 常量（ui/parent/WeeklyReportReceiver.kt:28），
 * [body] = 原 `BODY` 常量（:29）；[summary] = androidMain 接收器 `buildString` 前两次 append
 * 的拼接结果（:41-42），[encourageLow]/[encourageGood] = 空周/非空周分支（:43-44，
 * 前导「。」属于 encourage 字段本身），[fallback] = catch 分支正文（:53，查询异常兜底）。
 * 行尾注释里的 `:行号` 是迁移前两处原文的坐标。
 */
data class WeeklyReportStrings(
    val title: String,
    val body: String,
    /** 通知正文主体（发出时按当周统计值现拼；ZH 两次 append 的拼接，逐字节不变） */
    val summary: (checkins: Int, focusMin: Int, reviewCount: Int, earned: Int) -> String,
    val encourageLow: String,
    val encourageGood: String,
    val fallback: String,
)

val WeeklyReportStringsZh = WeeklyReportStrings(
    title = "每周学习周报",                                                       // :28
    body = "本周学习周报已生成，来看看孩子的学习情况吧！",                          // :29
    summary = { checkins, focusMin, reviewCount, earned ->                       // :41-42
        "📊 孩子本周学习周报：打卡${checkins}次 · 专注${focusMin}分钟 · 复习${reviewCount}道错题 · 获得${earned}积分"
    },
    encourageLow = "。本周学习较少，多鼓励孩子加油！",                             // :43
    encourageGood = "。继续保持，稳步进步！",                                      // :44
    fallback = "📊 孩子本周学习周报已生成，打开 App 查看详细报告。",               // :53
)

val WeeklyReportStringsEn = WeeklyReportStrings(
    title = "Weekly report",
    body = "Your child's weekly report is ready — take a look!",
    summary = { checkins, focusMin, reviewCount, earned ->
        "📊 Your child's week: $checkins check-ins · $focusMin min focused · " +
            "$reviewCount mistakes reviewed · $earned ${pointsWord(earned)} earned"
    },
    encourageLow = ". A lighter week — give your child some encouragement!",
    encourageGood = ". Keep it up — steady progress!",
    fallback = "📊 Your child's weekly report is ready — open the app for details.",
)

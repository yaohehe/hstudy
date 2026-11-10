package com.hstudy.app.i18n

/**
 * 错题复习通知（ui/mistake/ReviewReminderReceiver.kt）文案表（阶段 2 W1）。
 *
 * 取词时机 = **发出时**（spec §8）：common 侧 `ReviewReminderReceiver.localizedCopy()` 用
 * `stringsFor(currentDisplayLanguage())` 现取；已排程通知不追更，切换语言最迟下一个每日重建周期生效。
 * 三条正文分别对应迁移前 :55（有到期 N 条）、:60（默认）、:61（查询失败兜底）三处字面量；
 * 标题对应 :25。
 */
data class ReviewReminderStrings(
    val title: String,
    val dueBody: (dueCount: Int) -> String,
    val defaultBody: String,
    val fallbackBody: String,
)

val ReviewReminderStringsZh = ReviewReminderStrings(
    title = "错题复习提醒",
    dueBody = { dueCount -> "📚 今日有 $dueCount 道错题待复习，趁热打铁及时复习，记得更牢～" },
    defaultBody = "今天还有错题等着复习，快去看看吧！",
    fallbackBody = "📚 该复习错题啦！及时复习，记得更牢～",
)

val ReviewReminderStringsEn = ReviewReminderStrings(
    title = "Mistake review reminder",
    dueBody = { dueCount ->
        "📚 $dueCount ${mistakesWord(dueCount)} due for review today — review while it's fresh to remember longer"
    },
    defaultBody = "Mistakes are waiting to be reviewed — go take a look!",
    fallbackBody = "📚 Time to review your mistakes! Reviewing on time makes memories stick",
)

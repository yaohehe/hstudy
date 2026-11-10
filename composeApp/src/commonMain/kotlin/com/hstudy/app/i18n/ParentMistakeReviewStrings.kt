package com.hstudy.app.i18n

/**
 * 家长错题分析页（ui/mistake/ParentMistakeReviewScreen.kt）文案表（阶段 2 W1）。
 *
 * 统计卡的**裸标签** [statMastered]/[statUnmastered]（"已掌握"/"未掌握"）出自**本表**
 * （页面 `StatItem(strings.statMastered, …)`）；科目分布里的科目名、以及「最近错题」卡片上的
 * 掌握状态走共享表 [MistakeStrings]（[MistakeStrings.subjectMath]… 与
 * [MistakeStrings.masteredStatus]/[MistakeStrings.unmasteredStatus]），本表不重复。
 * 注意本表的裸标签与共享表带 emoji 的 "✅ 已掌握"/"⏳ 未掌握" 是两种不同形状的值，不得合并。
 */
data class ParentMistakeReviewStrings(
    val title: String,
    val overviewTitle: String,
    val statTotal: String,
    val statUnmastered: String,
    val statMastered: String,
    val subjectDistribution: String,
    val recentMistakes: String,
)

val ParentMistakeReviewStringsZh = ParentMistakeReviewStrings(
    title = "📊 错题分析",
    overviewTitle = "错题概览",
    statTotal = "总计",
    statUnmastered = "未掌握",
    statMastered = "已掌握",
    subjectDistribution = "科目分布",
    recentMistakes = "最近错题",
)

val ParentMistakeReviewStringsEn = ParentMistakeReviewStrings(
    title = "📊 Mistake Analysis",
    overviewTitle = "Overview",
    statTotal = "Total",
    statUnmastered = "Unmastered",
    statMastered = "Mastered",
    subjectDistribution = "By subject",
    recentMistakes = "Recent mistakes",
)

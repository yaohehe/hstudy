package com.hstudy.app.i18n

/**
 * 家长辅导建议页（`ui/parent/ParentTutoringAdviceScreen.kt`）文案表（阶段 2 W8 T2）。
 *
 * 本页只有两条固定文案：顶栏标题与说明卡。建议条目本身由 `TutoringAdviceEngine` 生成，
 * 引擎文案走 lang-keyed 的 [TutoringCopy]（不进本表，Ruling 1（W8））——本表只收页面骨架这两条。
 *
 * ZH 值逐字节等于迁移前页面字面量；两字段由 `W8CopyTest.parentTutoringAdviceCopyDiffers`
 * 逐字段 ZH≠EN + 英文 title/intro 整串钉死（`StringsCopyTest.englishTableContainsNoChineseLeftovers`
 * 的整表扫描只兜"英文表里抄了中文"，看不见"整条漏译成同一句英文"）。
 */
data class ParentTutoringAdviceStrings(
    val title: String,
    val intro: String,
)

val ParentTutoringAdviceStringsZh = ParentTutoringAdviceStrings(
    title = "💡 辅导建议",
    intro = "以下建议根据孩子的学科掌握度、错题情况和专注表现自动生成，帮助您更有针对性地辅导孩子。",
)

val ParentTutoringAdviceStringsEn = ParentTutoringAdviceStrings(
    title = "💡 Tutoring advice",
    intro = "The advice below is generated from your child's subject mastery, mistakes and focus — to help you coach more effectively.",
)

package com.hstudy.app.i18n

/**
 * 错题域共享词汇表（阶段 2 W1）：跨 4 个页面 + SpacedRepetition 的同一批中文值只在这里有一份。
 *
 * 为什么要有共享表（同 CommonStrings 的上收先例）：科目名在列表/详情/新增/家长分析四处出现，
 * 若各页各译一份，"其他"就会与 `PresetRegistry` 的 `preset("其他", "Other")` 以及各页面
 * 各自漂移；上收后中文值逐字节不变、英文只有一个出处，`MistakeCopyTest.crossTableSharedLabelsPinned`
 * 再把它与注册表/其它表钉成同值。
 *
 * 两类科目标签是**两个形状，不是同形异义**（都保留）：
 * - [subjectMath]…[subjectOther]：裸标签，详情页编辑芯片 / 列表筛选与卡片 / 家长分析用；
 * - [subjectMathChip]…[subjectOtherChip]：带 emoji 的芯片标签（"🔢 数学"），只有新增页用；
 *   页面里不再拼 "🔢" + 文本（拼接会留下页侧单边字面量，等价闸门按折叠后模板判等）。
 *
 * 熟练度/下次复习 8 条由 [com.hstudy.app.ui.mistake.SpacedRepetition] 消费：
 * 间隔算法留在 SpacedRepetition（纯域逻辑），文案在这里（测试可独立钉死两种语言）。
 * 英文用 timesWord 做 one/other（本表不用 daysWord；`nextReviewInDays` 是固定复数句式）；中文无单复数，直接插数值。
 * 术语：错题 = Mistake(s)（§13）、复习 = Review、掌握 = Mastered。
 */
data class MistakeStrings(
    val subjectMath: String,
    val subjectChinese: String,
    val subjectEnglish: String,
    val subjectScience: String,
    val subjectOther: String,
    val subjectMathChip: String,
    val subjectChineseChip: String,
    val subjectEnglishChip: String,
    val subjectScienceChip: String,
    val subjectOtherChip: String,
    val masteredStatus: String,
    val unmasteredStatus: String,
    val photoContentDescription: String,
    val proficiencyNone: String,
    val proficiencyTimes: (reviewCount: Int) -> String,
    val proficiencyFair: String,
    val proficiencyStrong: String,
    val nextReviewMastered: String,
    val nextReviewToday: String,
    val nextReviewTomorrow: String,
    val nextReviewInDays: (days: Int) -> String,
)

val MistakeStringsZh = MistakeStrings(
    subjectMath = "数学",
    subjectChinese = "语文",
    subjectEnglish = "英语",
    subjectScience = "科学",
    subjectOther = "其他",
    subjectMathChip = "🔢 数学",
    subjectChineseChip = "📝 语文",
    subjectEnglishChip = "🔤 英语",
    subjectScienceChip = "🔬 科学",
    subjectOtherChip = "📋 其他",
    masteredStatus = "✅ 已掌握",
    unmasteredStatus = "⏳ 未掌握",
    photoContentDescription = "错题照片",
    proficiencyNone = "未复习",
    proficiencyTimes = { reviewCount -> "复习${reviewCount}次" },
    proficiencyFair = "较熟练",
    proficiencyStrong = "已巩固",
    nextReviewMastered = "已掌握",
    nextReviewToday = "今天复习",
    nextReviewTomorrow = "明天复习",
    nextReviewInDays = { days -> "${days}天后复习" },
)

val MistakeStringsEn = MistakeStrings(
    subjectMath = "Math",
    subjectChinese = "Chinese",
    subjectEnglish = "English",
    subjectScience = "Science",
    subjectOther = "Other",
    subjectMathChip = "🔢 Math",
    subjectChineseChip = "📝 Chinese",
    subjectEnglishChip = "🔤 English",
    subjectScienceChip = "🔬 Science",
    subjectOtherChip = "📋 Other",
    masteredStatus = "✅ Mastered",
    unmasteredStatus = "⏳ Not mastered",
    photoContentDescription = "Mistake photo",
    proficiencyNone = "Not reviewed",
    proficiencyTimes = { reviewCount -> "Reviewed $reviewCount ${timesWord(reviewCount)}" },
    proficiencyFair = "Familiar",
    proficiencyStrong = "Solid",
    nextReviewMastered = "Mastered",
    nextReviewToday = "Review today",
    nextReviewTomorrow = "Review tomorrow",
    nextReviewInDays = { days -> "Review in $days days" },
)

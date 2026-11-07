package com.hstudy.app.i18n

/**
 * 公共控件（ui/common/CommonComponents.kt）文案表（Task 6）。
 *
 * ZH 四条逐字来自该文件第 91/127/151/161 行（迁移前行号），两条插值来自第 176/186 行；
 * 这些字面量此后只在本表出现，页面代码不再有中文。
 *
 * **去重清单是下限，不是字段数**：清单按字符串值去重得到 5 条，但 `"分"` 在同一文件里出现两次、
 * 语义完全不同——127 行是积分徽章的单位（`⭐ 12 分`），161 行是分钟步进器的标签。
 * 英文必须分开译（`points` vs `min`），所以本表把它们做成两个字段 [pointsUnit] 与 [minuteLabel]，
 * 共 6 个字段。以后任何页面照清单验收时也要先确认没有这种"同形异义"的字面量。
 *
 * [decreaseLabel] / [increaseLabel] 是**函数型字段**（本工程首例）：原文是 `contentDescription = "减少$label"`，
 * `$label` 由 [NumberStepper] 的调用方传入（"时"或"分"）。设计文档 §3 规定带参数文案存函数而不是 `{0}` 模板，
 * 理由是中英语序不同；英文这两条是给读屏器朗读的按钮说明，按英文习惯写成 "Decrease hr"，
 * 不套用"减少 + 单位"的中文结构。参数类型保持 `String`（而不是 Int）就是为了不改动页面传入的 `label` 实参。
 *
 * 函数型字段进不了 [StringsCopyTest.englishTableContainsNoChineseLeftovers]（data class 的 `toString()`
 * 不含函数型字段），所以由 `StringsCopyTest.commonInterpolatedCopyPinsBothLanguages` 用**逐字打出来的期望串**
 * 断言两种语言输出；该约定和理由写在测试的 KDoc 里。
 *
 * [pointsUnit] 用全称 "points"（术语表 §11：积分 = Points），与 Task 8 的 `+$N points` 保持同一词形；
 * [hourLabel] / [minuteLabel] 用缩写 "hr" / "min"，因为它们是 52.dp 宽、`labelSmall` 字号的单位标签。
 */
data class CommonStrings(
    val backContentDescription: String,
    val pointsUnit: String,
    val hourLabel: String,
    val minuteLabel: String,
    val decreaseLabel: (String) -> String,
    val increaseLabel: (String) -> String,
)

val CommonStringsZh = CommonStrings(
    backContentDescription = "返回",
    pointsUnit = "分",
    hourLabel = "时",
    minuteLabel = "分",
    decreaseLabel = { label -> "减少$label" },
    increaseLabel = { label -> "增加$label" },
)

val CommonStringsEn = CommonStrings(
    backContentDescription = "Back",
    pointsUnit = "points",
    hourLabel = "hr",
    minuteLabel = "min",
    decreaseLabel = { label -> "Decrease $label" },
    increaseLabel = { label -> "Increase $label" },
)

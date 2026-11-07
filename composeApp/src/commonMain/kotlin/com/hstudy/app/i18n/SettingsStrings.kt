package com.hstudy.app.i18n

/**
 * 家长设置页（ui/parent/ParentSettingsScreen）"语言"分区文案表。
 *
 * 本表只覆盖语言选择这一节（Task 5）。该页其余 64 条中文字面量仍留在源文件里，
 * 由 Task 11 在同一份 SettingsStrings 上增字段后迁移（Ruling 4：扩展而非重建）。
 *
 * ZH 六条的措辞与该页既有分区一致：标题是名词短语（对照"家长密码"/"数据备份与恢复"），
 * 说明句用陈述句 + 句号收尾（对照"定期备份孩子的学习数据……时可恢复。"）。
 *
 * 自称词规则（Ruling 6 + Ruling 15）：[languageZh] 与 [languageEn] 是语言的自称名
 * （endonym），**不随界面语言翻译**——两种 locale 下都显示「中文 / English」。
 * 这是语言选择控件的通用惯例：让用户用自己要找的名字选语言，也避免"中文"被译成
 * "Chinese" 后反而看不懂在选什么。因此这两个字段刻意同值，不进 assertDiffers 漏译守卫，
 * 由 StringsCopyTest.languageOptionLabelsAreEndonymsInBothLocales 用等值断言钉死；
 * SettingsStringsEn 里的"中文"字样同时在 StringsCopyTest.EN_TABLE_ENDONYMS 里登记豁免。
 * 请勿把它"顺手翻译"成 Chinese——豁免表存在的原因就是这里必须是中文。
 */
data class SettingsStrings(
    val languageTitle: String,
    val languageSystem: String,
    val languageZh: String,
    val languageEn: String,
    val languageNote: String,
    val partialTranslationNote: String,
)

val SettingsStringsZh = SettingsStrings(
    languageTitle = "语言",
    languageSystem = "跟随系统",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "选择界面显示语言，立即生效。",
    partialTranslationNote = "部分页面仍在适配英文，会暂时保持中文显示。",
)

val SettingsStringsEn = SettingsStrings(
    languageTitle = "Language",
    languageSystem = "System default",
    languageZh = "中文",
    languageEn = "English",
    languageNote = "Choose the app language. Changes apply immediately.",
    partialTranslationNote = "Some screens are not translated yet and stay in Chinese.",
)

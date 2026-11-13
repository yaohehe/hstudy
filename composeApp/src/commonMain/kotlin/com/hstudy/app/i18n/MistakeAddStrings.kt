package com.hstudy.app.i18n

/**
 * 添加错题页（ui/mistake/ChildMistakeAddScreen.kt）文案表（阶段 2 W1）。
 * 科目芯片标签走共享表 [MistakeStrings]（带 emoji 的 chip 形状），本表不重复；
 * 错误类型芯片显示 canonical 原值（`mistakeErrorTypes`）——W9 结束时仍未接线映射，
 * 属**已知残留**（芯片值参与落库；V-36 已按此口径改写）。
 */
data class MistakeAddStrings(
    val title: String,
    val subjectLabel: String,
    val titleLabel: String,
    val contentLabel: String,
    val wrongReasonLabel: String,
    val errorTypeHint: String,
    val correctMethodLabel: String,
    val takePhotoButton: String,
    val photoTaken: String,
    val missingTitleMessage: String,
    val saveButton: String,
)

val MistakeAddStringsZh = MistakeAddStrings(
    title = "➕ 添加错题",
    subjectLabel = "科目",
    titleLabel = "题目摘要",
    contentLabel = "题目详细描述",
    wrongReasonLabel = "❌ 错误原因分析",
    errorTypeHint = "错误类型（可选，用于错因分析）",
    correctMethodLabel = "✅ 正确解法",
    takePhotoButton = "拍照记录",
    photoTaken = "📷 已拍照",
    missingTitleMessage = "请输入题目摘要",
    saveButton = "保存错题",
)

val MistakeAddStringsEn = MistakeAddStrings(
    title = "➕ Add mistake",
    subjectLabel = "Subject",
    titleLabel = "Title",
    contentLabel = "Details",
    wrongReasonLabel = "❌ What went wrong",
    errorTypeHint = "Error type (optional, used for error analysis)",
    correctMethodLabel = "✅ Correct solution",
    takePhotoButton = "Take a photo",
    photoTaken = "📷 Photo taken",
    missingTitleMessage = "Please enter a title",
    saveButton = "Save mistake",
)

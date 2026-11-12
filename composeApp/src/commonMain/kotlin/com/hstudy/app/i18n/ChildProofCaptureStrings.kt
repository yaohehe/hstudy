package com.hstudy.app.i18n

/**
 * 孩子提交完成证明页（ui/proof/ChildProofCaptureScreen.kt）文案表（阶段 2 W6）。
 *
 * `taskTitle` 形参是 canonical 任务标题（由调用方传入存储值），页面渲染时包
 * [localizedPresetText]：预设标题译出、家长自建原样（Ruling 25）——表不持有任务名。
 * `Platform.camera.takePhoto`、`habitRepository.addProof(...)` 与 `SimpleDateFormat` 的
 * 日期模式串不动（写库路径与时间格式都不是 UI 文案）；备注输入框的用户内容也不入表。
 * 行尾注释里的 `:行号` 是迁移前 ChildProofCaptureScreen.kt 的坐标。
 */
data class ChildProofCaptureStrings(
    val title: String,
    val taskLabel: String,
    val takePhotoButton: String,
    val photoContentDescription: String,
    val emptyPhotoHint: String,
    val noteLabel: String,
    val needPhotoMessage: String,
    val submittedMessage: String,
    val submitButton: String,
    val tipsTitle: String,
    val tipsBody: String,
)

val ChildProofCaptureStringsZh = ChildProofCaptureStrings(
    title = "📷 提交完成证明",                                                   // :38
    taskLabel = "📋 关联任务",                                                  // :51
    takePhotoButton = "拍照",                                                   // :65
    photoContentDescription = "证明照片",                                        // :73
    emptyPhotoHint = "点击拍照或选择照片",                                        // :87
    noteLabel = "备注说明（可选）",                                               // :96
    needPhotoMessage = "请先拍照或选择照片",                                       // :104
    submittedMessage = "✅ 证明已提交，等待父母审核",                               // :119
    submitButton = "📤 提交完成证明",                                            // :127
    tipsTitle = "💡 温馨提示",                                                  // :136
    tipsBody = "提交后父母会审核。证明用于留痕，积分请在完成打卡后等待家长审批到账。",   // :137
)

val ChildProofCaptureStringsEn = ChildProofCaptureStrings(
    title = "📷 Submit proof",
    taskLabel = "📋 Linked task",
    takePhotoButton = "Take photo",
    photoContentDescription = "Proof photo",
    emptyPhotoHint = "Tap to take or choose a photo",
    noteLabel = "Note (optional)",
    needPhotoMessage = "Take or choose a photo first",
    submittedMessage = "✅ Proof submitted — waiting for parent review",
    submitButton = "📤 Submit proof",
    tipsTitle = "💡 Please note",
    tipsBody = "Parents will review it after submission. Proofs are kept for the record; " +
        "points arrive after your check-in is approved by a parent.",
)

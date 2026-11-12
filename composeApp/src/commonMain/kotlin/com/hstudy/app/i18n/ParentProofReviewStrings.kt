package com.hstudy.app.i18n

/**
 * 家长完成证明审核页（ui/proof/ParentProofReviewScreen.kt）文案表（阶段 2 W6）。
 *
 * 任务名的三个来源在页面侧分开处理：[taskFallback] 是**已本地化**的兜底文案（本表字段，
 * 英文态就是 "Task #N"），有映射时页面写
 * `localizedPresetText(taskNames[proof.taskId] ?: strings.taskFallback(proof.taskId), displayLanguage)`
 * ——命中注册表译出，家长自建标题原样（Ruling 25）。
 * `proof.note` 是用户内容（拍照时手输的备注），原样嵌入 [noteLine]，两语共用同一串；
 * `submittedAt` 的时间格式串仍在页面（`SimpleDateFormat` 模式不是文案）。
 * [approvedMessage] 刻意保留「积分请通过任务打卡审批发放」的说明：审核只改证明状态，
 * 不发积分（`approveProof(proof.id, 0)` 与 `rejectProof(proof.id)` 的调用不动）。
 * [approveButton] 的「(+5分)」是迁移前的历史文案（照录，实际发放走打卡审批），英文同形保留。
 * 行尾注释里的 `:行号` 是迁移前 ParentProofReviewScreen.kt 的坐标。
 */
data class ParentProofReviewStrings(
    val title: String,
    val filterPending: (n: Int) -> String,
    val filterAll: String,
    val emptyPending: String,
    val emptyAll: String,
    val statusPending: String,
    val statusApproved: String,
    val statusRejected: String,
    val taskFallback: (id: Long) -> String,
    val approvedMessage: String,
    val rejectedMessage: String,
    val noteLine: (note: String) -> String,
    val rejectButton: String,
    val approveButton: String,
    val photoContentDescription: String,
)

val ParentProofReviewStringsZh = ParentProofReviewStrings(
    title = "🔍 完成证明审核",                                                  // :53
    filterPending = { n -> "待审核 ($n)" },                                     // :69
    filterAll = "全部",                                                        // :71
    emptyPending = "没有待审核的证明",                                           // :79
    emptyAll = "还没有完成证明",                                                 // :79
    statusPending = "⏳ 待审核",                                                // :129
    statusApproved = "✅ 已通过",                                              // :130
    statusRejected = "❌ 已拒绝",                                              // :131
    taskFallback = { id -> "任务#$id" },                                        // :91
    approvedMessage = "✅ 已通过（积分请通过任务打卡审批发放）",                     // :98
    rejectedMessage = "❌ 已拒绝",                                              // :104
    noteLine = { note -> "备注: $note" },                                       // :164
    rejectButton = "拒绝",                                                     // :178
    approveButton = "通过 (+5分)",                                              // :186
    photoContentDescription = "证明照片",                                        // :155
)

val ParentProofReviewStringsEn = ParentProofReviewStrings(
    title = "🔍 Proof review",
    filterPending = { n -> "Pending ($n)" },
    filterAll = "All",
    emptyPending = "No proofs to review",
    emptyAll = "No proofs yet",
    statusPending = "⏳ Pending",
    statusApproved = "✅ Approved",
    statusRejected = "❌ Rejected",
    taskFallback = { id -> "Task #$id" },
    approvedMessage = "✅ Approved (points are issued via task check-in approval)",
    rejectedMessage = "❌ Rejected",
    noteLine = { note -> "Note: $note" },
    rejectButton = "Reject",
    approveButton = "Approve (+5 points)",
    photoContentDescription = "Proof photo",
)

package com.hstudy.app.i18n

/**
 * 家长积分审核页（ui/parent/ParentApprovalScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用：卡片「+N分」= [TaskListStrings.pointsEarnedLabel]（同一中文值、同一语义 = 某件事值多少积分，
 * 与 Task 9 ChildHomeScreen 的复用先例一致；等价闸门里该值已被 Task 9 双边披露覆盖）。
 * 标题带 📋 前缀，与家长首页菜单 [ParentHomeStrings.menuApprovalTitle]（"积分审核"，无 emoji）
 * 是**两个值**，故本表自建 [title]（与 W2 ParentTaskTemplateStrings.title 同理）。
 * [pendingTab] 的 ZH 侧保留原文的条件计数结构（`待审核` + 可选 ` N`），塌缩后与页面原文同形；
 * EN 用 "Pending (N)"。[approveAllMessage]/[approvedMessage] 英文 one/other 用 [pointsWord]。
 * `approval.description` 是审批描述（写入时语言冻结的动态业务数据，spec §1），原样渲染不入表。
 * 状态词汇与既有表对齐：撤销扣回 = points removed（[TaskListStrings.revertedClawedBackMessage]）。
 */
data class ParentApprovalStrings(
    val title: String,
    val approveAllButton: String,
    val historyTab: String,
    val emptyPendingTitle: String,
    val emptyPendingNote: String,
    val alreadyProcessedMessage: String,
    val rejectedMessage: String,
    val emptyHistory: String,
    val approveButton: String,
    val rejectButton: String,
    val statusPending: String,
    val statusApproved: String,
    val statusRejected: String,
    val statusCancelled: String,
    val statusRevoked: String,
    val approveAllMessage: (points: Int) -> String,
    val pendingTab: (count: Int) -> String,
    val approvedMessage: (points: Int) -> String,
)

val ParentApprovalStringsZh = ParentApprovalStrings(
    title = "📋 积分审核",                                                     // :46
    approveAllButton = "全部通过",                                             // :56
    historyTab = "历史记录",                                                   // :69
    emptyPendingTitle = "暂无待审核的积分申请",                                  // :78
    emptyPendingNote = "孩子完成学习行为后会在这里提交积分申请",                   // :79
    alreadyProcessedMessage = "该申请已处理，未重复加分",                         // :98
    rejectedMessage = "已拒绝该申请",                                           // :105
    emptyHistory = "暂无审核记录",                                              // :116
    approveButton = "通过",                                                    // :196
    rejectButton = "拒绝",                                                     // :204
    statusPending = "待审核",                                                  // :213
    statusApproved = "已通过",                                                 // :214
    statusRejected = "已拒绝",                                                 // :215
    statusCancelled = "已取消打卡（未计分）",                                    // :216
    statusRevoked = "已撤销（积分已扣回）",                                      // :217
    approveAllMessage = { points -> "✅ 已全部通过，共发放 $points 分" },         // :54
    pendingTab = { count -> "待审核${if (count > 0) " $count" else ""}" },      // :67
    approvedMessage = { points -> "✅ 已通过：+${points}分" },                   // :97
)

val ParentApprovalStringsEn = ParentApprovalStrings(
    title = "📋 Point approvals",
    approveAllButton = "Approve all",
    historyTab = "History",
    emptyPendingTitle = "No point requests to review",
    emptyPendingNote = "Point requests appear here after your child completes study activities",
    alreadyProcessedMessage = "Already handled — points were not added again",
    rejectedMessage = "Request rejected",
    emptyHistory = "No review history yet",
    approveButton = "Approve",
    rejectButton = "Reject",
    statusPending = "Pending",
    statusApproved = "Approved",
    statusRejected = "Rejected",
    statusCancelled = "Check-in cancelled (not scored)",
    statusRevoked = "Revoked (points removed)",
    approveAllMessage = { points -> "✅ All approved — $points ${pointsWord(points)} awarded" },
    pendingTab = { count -> if (count > 0) "Pending ($count)" else "Pending" },
    approvedMessage = { points -> "✅ Approved: +$points ${pointsWord(points)}" },
)
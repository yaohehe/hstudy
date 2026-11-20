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
 *
 * v3.3 W2 P1-02（指令【P1-02】/§四 状态机）新增历史卡**按状态如实展示**的分值语义字段：
 * [scorePending]/[scoreRevoked] 是函数型（分值进实参位），[scoreCancelled]/[scoreRejected] 是静态
 * （值恒为 0，语义在后括号）；APPROVED 仍是纯 "+N" 数值（无中文词面，不入表）。
 * 历史二级筛选钮 [historyFilterUndone] 覆盖 REVOKED+CANCELLED 两种"孩子撤销"终态；
 * 「全部/已通过/已拒绝」复用 [TaskListStrings.filterAll] 与本表 [statusApproved]/[statusRejected]
 * （同一中文值一个英文出处，不重开同值槽）。状态图标（emoji）不受 i18n 约束（RA8）。
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
    val scoreCancelled: String,
    val scoreRejected: String,
    val historyFilterUndone: String,
    val approveAllMessage: (points: Int) -> String,
    val pendingTab: (count: Int) -> String,
    val approvedMessage: (points: Int) -> String,
    val scorePending: (points: Int) -> String,
    val scoreRevoked: (points: Int) -> String,
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
    // P1-02：历史卡分值语义（§四：CANCELLED 从未计分 / REJECTED 不计分 / REVOKED 已扣回 / PENDING 待审）
    scoreCancelled = "0 (打卡已取消)",
    scoreRejected = "0 (已拒绝)",
    historyFilterUndone = "已撤销",
    approveAllMessage = { points -> "✅ 已全部通过，共发放 $points 分" },         // :54
    pendingTab = { count -> "待审核${if (count > 0) " $count" else ""}" },      // :67
    approvedMessage = { points -> "✅ 已通过：+${points}分" },                   // :97
    scorePending = { points -> "+$points (待审)" },
    scoreRevoked = { points -> "-$points (已扣回)" },
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
    // P1-02 history-card score semantics (§四 status machine)
    scoreCancelled = "0 (check-in cancelled)",
    scoreRejected = "0 (rejected)",
    historyFilterUndone = "Undone",
    approveAllMessage = { points -> "✅ All approved — $points ${pointsWord(points)} awarded" },
    pendingTab = { count -> if (count > 0) "Pending ($count)" else "Pending" },
    approvedMessage = { points -> "✅ Approved: +$points ${pointsWord(points)}" },
    scorePending = { points -> "+$points ${pointsWord(points)} pending" },
    scoreRevoked = { points -> "-$points ${pointsWord(points)} clawed back" },
)
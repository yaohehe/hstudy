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
 *
 * v3.3 W4（指令 §3.3.3）新增**待审看板 + 卡片业务类型标签**文案：
 * - [pendingCountLabel]/[pendingPointsLabel]：Tab 下方看板卡的两行读数（笔数 / 待发放积分）；
 *   [approveAllPointsButton] 是看板上"全部通过 (+M分)"填充按钮（与顶栏旧 [approveAllButton] 是两个值，
 *   旧字段保留——它仍是迁移前页面原文的配对槽，删了会破坏等价闸门）；
 * - [approveAllDialogMessage]：批量确认对话框正文（确认/取消复用 [CommonStrings.confirmButton/cancelButton]）；
 * - typeChip*：ApprovalCard 头部 SuggestionChip 的业务类型词（actionType → 词面映射在页面侧做；
 *   「习惯挑战」在 [ChildHomeStrings.entryHabitChallengeTitle] 已有同值槽，故本表 chip 用
 *   "习惯打卡"（HABIT_CHECKIN 的准确口径）重开**不同值**槽，沿 W2 typePenalty 差前缀先例。
 *
 * v3.3.1 D-10（指令 §4.4 家长撤回审批闭环）新增历史卡撤回文案 5 槽：
 * [revokeApprovalButton]/[revokeConfirmTitle]/[revokeTerminalMessage] 是静态钮面、弹窗标题与
 * 终态如实反馈；[revokeConfirmMessage]/[revokeSuccessMessage] 是函数型（扣回分值进实参位，
 * EN 单复数走 [pointsWord]）。字段命名统一走 `revoke…` 前缀——`undo…` 是存储边界闸门的
 * sink 词根（PointRepository.undoCheckIn），文案槽沿用 W9 T1 revert… 的避让先例。
 * 「扣回 N 分」词面与 [statusRevoked] / TaskListStrings.revertedClawedBackMessage 口径对齐。
 *
 * v3.3.1 D-11（指令 §5.2 超期待审一键清理）新增清理闭环文案 5 槽：
 * [staleCleanButton]/[staleConfirmTitle] 是提示条钮面与二次确认标题两条静态（EN 侧不提 points，
 * 刻意不进 StringsCopyTest 的 EN_STATIC_POINTS_MENTIONS 闭集）；[stalePendingNote]/
 * [staleConfirmMessage]/[staleCleanedMessage] 是函数型（笔数进实参位；中文量词 笔 只换数字，
 * EN 手写单复数分支，沿 pendingCountLabel 先例）。snackbar 按**实际清理笔数**如实反馈
 * （竞态下二次清理报 0）；「30 天」口径出自 PointRepository.STALE_PENDING_MILLIS，本表只出词面。
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
    // W4 §3.3.3-2 ApprovalCard 头部业务类型 SuggestionChip（按 actionType 映射）
    val typeChipTask: String,
    val typeChipHabit: String,
    val typeChipAssignment: String,
    val typeChipTarget: String,
    val typeChipMistake: String,
    val typeChipFocus: String,
    val typeChipVariation: String,
    val typeChipOther: String,
    val approveAllMessage: (points: Int) -> String,
    val pendingTab: (count: Int) -> String,
    val approvedMessage: (points: Int) -> String,
    val scorePending: (points: Int) -> String,
    val scoreRevoked: (points: Int) -> String,
    // W4 §3.3.3-1 待审看板（笔数 / 待发放积分 / 批量钮 / 确认对话框正文）
    val pendingCountLabel: (count: Int) -> String,
    val pendingPointsLabel: (points: Int) -> String,
    val approveAllPointsButton: (points: Int) -> String,
    val approveAllDialogMessage: (count: Int, points: Int) -> String,
    // v3.3.1 D-10 家长撤回审批（历史 APPROVED 卡钮 / 二次确认弹窗 / snackbar 如实反馈）
    val revokeApprovalButton: String,
    val revokeConfirmTitle: String,
    val revokeTerminalMessage: String,
    val revokeConfirmMessage: (points: Int) -> String,
    val revokeSuccessMessage: (points: Int) -> String,
    // v3.3.1 D-11 超期待审清理（看板下方提示条 / 清理钮 / 二次确认 / snackbar 笔数如实反馈）。
    // 命名统一走 stale… 前缀：`undo…` 是存储边界闸门 sink 词根（PointRepository.undoCheckIn），
    // 文案槽沿用 D-10 revoke… / W9 T1 revert… 的避让先例；EN 单复数手写分支（同 pendingCountLabel 先例）。
    val stalePendingNote: (count: Int) -> String,
    val staleCleanButton: String,
    val staleConfirmTitle: String,
    val staleConfirmMessage: (count: Int) -> String,
    val staleCleanedMessage: (count: Int) -> String,
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
    historyFilterUndone = "撤销与取消",
    // W4 §3.3.3-2：ApprovalCard 头部业务类型 chip（actionType → 词面）
    typeChipTask = "每日任务",
    typeChipHabit = "习惯打卡",
    typeChipAssignment = "家长布置",
    typeChipTarget = "每日目标",
    typeChipMistake = "错题复习",
    typeChipFocus = "专注番茄钟",
    typeChipVariation = "一题多解",
    typeChipOther = "学习打卡",
    approveAllMessage = { points -> "✅ 已全部通过，共发放 $points 分" },         // :54
    pendingTab = { count -> "待审核${if (count > 0) " $count" else ""}" },      // :67
    approvedMessage = { points -> "✅ 已通过：+${points}分" },                   // :97
    scorePending = { points -> "+$points (待审)" },
    scoreRevoked = { points -> "-$points (已扣回)" },
    // W4 §3.3.3-1：待审看板（中文侧量词 笔/分，不分支）
    pendingCountLabel = { count -> "待审总笔数：$count 笔" },
    pendingPointsLabel = { points -> "待发放总积分：+$points 分" },
    approveAllPointsButton = { points -> "全部通过 (+${points}分)" },
    approveAllDialogMessage = { count, points -> "确认一次通过全部 $count 笔申请，共发放 +$points 分？" },
    // v3.3.1 D-10：家长撤回审批（指令 §4.4 词面；「扣回 N 分」口径与 statusRevoked/
    // TaskListStrings.revertedClawedBackMessage 对齐，中文侧只换数字、不分支）
    revokeApprovalButton = "撤回通过",
    revokeConfirmTitle = "确认撤回审批？",
    revokeTerminalMessage = "该申请已是终态（可能已被孩子端撤销），未扣回积分",
    revokeConfirmMessage = { points -> "撤回后将扣回已发放的 $points 积分，并将申请状态置为已撤销。" },
    revokeSuccessMessage = { points -> "🔄 已撤回审批，扣回 $points 分" },
    // v3.3.1 D-11：超期待审清理（中文侧量词 笔，只换数字、不分支；「30 天」词面与提示条同源）
    // 终审 Minor-4：提示条读数出自组合期一次算好的 threshold 快照（ParentApprovalScreen
    // staleThreshold），长驻页面可能偏旧；清理本身在仓库层按执行时刻重筛（现状即正确），
    // 故确认正文补一句「实际笔数以执行时为准」如实披露，不动快照口径。
    stalePendingNote = { count -> "$count 笔申请已超过 30 天未审核" },
    staleCleanButton = "清理超期申请",
    staleConfirmTitle = "确认清理超期申请？",
    staleConfirmMessage = { count ->
        "将把 $count 笔超过 30 天仍未审核的申请置为已取消。这些申请从未发放积分，取消后余额不变；实际笔数以执行时为准。"
    },
    staleCleanedMessage = { count -> "🧹 已清理 $count 笔超期申请" },
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
    historyFilterUndone = "Reversed & cancelled",
    // W4 §3.3.3-2: business-type chips on the approval card header (actionType mapping lives in the screen)
    typeChipTask = "Daily task",
    typeChipHabit = "Habit check-in",
    typeChipAssignment = "Parent assignment",
    typeChipTarget = "Daily goal",
    typeChipMistake = "Mistake review",
    typeChipFocus = "Focus timer",
    typeChipVariation = "Variation bonus",
    typeChipOther = "Study activity",
    approveAllMessage = { points -> "✅ All approved — $points ${pointsWord(points)} awarded" },
    pendingTab = { count -> if (count > 0) "Pending ($count)" else "Pending" },
    approvedMessage = { points -> "✅ Approved: +$points ${pointsWord(points)}" },
    scorePending = { points -> "+$points ${pointsWord(points)} pending" },
    scoreRevoked = { points -> "-$points ${pointsWord(points)} removed" },
    // W4 §3.3.3-1: pending dashboard rows, batch button and its confirm dialog
    pendingCountLabel = { count ->
        if (count == 1) "1 request pending review" else "$count requests pending review"
    },
    pendingPointsLabel = { points -> "+$points ${pointsWord(points)} to award" },
    approveAllPointsButton = { points -> "Approve all (+$points ${pointsWord(points)})" },
    approveAllDialogMessage = { count, points ->
        // QA C4 后续语法收口：count==1 用自然单数；复数保留计数
        if (count == 1) {
            "Approve your only pending request and award +$points ${pointsWord(points)} now?"
        } else {
            "Approve all $count pending requests and award +$points ${pointsWord(points)} now?"
        }
    },
    // v3.3.1 D-10: parent-side revoke on history APPROVED cards (button, confirm dialog, honest snackbars)
    revokeApprovalButton = "Revoke",
    revokeConfirmTitle = "Revoke this approval?",
    revokeTerminalMessage = "Already final (your child may have undone it) — no points removed",
    revokeConfirmMessage = { points ->
        "Revoking removes $points ${pointsWord(points)} already awarded and marks the request as revoked."
    },
    revokeSuccessMessage = { points ->
        "🔄 Approval revoked — $points ${pointsWord(points)} removed"
    },
    // v3.3.1 D-11: stale pending cleanup (banner, clean button, confirm dialog, honest count snackbar).
    // Singular/plural branches follow the pendingCountLabel precedent (no new plural helper).
    // Final-review Minor-4: the banner count comes from a composition-time threshold snapshot and can age
    // on a long-open screen; the repository re-filters at execution time (already correct), so the dialog
    // body now says the actual count is decided at execution time.
    stalePendingNote = { count ->
        if (count == 1) "1 request has been pending for over 30 days"
        else "$count requests have been pending for over 30 days"
    },
    staleCleanButton = "Clean stale requests",
    staleConfirmTitle = "Clean stale requests?",
    staleConfirmMessage = { count ->
        if (count == 1) {
            "The request that has been pending for over 30 days will be marked cancelled. " +
                "It never awarded points, so your balance stays unchanged. " +
                "The actual count is decided at execution time."
        } else {
            "These $count requests, each pending for over 30 days, will be marked cancelled. " +
                "They never awarded points, so your balance stays unchanged. " +
                "The actual count is decided at execution time."
        }
    },
    staleCleanedMessage = { count ->
        if (count == 1) "🧹 Cleaned 1 stale request" else "🧹 Cleaned $count stale requests"
    },
)

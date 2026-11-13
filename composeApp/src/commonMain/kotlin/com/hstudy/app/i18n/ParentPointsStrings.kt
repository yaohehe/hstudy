package com.hstudy.app.i18n

/**
 * 家长积分管理页（ui/parent/ParentPointsScreen.kt）文案表（阶段 2 W3）。
 *
 * 复用而非新建（同一中文值一个英文出处）：
 * - 标题「积分管理」= [ParentHomeStrings.menuPointsTitle]（家长首页菜单已译 "Points management"）；
 * - 调整对话框「确认/取消」= [CommonStrings.confirmButton/cancelButton]。
 * 排行榜任务标题是 canonical/用户数据：`task?.let { localizedPresetText(it.title, …) }`，
 * 缺失回退 [taskFallbackTitle]（"任务#N" 是 UI 文案，入表）。
 * [timesCount] 英文 one/other 用 [timesWord]；[netValue] 是纯数值标签（Net -3），无复数。
 * 记录卡 `record.note` 是积分账本备注（spec §1：写入时语言冻结的动态数据），原样渲染不入表。
 * 类型标签词汇与既有表对齐：撤销 = check-in undo/reversal（[TaskListStrings.revertedClawedBackMessage]），
 * 兑换 = redeem/redemption（[RoleStrings.childDesc] EN "Redeem rewards"）。
 */
data class ParentPointsStrings(
    val adjustContentDescription: String,
    val dailyStatsTitle: String,
    val taskRankingTitle: String,
    val allRecordsTitle: String,
    val emptyRecords: String,
    val adjustDialogTitle: String,
    val adjustPointsLabel: String,
    val adjustNoteLabel: String,
    val typeTaskComplete: String,
    val typeBonus: String,
    val typeAdjustment: String,
    val typeRewardExchange: String,
    val typeReversal: String,
    val netValue: (net: Int) -> String,
    val taskFallbackTitle: (taskId: Long) -> String,
    val timesCount: (count: Int) -> String,
)

val ParentPointsStringsZh = ParentPointsStrings(
    adjustContentDescription = "调整积分",                                     // :56
    dailyStatsTitle = "📅 每日得分统计（近7天）",                                // :69
    taskRankingTitle = "📋 任务积分排行（近7天）",                               // :111
    allRecordsTitle = "全部积分记录",                                          // :125
    emptyRecords = "暂无积分记录",                                             // :132
    adjustDialogTitle = "手动调整积分",                                        // :146
    adjustPointsLabel = "积分数（正=加分，负=扣分）",                            // :152
    adjustNoteLabel = "备注说明",                                              // :159
    typeTaskComplete = "✅ 任务完成",                                          // :185
    typeBonus = "🎁 学习奖励",                                                 // :186
    typeAdjustment = "✏️ 手动调整",                                            // :187
    typeRewardExchange = "🛒 兑换消耗",                                        // :188
    typeReversal = "↩️ 撤销扣回",                                              // :189
    netValue = { net -> "净$net" },                                           // :88
    taskFallbackTitle = { taskId -> "任务#$taskId" },                          // :100
    timesCount = { count -> "${count}次" },                                   // :116
)

val ParentPointsStringsEn = ParentPointsStrings(
    adjustContentDescription = "Adjust points",
    dailyStatsTitle = "📅 Daily scores (last 7 days)",
    taskRankingTitle = "📋 Task points ranking (last 7 days)",
    allRecordsTitle = "All point records",
    emptyRecords = "No point records yet",
    adjustDialogTitle = "Adjust points manually",
    adjustPointsLabel = "Points (positive = add, negative = deduct)",
    adjustNoteLabel = "Note",
    typeTaskComplete = "✅ Task completed",
    typeBonus = "🎁 Study bonus",
    typeAdjustment = "✏️ Manual adjustment",
    typeRewardExchange = "🛒 Reward redemption",
    typeReversal = "↩️ Check-in reversal",
    netValue = { net -> "Net $net" },
    taskFallbackTitle = { taskId -> "Task #$taskId" },
    timesCount = { count -> "$count ${timesWord(count)}" },
)
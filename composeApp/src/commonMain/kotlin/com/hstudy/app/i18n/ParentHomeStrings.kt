package com.hstudy.app.i18n

/**
 * 家长首页（ui/parent/ParentHomeScreen.kt）文案表（Task 10）。
 *
 * 清单口径：`python scripts/i18n_inventory.py parent/ParentHomeScreen.kt` 迁移前给出**去重 59 条 / 含插值 8 条**，
 * 本表落成 **59 个字段（其中 8 个函数型）**，与清单 1:1，没有跨表复用（本页没有 `取消`/`确认`/`删除`
 * 这类通用词，也没有第二种 `分`：整页只有"连续打卡 N 天"的**天**与"N 道错题"的**道**两种量词）。
 * 每条 ZH 值逐字来自迁移前对应行（行号写在行尾注释里），迁移后页面只剩 `strings.<field>` /
 * `strings.<fn>(args)`，页面内 CJK 字面量 0 条。
 *
 * **存储边界（Ruling 25，本页实测为"零写库"**）：整页唯一的写操作是 `:154` 的
 * `app.rewardRepository.initDefaultRewards()`（**无字符串实参**，预设奖励的名称由数据层自己生成），
 * 其余 15 个 `MenuAction(...)` / `SetupRow(..., okText = …, actionLabel = …)` 看着像"字段赋值"，
 * 但 `MenuAction` 是本文件里的**渲染模型**（只被 `HomeMenuCard` 取出去进 `Text()`），
 * `SetupRow` 是 `@Composable`，五个实参全部通向 `Text`，没有任何一条进入 update/insert 或业务判等。
 * `:155` 的 `showSnackbar(...)` 是一次性 UI 提示，不落库。所以本表**不需要** CanonicalParentHomeText：
 * 没有一条迁移的文案进存储。逐条留证见任务报告的"sink 实参来源表"。
 *
 * 同形异义（`分`）：本页 **0 处**。`:114` 的 `${streak}天` 是天数、`:136` 的 `${n} 道` 是题数，
 * 都不是积分也不是分钟；积分概念的出场全是名词性的菜单标题（[menuPointsTitle]、[menuTemplateTitle]），
 * 不接数值，因此不需要 [pointsWord]。仪表盘那一侧的三种 `分` 见 [ParentDashboardStrings]。
 *
 * Ruling 20 的例外（每条都在对应字段 KDoc 里再点一次名）：
 * - [streakDaysValue] / [pendingProofsButton] / [unmasteredMistakesNote] / [syncOverdueTitle]：
 *   英文需要 one/other，而 [Plurals.kt] 本期不改（任务约束"不动 Plurals.kt"），
 *   所以单复数分支写在函数体内（与 ChildHomeStrings 同一处置）；ZH 侧没有分支，逐字节等于页面原文；
 * - [menuWishGoalSubtitle]：`设定存钱罐大目标` 的英文刻意**不**写 "Set a big Wish Pool goal"——
 *   标题已经是 Wish Pool，副标题再重复一次是英文里的废话，改成说明动作的一句
 *   （§11 "Wish Pool 的配套文案规则"要求首次出现要用句子把语义撑起来）；
 * - [overviewCheckin]：`今日打卡` 在英文里是名词性的统计标签，取 "Checked in today"（§11 打卡 =
 *   Check in / Checked in），不复用 [TaskListStrings.checkInTitle]（"🎯 今日打卡"是带 emoji 的卡片标题，
 *   语义是入口名而不是指标名）。
 *
 * 函数型字段的返回值不在 data class `toString()` 里（Ruling 20-b 的盲区），整表 CJK 扫描看不见，
 * 因此 8 条每个都有逐字打出来的双语期望串，见
 * `StringsCopyTest.parentHomeInterpolatedCopyPinsBothLanguages` 与配套的单复数断言；
 * 静态字段 51 条全部进 `StringsCopyTest.parentHomeCopyDiffers` 的 ZH≠EN 穷举清单。
 *
 * 术语按设计文档 §11：积分 = Points、打卡 = Check in / Checked in、连续打卡 = Streak、
 * 家长审核 = Approval、周报 = Weekly Report、奖励 = Reward、任务 = Task、专注 = Focus、
 * 心愿存钱罐/许愿池 = Wish Pool、设备配对 = Pairing。本页新增词条（家长管理台 = Parent console、
 * 快速配置助手 = Quick setup assistant、完成证明 = Completion proofs、积分模板 = Points templates）
 * 已同步补进 §11。
 */
data class ParentHomeStrings(
    // ===== 顶栏与孩子今日概览 =====
    val pageTitle: String,
    val switchRoleContentDescription: String,
    val childTodayOverviewTitle: String,
    val overviewCheckin: String,
    val overviewCompletionRate: String,
    val overviewStreak: String,
    val streakDaysValue: (days: Int) -> String,
    val overviewPendingProofs: String,
    val todayAllDoneBanner: String,
    val pendingProofsButton: (count: Int) -> String,
    val unmasteredMistakesNote: (count: Int) -> String,

    // ===== 载入预设奖励与同步提醒 =====
    val presetRewardsLoadedMessage: String,
    val syncOverdueTitle: (days: Int) -> String,
    val syncOverdueNote: String,

    // ===== 功能菜单（15 个入口 × 标题 + 说明）=====
    val menuTaskManageTitle: String,
    val menuTaskManageSubtitle: String,
    val menuAssignTaskTitle: String,
    val menuAssignTaskSubtitle: String,
    val menuTemplateTitle: String,
    val menuTemplateSubtitle: String,
    val menuPointsTitle: String,
    val menuPointsSubtitle: String,
    val menuRewardStoreTitle: String,
    val menuRewardStoreSubtitle: String,
    val menuWishGoalTitle: String,
    val menuWishGoalSubtitle: String,
    val menuScheduleTitle: String,
    val menuScheduleSubtitle: String,
    val menuProofTitle: String,
    val menuProofSubtitle: String,
    val menuApprovalTitle: String,
    val menuApprovalSubtitle: String,
    val menuMistakeAnalysisTitle: String,
    val menuMistakeAnalysisSubtitle: String,
    val menuCoachingTitle: String,
    val menuCoachingSubtitle: String,
    val menuDashboardTitle: String,
    val menuDashboardSubtitle: String,
    val menuGrowthReportTitle: String,
    val menuGrowthReportSubtitle: String,
    val menuWeeklyReportTitle: String,
    val menuWeeklyReportSubtitle: String,
    val menuSettingsTitle: String,
    val menuSettingsSubtitle: String,

    // ===== 快速配置助手 =====
    val setupAssistantTitle: String,
    val setupAllDoneNote: String,
    val setupIntroNote: String,
    val setupTasksConfigured: (count: Int) -> String,
    val setupTasksMissing: String,
    val setupTasksAction: String,
    val setupRewardsConfigured: (count: Int) -> String,
    val setupRewardsMissing: String,
    val setupRewardsAction: String,
    val setupWishConfigured: (count: Int) -> String,
    val setupWishMissing: String,
    val setupWishAction: String,
    val setupScheduleConfigured: (count: Int) -> String,
    val setupScheduleMissing: String,
    val setupScheduleAction: String,
)

val ParentHomeStringsZh = ParentHomeStrings(
    // ===== 顶栏与孩子今日概览 =====
    pageTitle = "👨‍👩‍👧 家长管理台",                                          // :65
    switchRoleContentDescription = "切换角色",                                // :70
    childTodayOverviewTitle = "📋 孩子今日概览",                               // :105
    overviewCheckin = "今日打卡",                                            // :112
    overviewCompletionRate = "完成率",                                        // :113
    overviewStreak = "连续打卡",                                             // :114
    streakDaysValue = { days -> "${days}天" },                              // :114
    overviewPendingProofs = "待审证明",                                       // :115
    todayAllDoneBanner = "🎉 今日任务全部完成！",                              // :120
    pendingProofsButton = { count -> "📷 有 $count 个证明待审核，点击前往" },     // :130
    unmasteredMistakesNote = { count -> "📚 还有 $count 道错题未掌握" },        // :136

    // ===== 载入预设奖励与同步提醒 =====
    presetRewardsLoadedMessage = "✅ 已载入预设奖励",                          // :155
    syncOverdueTitle = { days -> "设备数据已 $days 天未同步" },                // :184
    syncOverdueNote = "点击去同步，保持家长手机与孩子设备数据一致",               // :187

    // ===== 功能菜单 =====
    menuTaskManageTitle = "任务管理",                                        // :201
    menuTaskManageSubtitle = "创建、编辑学习任务",                             // :201
    menuAssignTaskTitle = "布置今日任务",                                     // :202
    menuAssignTaskSubtitle = "给孩子推送当天任务",                             // :202
    menuTemplateTitle = "积分模板",                                          // :203
    menuTemplateSubtitle = "一键应用预设任务方案",                             // :203
    menuPointsTitle = "积分管理",                                            // :204
    menuPointsSubtitle = "查看记录、手动调整",                                // :204
    menuRewardStoreTitle = "奖励商店",                                        // :205
    menuRewardStoreSubtitle = "设置可兑换的奖励",                              // :205
    menuWishGoalTitle = "心愿目标",                                          // :206
    menuWishGoalSubtitle = "设定存钱罐大目标",                                 // :206
    menuScheduleTitle = "每日日程",                                          // :207
    menuScheduleSubtitle = "设置定时提醒",                                    // :207
    menuProofTitle = "完成证明",                                             // :208
    menuProofSubtitle = "审核孩子提交的证明",                                  // :208
    menuApprovalTitle = "积分审核",                                          // :209
    menuApprovalSubtitle = "审核孩子的积分申请",                               // :209
    menuMistakeAnalysisTitle = "错题分析",                                    // :210
    menuMistakeAnalysisSubtitle = "孩子错题分布与掌握情况",                     // :210
    menuCoachingTitle = "辅导建议",                                          // :211
    menuCoachingSubtitle = "基于数据的科学辅导方法",                            // :211
    menuDashboardTitle = "数据看板",                                          // :212
    menuDashboardSubtitle = "学习数据统计",                                   // :212
    menuGrowthReportTitle = "成长报告",                                       // :213
    menuGrowthReportSubtitle = "汇总成长数据、一键分享",                        // :213
    menuWeeklyReportTitle = "周报总览",                                       // :214
    menuWeeklyReportSubtitle = "每周打卡与积分汇总",                            // :214
    menuSettingsTitle = "家长设置",                                          // :215
    menuSettingsSubtitle = "密码保护与数据同步",                               // :215

    // ===== 快速配置助手 =====
    setupAssistantTitle = "快速配置助手",                                     // :299
    setupAllDoneNote = "🎉 任务、奖励、心愿、日程都已配置完成，可以直接开始使用啦！", // :303
    setupIntroNote = "只需几步即可完成配置，点击右侧按钮快速补齐：",              // :306
    setupTasksConfigured = { count -> "学习任务已配置（${count}个）" },          // :309
    setupTasksMissing = "还没有学习任务",                                     // :309
    setupTasksAction = "去配置",                                             // :310
    setupRewardsConfigured = { count -> "奖励商店已配置（${count}个）" },          // :313
    setupRewardsMissing = "奖励商店还是空的",                                  // :315
    setupRewardsAction = "一键载入",                                         // :315
    setupWishConfigured = { count -> "心愿存钱罐已配置（${count}个）" },          // :318
    setupWishMissing = "还没设定心愿目标",                                    // :318
    setupWishAction = "去设定",                                              // :319
    setupScheduleConfigured = { count -> "每日日程已配置（${count}项）" },        // :321
    setupScheduleMissing = "还没设置每日日程",                                 // :321
    setupScheduleAction = "去设置",                                          // :322
)

val ParentHomeStringsEn = ParentHomeStrings(
    // ===== 顶栏与孩子今日概览 =====
    pageTitle = "👨‍👩‍👧 Parent console",
    switchRoleContentDescription = "Switch role",
    childTodayOverviewTitle = "📋 Your child's day at a glance",
    overviewCheckin = "Checked in today",
    overviewCompletionRate = "Completion",
    overviewStreak = "Streak",
    // Ruling 20：英文有 one/other，Plurals.kt 本期不改，分支写在函数内（ZH 侧无分支）
    streakDaysValue = { days -> if (days == 1) "$days day" else "$days days" },
    overviewPendingProofs = "Proofs to review",
    todayAllDoneBanner = "🎉 Every task today is done!",
    pendingProofsButton = { count ->
        if (count == 1) "📷 1 proof awaits review — tap to open"
        else "📷 $count proofs await review — tap to open"
    },
    unmasteredMistakesNote = { count ->
        if (count == 1) "📚 1 mistake is still not mastered"
        else "📚 $count mistakes are still not mastered"
    },

    // ===== 载入预设奖励与同步提醒 =====
    presetRewardsLoadedMessage = "✅ Preset rewards loaded",
    syncOverdueTitle = { days ->
        if (days == 1) "Device data has not been synced for 1 day"
        else "Device data has not been synced for $days days"
    },
    syncOverdueNote = "Tap to sync and keep the parent phone in step with your child's device",

    // ===== 功能菜单 =====
    menuTaskManageTitle = "Task management",
    menuTaskManageSubtitle = "Create and edit study tasks",
    menuAssignTaskTitle = "Assign today's tasks",
    menuAssignTaskSubtitle = "Push the day's tasks to your child",
    menuTemplateTitle = "Points templates",
    menuTemplateSubtitle = "Apply a preset task plan in one tap",
    menuPointsTitle = "Points management",
    menuPointsSubtitle = "View records and adjust them by hand",
    menuRewardStoreTitle = "Reward Store",
    menuRewardStoreSubtitle = "Set up the rewards your child can trade for",
    menuWishGoalTitle = "Wish Pool",
    menuWishGoalSubtitle = "Set the big goal your child saves toward",
    menuScheduleTitle = "Daily schedule",
    menuScheduleSubtitle = "Set timed reminders",
    menuProofTitle = "Completion proofs",
    menuProofSubtitle = "Review the proofs your child submits",
    menuApprovalTitle = "Approve point requests",
    menuApprovalSubtitle = "Review the point requests your child sent",
    menuMistakeAnalysisTitle = "Mistake analysis",
    menuMistakeAnalysisSubtitle = "Where your child's mistakes are and what they've mastered",
    menuCoachingTitle = "Coaching advice",
    menuCoachingSubtitle = "Science-based coaching that starts from the data",
    menuDashboardTitle = "Data dashboard",
    menuDashboardSubtitle = "Study statistics",
    menuGrowthReportTitle = "Growth report",
    menuGrowthReportSubtitle = "Summarize growth data and share it in one tap",
    menuWeeklyReportTitle = "Weekly reports",
    menuWeeklyReportSubtitle = "Weekly check-ins and points summary",
    menuSettingsTitle = "Parent settings",
    menuSettingsSubtitle = "Password protection and data sync",

    // ===== 快速配置助手 =====
    setupAssistantTitle = "Quick setup assistant",
    setupAllDoneNote = "🎉 Tasks, rewards, wishes and the schedule are all set — you're ready to go!",
    setupIntroNote = "It takes a few steps to finish setting up — use the buttons on the right to fill the gaps:",
    setupTasksConfigured = { count -> "Study tasks set up ($count)" },
    setupTasksMissing = "No study tasks yet",
    setupTasksAction = "Configure",
    setupRewardsConfigured = { count -> "Reward store set up ($count)" },
    setupRewardsMissing = "The reward store is still empty",
    setupRewardsAction = "Load presets",
    setupWishConfigured = { count -> "Wish Pool set up ($count)" },
    setupWishMissing = "No wish goal set yet",
    setupWishAction = "Set a goal",
    setupScheduleConfigured = { count -> "Daily schedule set up ($count)" },
    setupScheduleMissing = "No daily schedule set yet",
    setupScheduleAction = "Set times",
)

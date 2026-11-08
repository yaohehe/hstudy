package com.hstudy.app.i18n

/**
 * 孩子首页（ui/child/ChildHomeScreen.kt）文案表（Task 9，第一阶段最大单页）。
 *
 * 清单口径：`python scripts/i18n_inventory.py child/ChildHomeScreen.kt` 迁移前给出**去重 107 条 / 含插值 20 条**。
 * 本表落成 **102 个字段（其中 17 个函数型）**，与清单的差额是三条去向，逐条可查：
 * - 4 条进存储/幂等路径 → 不在本表，改由 [CanonicalChildHomeText] 持有规范中文（Ruling 25，详见下面那段）；
 * - 1 条（`:567` 的 `+${a.points}分`）复用 [TaskListStrings.pointsEarnedLabel]（中文值逐字节相同、
 *   语义同为"某件事值多少积分"），按"复用不新增"的约定不在本表重开一个字段；
 * - 其余 102 条逐字来自迁移前对应行（行号写在每个 ZH 值的行尾注释里），迁移后页面只剩
 *   `strings.<field>` / `strings.<fn>(args)`，页面内 CJK 字面量 0 条。
 *
 * **存储边界（本页最容易出事的地方，Ruling 25）**：`point_records.note` 与 `checkIn` 的 description
 * 都会落库，而 `data/repository/PointRepository.kt:256` 用 `pending.description != description` 判等——
 * 文案一旦跟着语言变，切语言就等于"业务变更"，历史行会被重写。所以这四条**不进文案表**，
 * 规范值放在 [CanonicalChildHomeText]（`adjustPoints` 三处 + `checkIn` 一处）。
 * `scripts/i18n_storage_boundary.py` 会盯着这类调用，跨行的也算（Task 9 修掉了原来的行级盲区）。
 *
 * 同形异义（`分`）在本页有三类，取值时必须逐处判断，不能整页套一个词：
 * 1. **积分**：`:95` 的"已自动扣减 N 分"、`:320`/`:324` 的"+2 分"、`:401`/`:406`/`:567`/`:591` 的"+N 分"、
 *    `:473`/`:480`/`:501`/`:516` 的"N 积分"、`:802`/`:822` 的"存 N 分/再攒 N 分"——英文一律走
 *    [pointsWord]（术语表 §11 积分 = Points），计数句按 §8.3 做 one/other；
 * 2. **评分单位**：[scoreUnit]（`:290`，紧跟 `${learningScore.total}`）。这是"今日学习表现"的 0–100 得分，
 *    不是积分：`adjustPoints` 走的是另一条账，`LearningScoreCalculator` 的 total 从不入账。
 *    中文与 [CommonStrings.pointsUnit] 同字（都是 `分`），但英文必须分开（`pts` vs `points`），
 *    否则英文态会读出 "87 Points"，把一次评分念成 87 积分——这正是 [CommonStrings] 头注释里
 *    "同形异义必须分槽、且只能有一个出处"要避免的第二份出处；本页不复用 pointsUnit，
 *    由 `StringsCopyTest.childHomeScoreUnitIsNotPoints` 用字面量钉住两侧。
 * 3. **`提升` 同形**：[improvementSuggestionsHeading]（`:304` "💡 提升建议："）里的"提升"是"改进建议"，
 *    不是 [TaskListStrings.filterEnhancement]（任务分类 `提升` = Advanced）。复用会把分类词念成动词。
 *
 * Ruling 20 的四条例外（每条都在对应字段的 KDoc 里再点一次名）：
 * - [tackleFirstHint]：`「」` 在英文侧改成引号、`(` 与难度名之间的间距按英文排（ZH 侧逐字节不变）；
 * - [hardTaskFirstTitle] / [mistakesDueTitle] / [streakMilestoneTitle] / [pendingApprovalSummary] /
 *   [affordableRewardsNote]：英文需要 one/other，[Plurals.kt] 本期不改（任务约束），所以分支写在函数内；
 * - [weeklyChallengeProgressNote]：`5` 是页面里写死的分母，迁移只把文案搬进表，数字仍由页面传入被夹过的值
 *   （`weekCheckinDays.coerceAtMost(5)` 的调用形态不变，Ruling 20(c)）；
 * - [shareProgressChooserTitle]：原文进的是 `FamilySyncHelper.shareFile(filePath, title)`，
 *   实测该方法**没有使用 title 参数**（`data/sync/FamilySyncHelper.kt:249-256` 只读文件后转给
 *   `Platform.fileSharer.shareTextFile`），所以本地化它不会改动任何存储字节；
 *   留中文反而会让 `i18n_inventory` 的残留计数非 0。
 *
 * 函数型字段的返回值不在 data class `toString()` 里（Ruling 20-b 的盲区），整表 CJK 扫描看不见，
 * 因此 17 条每个都有逐字打出来的双语期望串，见 `StringsCopyTest.childHomeInterpolatedCopyPinsBothLanguages`
 * 与配套的同形/内容透传断言；静态字段则全部进 `childHomeCopyDiffers` 的 ZH≠EN 穷举清单。
 *
 * 术语按设计文档 §11：积分 = Points、打卡 = Check in / Checked in、番茄钟 = Pomodoro、
 * 许愿池 = Wish Pool、每日必须任务 = Daily must-do、困难任务 = Hard task、连续打卡 = Streak、
 * 家长审核 = Approval、今日/每日计划 = today's plan。本轮新增五个词条（普通/进阶/挑战 三档难度、
 * 心愿存钱罐、学习表现评分的 pts 单位）已同步补进 §11。
 */
data class ChildHomeStrings(
    // ===== 顶栏与鼓励语 =====
    val pageTitle: String,
    val switchRoleContentDescription: String,
    val encouragementNoTask: String,
    val encouragementFreshStart: String,
    val encouragementAllDone: String,
    val encouragementAlmostThere: String,
    val encouragementOnTrack: String,
    val encouragementKickoff: String,
    val importFailedMessage: (detail: String) -> String,
    val mandatoryPenaltyMessage: (penalty: Int) -> String,

    // ===== 今日打卡进度 =====
    val todayCheckinTitle: String,
    val todayAllDoneBanner: String,

    // ===== 先难后易（吃青蛙）=====
    val difficultyChallengeLevel: String,
    val difficultyAdvancedLevel: String,
    val difficultyNormalLevel: String,
    val hardTaskFirstTitle: (remaining: Int) -> String,
    val tackleFirstHint: (taskTitle: String, stars: String, levelLabel: String) -> String,

    // ===== 今日学习表现 =====
    val learningScoreTitle: String,
    val scoreUnit: String,
    val scoreDimensionCheckin: String,
    val scoreDimensionFocus: String,
    val scoreDimensionReview: String,
    val weakTipCheckin: String,
    val weakTipFocus: String,
    val weakTipReview: String,
    val improvementSuggestionsHeading: String,
    val excellentBonusMessage: String,
    val claimExcellentBonusButton: String,
    val excellentBonusClaimedNote: String,

    // ===== 错题复习提醒 =====
    val mistakesDueTitle: (count: Int) -> String,
    val mistakesDueNote: String,
    val mistakesAllDoneTitle: String,
    val mistakesAllDoneNote: String,

    // ===== 成就与积分申请 =====
    val viewAllAchievementsButton: String,
    val myApprovalRequestsTitle: String,
    val pendingApprovalSummary: (count: Int, points: Int) -> String,
    val approvedRequestLine: (description: String, points: Int) -> String,
    val rejectedRequestLine: (description: String) -> String,

    // ===== 家长同步 =====
    val parentSyncTitle: String,
    val receiveParentSettingsButton: String,
    val sendProgressButton: String,
    val shareProgressChooserTitle: String,
    val progressExportedMessage: String,
    val exportFailedMessage: String,
    val syncingLabel: String,

    // ===== 连续打卡里程碑 =====
    val streakMilestoneTitle: (days: Int) -> String,
    val streakMilestoneBonusNote: (bonus: Int) -> String,
    val streakMilestoneClaimedMessage: (bonus: Int) -> String,
    val claimBonusButton: String,

    // ===== 每周挑战 =====
    val weeklyChallengeTitle: String,
    val weeklyChallengeProgressNote: (days: Int) -> String,
    val weeklyChallengeClaimMessage: String,
    val claimButton: String,
    val weeklyChallengeClaimedNote: String,
    val weeklyChallengeInProgressNote: String,

    // ===== 家长今日布置 =====
    val parentAssignmentsTitle: String,
    val assignmentCompletedMessage: (points: Int) -> String,
    val completeAssignmentButton: String,

    // ===== 4 步学习引导：分区标题 + 功能卡片（标题 / 副标题成对）=====
    val step1Title: String,
    val step1Subtitle: String,
    val entryStartCheckinTitle: String,
    val entryStartCheckinSubtitle: String,
    val entryMyPetTitle: String,
    val entryMyPetSubtitle: String,
    val step2Title: String,
    val step2Subtitle: String,
    val entryPomodoroTitle: String,
    val entryPomodoroSubtitle: String,
    val entryMistakeNotebookTitle: String,
    val entryMistakeNotebookSubtitle: String,
    val step3Title: String,
    val step3Subtitle: String,
    val entryDailyPlanTitle: String,
    val entryDailyPlanSubtitle: String,
    val entryDailyTargetTitle: String,
    val entryDailyTargetSubtitle: String,
    val entryScheduleTimelineTitle: String,
    val entryScheduleTimelineSubtitle: String,
    val entryHabitChallengeTitle: String,
    val entryHabitChallengeSubtitle: String,
    val entryGoalTrackerTitle: String,
    val entryGoalTrackerSubtitle: String,
    val step4Title: String,
    val step4Subtitle: String,
    val entryLuckyDrawTitle: String,
    val entryLuckyDrawSubtitle: String,
    val entryEfficiencyReportTitle: String,
    val entryEfficiencyReportSubtitle: String,
    val entryPointsDetailTitle: String,
    val entryPointsDetailSubtitle: String,
    val entryRewardStoreTitle: String,
    val entryRewardStoreSubtitle: String,
    val entryWishBankTitle: String,
    val entryWishBankSubtitle: String,
    val entryWeeklyReportTitle: String,
    val entryWeeklyReportSubtitle: String,

    // ===== 我的目标（心愿存钱罐 + 下个可兑换奖励）=====
    val myGoalTitle: String,
    val wishGoalNameLabel: (wishName: String) -> String,
    val wishGoalProgressNote: (saved: Int, target: Int, remaining: Int) -> String,
    val affordableRewardsNote: (count: Int) -> String,
    val saveTowardsRewardNote: (need: Int, rewardIcon: String, rewardName: String) -> String,
    val earnPointsForRewardsNote: String,
)

/**
 * 孩子首页里**落库/参与业务判等**的规范中文（Ruling 25，模式与 [CanonicalFocusText] 相同）。
 *
 * 为什么在本期必须由常量而不是文案表承担：这四条传给 `PointRepository.adjustPoints(points, note)` 与
 * `checkIn(bizKey, description)`，而 `PointRepository.kt:256` 拿 description 做幂等比较——
 * 若跟着界面语言走，孩子切一次语言就会被判定成"新的一笔业务"，同一天的奖励行会被重复登记。
 * 值必须与迁移前逐字节相同（`StringsCopyTest.childHomeStorageCopyStaysCanonical` 钉住），
 * 只在渲染时才本地化；英文态里这些历史行仍显示它自己的原文，这条进 Task 12 的人工验证清单。
 *
 * 与 [CanonicalFocusText] 一样**刻意不进文案表**：表值随语言实例走，
 * 存储边界闸门见到 `strings.` 进 sink 就该红，放这里闸门才能继续当硬约束用。
 */
object CanonicalChildHomeText {
    /** `ChildHomeScreen.kt:317` 迁移前传给 `adjustPoints(2, …)` 的原话 */
    const val EXCELLENT_BONUS_NOTE = "今日学习表现优秀奖励"

    /** `ChildHomeScreen.kt:514` 迁移前传给 `adjustPoints(5, …)` 的原话 */
    const val WEEKLY_CHALLENGE_NOTE = "每周挑战奖励：本周打卡5天"

    /** `ChildHomeScreen.kt:478` 迁移前的模板 `连续打卡${days}天里程碑奖励` */
    fun streakMilestoneNote(days: Int): String = "连续打卡${days}天里程碑奖励"

    /** `ChildHomeScreen.kt:586` 迁移前的模板 `完成布置任务: $name`（参与 `checkIn` 的幂等判等） */
    fun assignmentDescription(taskName: String): String = "完成布置任务: $taskName"
}

val ChildHomeStringsZh = ChildHomeStrings(
    // ===== 顶栏与鼓励语 =====
    pageTitle = "🌟 我的学习",                                                // :144
    switchRoleContentDescription = "切换角色",                                // :147
    encouragementNoTask = "还没有任务哦，请爸爸妈妈添加 📝",                    // :123
    encouragementFreshStart = "新的一天，加油打卡吧！💪",                       // :124
    encouragementAllDone = "太棒了！今天全部完成！🎉",                          // :125
    encouragementAlmostThere = "快完成啦，继续加油！🌟",                        // :126
    encouragementOnTrack = "不错不错，保持节奏！👍",                            // :127
    encouragementKickoff = "开始今天的学习之旅吧！🚀",                          // :128
    importFailedMessage = { detail -> "导入失败：$detail" },                     // :50（异常文本原样透传）
    mandatoryPenaltyMessage = { penalty -> "⚠️ 有每日必须任务未完成，已自动扣减 $penalty 分" },  // :95

    // ===== 今日打卡进度 =====
    todayCheckinTitle = "📊 今日打卡",                                        // :219
    todayAllDoneBanner = "🎊 今日任务全部完成！",                              // :234

    // ===== 先难后易 =====
    difficultyChallengeLevel = "挑战",                                        // :249
    difficultyAdvancedLevel = "进阶",                                         // :249
    difficultyNormalLevel = "普通",                                           // :249
    hardTaskFirstTitle = { remaining -> "先难后易 · 还有 $remaining 个困难任务" },   // :258
    tackleFirstHint = { taskTitle, stars, levelLabel ->
        "趁精力充沛，先攻克「$taskTitle」($stars$levelLabel)，剩下的会更轻松！"        // :260
    },

    // ===== 今日学习表现 =====
    learningScoreTitle = "今日学习表现",                                       // :284
    scoreUnit = "分",                                                        // :290（评分单位，非积分）
    scoreDimensionCheckin = "✏️ 打卡",                                        // :293
    scoreDimensionFocus = "🍅 专注",                                          // :294
    scoreDimensionReview = "📚 复习",                                         // :295
    weakTipCheckin = "✏️ 还有任务没完成，加油打卡！",                            // :298
    weakTipFocus = "🍅 专注时间还不够，用番茄钟专注一会儿吧！",                   // :299
    weakTipReview = "📚 还有错题待复习，去复习几道吧！",                         // :300
    improvementSuggestionsHeading = "💡 提升建议：",                            // :304
    excellentBonusMessage = "🌟 学习表现优秀！奖励 +2 分！",                     // :320
    claimExcellentBonusButton = "🌟 领取优秀奖励 +2分",                         // :324
    excellentBonusClaimedNote = "✅ 今日优秀奖励已领取，明天继续加油！",          // :326

    // ===== 错题复习提醒 =====
    mistakesDueTitle = { count -> "今日有 $count 道错题待复习" },               // :355
    mistakesDueNote = "及时复习记得牢，复习还能赚积分哦～",                      // :356
    mistakesAllDoneTitle = "今日待复习错题已全部完成！",                         // :373
    mistakesAllDoneNote = "坚持间隔复习，记忆越来越牢固，真棒！",                  // :375

    // ===== 成就与积分申请 =====
    viewAllAchievementsButton = "🏅 查看全部成就中心 →",                        // :386
    myApprovalRequestsTitle = "📝 我的积分申请",                                // :399
    pendingApprovalSummary = { count, points ->
        "⏳ $count 个申请待家长审核（+$points 分，通过后计入）"                    // :401
    },
    approvedRequestLine = { description, points -> "✅ $description +${points}分" },   // :406
    rejectedRequestLine = { description -> "❌ $description（未通过）" },        // :410

    // ===== 家长同步 =====
    parentSyncTitle = "👨‍👩‍👧 家长同步",                                        // :424
    receiveParentSettingsButton = "📥 接收家长设置",                            // :430
    sendProgressButton = "📤 发送进度给家长",                                   // :449
    shareProgressChooserTitle = "发送学习进度给家长",                            // :438
    progressExportedMessage = "✅ 进度已导出，请发送给家长",                      // :440
    exportFailedMessage = "导出失败",                                          // :442
    syncingLabel = "正在处理…",                                                // :452

    // ===== 连续打卡里程碑 =====
    streakMilestoneTitle = { days -> "连续打卡 $days 天啦！" },                 // :472
    streakMilestoneBonusNote = { bonus -> "达成里程碑，领取 +$bonus 积分奖励！" },   // :473
    streakMilestoneClaimedMessage = { bonus -> "🎉 领取成功！+$bonus 积分" },     // :480
    claimBonusButton = "🎁 领取",                                              // :482

    // ===== 每周挑战 =====
    weeklyChallengeTitle = "每周挑战：本周打卡 5 天",                            // :500
    weeklyChallengeProgressNote = { days -> "进度 $days/5 天 · 达成领 +5 积分" },  // :501
    weeklyChallengeClaimMessage = "🏅 挑战达成！+5 积分",                        // :516
    claimButton = "领取",                                                     // :518
    weeklyChallengeClaimedNote = "✅ 本周已领取",                               // :519
    weeklyChallengeInProgressNote = "加油中…",                                  // :521

    // ===== 家长今日布置 =====
    parentAssignmentsTitle = "📣 家长今日布置",                                 // :549
    assignmentCompletedMessage = { points -> "🎉 完成布置任务 +${points}分（待家长审核）！" },  // :591
    completeAssignmentButton = "完成",                                         // :594

    // ===== 4 步学习引导 =====
    step1Title = "每日打卡",                                                   // :603
    step1Subtitle = "完成今天的学习任务",                                        // :603
    entryStartCheckinTitle = "开始打卡",                                        // :606
    entryStartCheckinSubtitle = "完成任务获得积分",                              // :606
    entryMyPetTitle = "我的学习宠物",                                           // :607
    entryMyPetSubtitle = "赚积分喂养它进化",                                     // :607
    step2Title = "过程管理",                                                   // :614
    step2Subtitle = "专注学习、记录错题、提交证明",                                // :614
    entryPomodoroTitle = "番茄钟",                                             // :617
    entryPomodoroSubtitle = "专注学习计时",                                     // :617
    entryMistakeNotebookTitle = "错题本",                                      // :618
    entryMistakeNotebookSubtitle = "记录和复习错题",                             // :618
    step3Title = "计划规划",                                                   // :625
    step3Subtitle = "安排时间、培养习惯、设定目标",                               // :625
    entryDailyPlanTitle = "每日计划",                                          // :628
    entryDailyPlanSubtitle = "安排学习时间",                                    // :628
    entryDailyTargetTitle = "每日学习目标",                                     // :629
    entryDailyTargetSubtitle = "自己定目标主动学",                               // :629
    entryScheduleTimelineTitle = "今日时间线",                                  // :630
    entryScheduleTimelineSubtitle = "查看今天日程安排",                           // :630
    entryHabitChallengeTitle = "习惯挑战",                                     // :631
    entryHabitChallengeSubtitle = "21天习惯养成",                               // :631
    entryGoalTrackerTitle = "目标追踪",                                        // :632
    entryGoalTrackerSubtitle = "学习进度与目标",                                 // :632
    step4Title = "数据反馈",                                                   // :639
    step4Subtitle = "查看学习分析和积分详情",                                    // :639
    entryLuckyDrawTitle = "幸运抽奖",                                          // :642
    entryLuckyDrawSubtitle = "每日抽奖赢积分",                                  // :642
    entryEfficiencyReportTitle = "效率报表",                                    // :643
    entryEfficiencyReportSubtitle = "学习数据分析",                              // :643
    entryPointsDetailTitle = "积分明细",                                        // :644
    entryPointsDetailSubtitle = "查看积分记录",                                  // :644
    entryRewardStoreTitle = "奖励商店",                                         // :645
    entryRewardStoreSubtitle = "用积分兑换奖励",                                 // :645
    entryWishBankTitle = "心愿存钱罐",                                          // :646
    entryWishBankSubtitle = "攒积分实现大心愿",                                  // :646
    entryWeeklyReportTitle = "周报查看",                                        // :647
    entryWeeklyReportSubtitle = "每周打卡汇总",                                  // :647

    // ===== 我的目标 =====
    myGoalTitle = "我的目标",                                                  // :776
    wishGoalNameLabel = { wishName -> "心愿：$wishName" },                      // :792
    wishGoalProgressNote = { saved, target, remaining ->
        "已存 $saved/$target 分，还差 $remaining 分达成心愿！"                    // :802
    },
    affordableRewardsNote = { count -> "🎉 现在可以兑换 $count 个奖励啦，去商店看看吧！" },  // :816
    saveTowardsRewardNote = { need, rewardIcon, rewardName ->
        "再攒 $need 分就能兑换「$rewardIcon $rewardName」"                        // :822
    },
    earnPointsForRewardsNote = "完成任务赚积分，就能兑换奖励啦！加油💪",            // :830
)

val ChildHomeStringsEn = ChildHomeStrings(
    // ===== 顶栏与鼓励语 =====
    pageTitle = "🌟 My Study",
    switchRoleContentDescription = "Switch role",
    encouragementNoTask = "No tasks yet — ask Mom or Dad to add some 📝",
    encouragementFreshStart = "A brand-new day — go get that check-in! 💪",
    encouragementAllDone = "Amazing! Everything is done for today! 🎉",
    encouragementAlmostThere = "Almost there — keep pushing! 🌟",
    encouragementOnTrack = "Nice pace, keep it going! 👍",
    encouragementKickoff = "Let's start today's learning adventure! 🚀",
    importFailedMessage = { detail -> "Import failed: $detail" },
    mandatoryPenaltyMessage = { penalty ->
        "⚠️ Daily must-do tasks were missed — $penalty ${pointsWord(penalty)} deducted automatically"
    },

    // ===== 今日打卡进度 =====
    todayCheckinTitle = "📊 Today's Check-in",
    todayAllDoneBanner = "🎊 Every task for today is done!",

    // ===== 先难后易 =====
    difficultyChallengeLevel = "Challenge",
    difficultyAdvancedLevel = "Advanced",
    difficultyNormalLevel = "Normal",
    hardTaskFirstTitle = { remaining ->
        "Hard ones first · $remaining hard ${if (remaining == 1) "task" else "tasks"} left"
    },
    tackleFirstHint = { taskTitle, stars, levelLabel ->
        "You're at your best right now — finish \"$taskTitle\" ($stars $levelLabel) first, " +
            "and everything after it feels easier!"
    },

    // ===== 今日学习表现 =====
    learningScoreTitle = "Today's study performance",
    scoreUnit = "pts",
    scoreDimensionCheckin = "✏️ Check-in",
    scoreDimensionFocus = "🍅 Focus",
    scoreDimensionReview = "📚 Review",
    weakTipCheckin = "✏️ Some tasks are still open — finish them and check in!",
    weakTipFocus = "🍅 Not enough focus time yet — run a Pomodoro and settle in!",
    weakTipReview = "📚 A few mistakes are still waiting — go review them!",
    improvementSuggestionsHeading = "💡 How to improve:",
    excellentBonusMessage = "🌟 Great study today! Bonus +2 ${pointsWord(2)}!",
    claimExcellentBonusButton = "🌟 Claim the +2 ${pointsWord(2)} bonus",
    excellentBonusClaimedNote = "✅ Today's bonus is claimed — keep it up tomorrow!",

    // ===== 错题复习提醒 =====
    mistakesDueTitle = { count ->
        "$count ${if (count == 1) "mistake" else "mistakes"} to review today"
    },
    mistakesDueNote = "Reviewing on time makes it stick — and it earns points too!",
    mistakesAllDoneTitle = "All of today's reviews are done!",
    mistakesAllDoneNote = "Keep up the spaced reviews and your memory gets stronger — great job!",

    // ===== 成就与积分申请 =====
    viewAllAchievementsButton = "🏅 See all achievements →",
    myApprovalRequestsTitle = "📝 My point requests",
    pendingApprovalSummary = { count, points ->
        "⏳ $count ${if (count == 1) "request" else "requests"} waiting for parent approval " +
            "(+$points ${pointsWord(points)}, counted once approved)"
    },
    approvedRequestLine = { description, points -> "✅ $description +$points ${pointsWord(points)}" },
    rejectedRequestLine = { description -> "❌ $description (not approved)" },

    // ===== 家长同步 =====
    parentSyncTitle = "👨‍👩‍👧 Parent sync",
    receiveParentSettingsButton = "📥 Receive parent settings",
    sendProgressButton = "📤 Send progress to parent",
    shareProgressChooserTitle = "Send study progress to parent",
    progressExportedMessage = "✅ Progress exported — send it to your parent",
    exportFailedMessage = "Export failed",
    syncingLabel = "Working…",

    // ===== 连续打卡里程碑 =====
    streakMilestoneTitle = { days ->
        "$days ${if (days == 1) "day" else "days"} in a row — what a streak!"
    },
    streakMilestoneBonusNote = { bonus -> "Milestone reached — claim your +$bonus ${pointsWord(bonus)} reward!" },
    streakMilestoneClaimedMessage = { bonus -> "🎉 Claimed! +$bonus ${pointsWord(bonus)}" },
    claimBonusButton = "🎁 Claim",

    // ===== 每周挑战 =====
    weeklyChallengeTitle = "Weekly challenge: check in 5 days",
    weeklyChallengeProgressNote = { days -> "Progress $days/5 days · reach 5 for +5 points" },
    weeklyChallengeClaimMessage = "🏅 Challenge complete! +5 points",
    claimButton = "Claim",
    weeklyChallengeClaimedNote = "✅ Claimed this week",
    weeklyChallengeInProgressNote = "Keep going…",

    // ===== 家长今日布置 =====
    parentAssignmentsTitle = "📣 Set by your parent today",
    assignmentCompletedMessage = { points ->
        "🎉 Assignment done! +$points ${pointsWord(points)} (pending approval)"
    },
    completeAssignmentButton = "Complete",

    // ===== 4 步学习引导 =====
    step1Title = "Daily check-in",
    step1Subtitle = "Finish today's study tasks",
    entryStartCheckinTitle = "Start checking in",
    entryStartCheckinSubtitle = "Complete tasks to earn points",
    entryMyPetTitle = "My study pet",
    entryMyPetSubtitle = "Earn points to feed it and let it grow",
    step2Title = "Study process",
    step2Subtitle = "Focus, log mistakes, submit proof",
    entryPomodoroTitle = "Pomodoro",
    entryPomodoroSubtitle = "Time your focused study",
    entryMistakeNotebookTitle = "Mistake notebook",
    entryMistakeNotebookSubtitle = "Record and review mistakes",
    step3Title = "Planning",
    step3Subtitle = "Schedule time, build habits, set goals",
    entryDailyPlanTitle = "Today's plan",
    entryDailyPlanSubtitle = "Plan your study time",
    entryDailyTargetTitle = "Daily study goal",
    entryDailyTargetSubtitle = "Set your own goal and get started",
    entryScheduleTimelineTitle = "Today's timeline",
    entryScheduleTimelineSubtitle = "See today's schedule",
    entryHabitChallengeTitle = "Habit challenge",
    entryHabitChallengeSubtitle = "Build a habit in 21 days",
    entryGoalTrackerTitle = "Goal tracker",
    entryGoalTrackerSubtitle = "Study progress and goals",
    step4Title = "Progress feedback",
    step4Subtitle = "See your study analysis and points details",
    entryLuckyDrawTitle = "Lucky Draw",
    entryLuckyDrawSubtitle = "Draw every day to win points",
    entryEfficiencyReportTitle = "Efficiency report",
    entryEfficiencyReportSubtitle = "Study data analysis",
    entryPointsDetailTitle = "Points details",
    entryPointsDetailSubtitle = "View your points history",
    entryRewardStoreTitle = "Reward Store",
    entryRewardStoreSubtitle = "Trade points for rewards",
    entryWishBankTitle = "Wish Pool",
    entryWishBankSubtitle = "Save points toward a big wish",
    entryWeeklyReportTitle = "Weekly report",
    entryWeeklyReportSubtitle = "This week's check-in summary",

    // ===== 我的目标 =====
    myGoalTitle = "My goals",
    wishGoalNameLabel = { wishName -> "Wish: $wishName" },
    wishGoalProgressNote = { saved, target, remaining ->
        "$saved/$target ${pointsWord(target)} saved — only $remaining ${pointsWord(remaining)} to go!"
    },
    affordableRewardsNote = { count ->
        "🎉 You can afford $count ${if (count == 1) "reward" else "rewards"} now — take a look in the store!"
    },
    saveTowardsRewardNote = { need, rewardIcon, rewardName ->
        "Save $need more ${pointsWord(need)} to get \"$rewardIcon $rewardName\""
    },
    earnPointsForRewardsNote = "Finish tasks, earn points, and trade them for rewards — you've got it! 💪",
)

package com.hstudy.app.i18n

/**
 * 孩子错题列表页（ui/mistake/ChildMistakeListScreen.kt）文案表（阶段 2 W1）。
 *
 * 来源：迁移前（`4d28c8e`）该文件的状态卡/筛选栏/卡片/提醒开关全部 UI 字面量。
 * **不迁**三条落库原文（Ruling 25）：`间隔复习`（reviewMistake note）、
 * `复习错题：${title}`（submitApproval description）、`连续复习${days}天里程碑奖励`（adjustPoints note）。
 *
 * 字段要点：
 * - [filterAll] 与 [TaskListStringsZh.filterAll] 是同一个中文值「全部」，英文都必须是 "All"，
 *   由 `MistakeCopyTest.crossTableSharedLabelsPinned` 钉住（Task 6 追加）；
 * - [forecastLabels] 是列表字段：等价闸门逐字面量抽取，无需拆成 7 个字段；
 * - 里程碑名与成就注册表（W0）命名对齐：克星 = Mistake Buster、大师 = Mistake Master；
 * - 英文本波统一用 [pointsWord]/[daysWord] 处理 one/other。
 */
data class MistakeListStrings(
    val title: String,
    val masteredBadge: (count: Int) -> String,
    val addContentDescription: String,
    val reviewSuccessMessage: (nextReviewLabel: String) -> String,
    val streakTitle: (days: Int) -> String,
    val streakEncouragementHigh: String,
    val streakEncouragementMid: String,
    val streakEncouragementStart: String,
    val streakEncouragementNone: String,
    val milestoneClaimedMessage: (days: Int, bonus: Int) -> String,
    val milestoneClaimButton: (days: Int, bonus: Int) -> String,
    val calendarTitle: String,
    val calendarSummary: (reviewedDays: Int) -> String,
    val calendarHint: String,
    val milestoneName5: String,
    val milestoneName15: String,
    val milestoneName30: String,
    val milestoneName50: String,
    val milestoneName100: String,
    val masteryProgressTitle: String,
    val masteryNextHint: (need: Int, milestoneName: String) -> String,
    val masteryAllUnlocked: String,
    val forecastTitle: String,
    val forecastLabels: List<String>,
    val weakSubjectTitle: (subjectName: String) -> String,
    val weakSubjectHint: (subjectName: String) -> String,
    val dueBannerTitle: (dueCount: Int) -> String,
    val dueBannerHint: String,
    val reminderTitle: String,
    val reminderHint: String,
    val reminderOnMessage: (hour: Int) -> String,
    val reminderOffMessage: String,
    val reminderTimeLabel: String,
    val reminderHourChip: (hour: Int) -> String,
    val searchPlaceholder: String,
    val clearSearchContentDescription: String,
    val filterAll: String,
    val filterUnmasteredOnly: String,
    val filterDue: (dueCount: Int) -> String,
    val emptyState: String,
    val dueBadge: String,
    val reviewButton: String,
    val markMasteredContentDescription: String,
)

val MistakeListStringsZh = MistakeListStrings(
    title = "📚 错题本",
    masteredBadge = { count -> "已掌握 $count" },
    addContentDescription = "添加错题",
    reviewSuccessMessage = { nextReviewLabel -> "📖 复习完成！+3分已提交家长审核 $nextReviewLabel" },
    streakTitle = { days -> "连续复习 $days 天" },
    streakEncouragementHigh = "太棒了！坚持复习一周以上，记忆超牢固！",
    streakEncouragementMid = "坚持得不错！每天复习，遗忘远离你！",
    streakEncouragementStart = "好的开始！今天也来复习巩固一下吧！",
    streakEncouragementNone = "今天复习错题，开启连续复习 streak 吧！",
    milestoneClaimedMessage = { days, bonus -> "🎉 连续复习${days}天达成！奖励 +${bonus} 分！" },
    milestoneClaimButton = { days, bonus -> "🎁 领取连续复习${days}天奖励 +${bonus}分" },
    calendarTitle = "📅 复习打卡日历",
    calendarSummary = { reviewedDays -> "近28天复习 $reviewedDays 天" },
    calendarHint = "坚持每天复习，让格子都亮起来！",
    milestoneName5 = "错题新手",
    milestoneName15 = "错题进阶",
    milestoneName30 = "错题熟练",
    milestoneName50 = "错题克星",
    milestoneName100 = "错题大师",
    masteryProgressTitle = "🏆 错题清零进度",
    masteryNextHint = { need, milestoneName -> "再掌握 $need 道，即可解锁「$milestoneName」里程碑！" },
    masteryAllUnlocked = "🎉 已解锁全部里程碑，你是真正的错题大师！",
    forecastTitle = "📆 未来7天复习计划",
    forecastLabels = listOf("今天", "明天", "后天", "3天后", "4天后", "5天后", "6天后"),
    weakSubjectTitle = { subjectName -> "重点攻克薄弱科目：$subjectName" },
    weakSubjectHint = { subjectName -> "点击筛选${subjectName}未掌握错题，针对性突破" },
    dueBannerTitle = { dueCount -> "今日有 $dueCount 道错题待复习" },
    dueBannerHint = "按遗忘曲线及时复习，记忆更牢固，点击查看",
    reminderTitle = "每日复习提醒",
    reminderHint = "每天定时提醒你复习错题，不忘复习",
    reminderOnMessage = { hour -> "✅ 已开启每日${hour}点复习提醒" },
    reminderOffMessage = "已关闭复习提醒",
    reminderTimeLabel = "提醒时间",
    reminderHourChip = { hour -> "${hour}点" },
    searchPlaceholder = "搜索错题（题目/错因/解法）",
    clearSearchContentDescription = "清空",
    filterAll = "全部",
    filterUnmasteredOnly = "仅未掌握",
    filterDue = { dueCount -> "今日待复习${if (dueCount > 0) " $dueCount" else ""}" },
    emptyState = "还没有错题记录，点击下方 + 添加",
    dueBadge = "🔔待复习",
    reviewButton = "复习+3",
    markMasteredContentDescription = "标记掌握",
)

val MistakeListStringsEn = MistakeListStrings(
    title = "📚 Mistakes",
    masteredBadge = { count -> "Mastered: $count" },
    addContentDescription = "Add mistake",
    reviewSuccessMessage = { nextReviewLabel ->
        "📖 Review complete! +3 points submitted for parent approval. $nextReviewLabel"
    },
    streakTitle = { days -> "Review streak: $days ${daysWord(days)}" },
    streakEncouragementHigh = "Amazing! Over a week of daily review — your memory is getting rock solid!",
    streakEncouragementMid = "Nice going! Review every day and forgetfulness stays away!",
    streakEncouragementStart = "A good start! Review again today to reinforce what you know!",
    streakEncouragementNone = "Review a mistake today to start your review streak!",
    milestoneClaimedMessage = { days, bonus ->
        "🎉 $days-day review streak reached! Reward: +$bonus ${pointsWord(bonus)}!"
    },
    milestoneClaimButton = { days, bonus ->
        "🎁 Claim $days-day streak reward +$bonus ${pointsWord(bonus)}"
    },
    calendarTitle = "📅 Review calendar",
    calendarSummary = { reviewedDays -> "Reviewed $reviewedDays ${daysWord(reviewedDays)} in the last 28 days" },
    calendarHint = "Review every day and light up every square!",
    milestoneName5 = "Mistake Rookie",
    milestoneName15 = "Mistake Improver",
    milestoneName30 = "Mistake Skilled",
    milestoneName50 = "Mistake Buster",
    milestoneName100 = "Mistake Master",
    masteryProgressTitle = "🏆 Mistake-clearing progress",
    masteryNextHint = { need, milestoneName ->
        "Master $need more to unlock the “$milestoneName” milestone!"
    },
    masteryAllUnlocked = "🎉 All milestones unlocked — you are a true Mistake Master!",
    forecastTitle = "📆 7-day review plan",
    forecastLabels = listOf("Today", "Tomorrow", "In 2 days", "In 3 days", "In 4 days", "In 5 days", "In 6 days"),
    weakSubjectTitle = { subjectName -> "Focus on your weakest subject: $subjectName" },
    weakSubjectHint = { subjectName ->
        "Tap to filter unmastered $subjectName mistakes and target your weak spots"
    },
    dueBannerTitle = { dueCount -> "Due for review today: $dueCount" },
    dueBannerHint = "Review on the forgetting curve to remember longer — tap to view",
    reminderTitle = "Daily review reminder",
    reminderHint = "A daily reminder so review never slips your mind",
    reminderOnMessage = { hour -> "✅ Daily review reminder on at $hour:00" },
    reminderOffMessage = "Review reminder turned off",
    reminderTimeLabel = "Reminder time",
    reminderHourChip = { hour -> "$hour:00" },
    searchPlaceholder = "Search mistakes (title / reason / solution)",
    clearSearchContentDescription = "Clear",
    filterAll = "All",
    filterUnmasteredOnly = "Unmastered only",
    filterDue = { dueCount -> "Due today${if (dueCount > 0) " $dueCount" else ""}" },
    emptyState = "No mistakes yet — tap + below to add one",
    dueBadge = "🔔 Due",
    reviewButton = "Review +3",
    markMasteredContentDescription = "Mark as mastered",
)

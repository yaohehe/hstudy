package com.hstudy.app.i18n

/**
 * 孩子端习惯挑战页（ui/habit/ChildHabitChallengeScreen.kt）文案表（阶段 2 W5）。
 *
 * [challengeCompleteMessage]/[checkInSuccessMessage] 的挑战标题/描述是**用户内容**，原样传入。
 * **落库字面量**（Ruling 25，保持 canonical 原样、T7 登记披露）：
 * `checkIn(description = "习惯打卡: ${challenge.title}")` 与
 * `tryUnlockAchievement("habit_complete", "习惯达人", "完成一个21天挑战", "🏆")`。
 * 「取消」复用 [CommonStrings.cancelButton]。[daysProgress] 英文单复数用行内条件。
 */
data class ChildHabitChallengeStrings(
    val title: String,
    val newChallengeContentDescription: String,
    val emptyMessage: String,
    val activeSectionTitle: String,
    val alreadyCheckedInMessage: String,
    val challengeCompleteMessage: (title: String, points: Int) -> String,
    val checkInSuccessMessage: (points: Int, done: Int, total: Int) -> String,
    val challengeCreated: String,
    val completedSectionTitle: String,
    val daysProgress: (done: Int, total: Int) -> String,
    val checkedInToday: String,
    val checkInButton: String,
    val newChallengeDialogTitle: String,
    val nameLabel: String,
    val descLabel: String,
    val daysLabel: String,
    val daysOption: (days: Int) -> String,
    val iconLabel: String,
    val createButton: String,
)

val ChildHabitChallengeStringsZh = ChildHabitChallengeStrings(
    title = "🏆 习惯挑战",                                                      // :46
    newChallengeContentDescription = "新建挑战",                                 // :49
    emptyMessage = "开始你的21天习惯挑战吧！",                                    // :59
    activeSectionTitle = "🔥 进行中的挑战",                                      // :69
    alreadyCheckedInMessage = "😊 今天已经打过卡啦，明天再来！",                   // :79
    challengeCompleteMessage = { title, points -> "🎉 恭喜完成挑战: $title! +${points}分待家长审核" }, // :92
    checkInSuccessMessage = { points, done, total -> "✅ 打卡成功 +${points}分（待家长审核）($done/$total)" }, // :107
    challengeCreated = "🎯 挑战已创建！",                                        // :144
    completedSectionTitle = "✅ 已完成的挑战",                                   // :122
    daysProgress = { done, total -> "$done / $total 天" },                       // :193
    checkedInToday = "✅ 今日已打卡",                                            // :199
    checkInButton = "打卡",                                                     // :203
    newChallengeDialogTitle = "🎯 新建习惯挑战",                                  // :232
    nameLabel = "挑战名称",                                                     // :239
    descLabel = "描述（可选）",                                                  // :241
    daysLabel = "天数: ",                                                       // :242
    daysOption = { days -> "${days}天" },                                       // :245
    iconLabel = "图标:",                                                        // :248
    createButton = "创建",                                                      // :257
)

val ChildHabitChallengeStringsEn = ChildHabitChallengeStrings(
    title = "🏆 Habit Challenge",
    newChallengeContentDescription = "New challenge",
    emptyMessage = "Start your 21-day habit challenge!",
    activeSectionTitle = "🔥 Active challenges",
    alreadyCheckedInMessage = "😊 You've already checked in today — come back tomorrow!",
    challengeCompleteMessage = { title, points -> "🎉 Challenge complete: $title! +$points ${pointsWord(points)} awaiting parent approval" },
    checkInSuccessMessage = { points, done, total -> "✅ Checked in +$points ${pointsWord(points)} (awaiting parent approval) ($done/$total)" },
    challengeCreated = "🎯 Challenge created!",
    completedSectionTitle = "✅ Completed challenges",
    daysProgress = { done, total -> "$done / $total ${if (total == 1) "day" else "days"}" },
    checkedInToday = "✅ Checked in today",
    checkInButton = "Check in",
    newChallengeDialogTitle = "🎯 New habit challenge",
    nameLabel = "Challenge name",
    descLabel = "Description (optional)",
    daysLabel = "Days: ",
    daysOption = { days -> "$days days" },
    iconLabel = "Icon:",
    createButton = "Create",
)

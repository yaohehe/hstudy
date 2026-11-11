package com.hstudy.app.i18n

/**
 * 孩子端每日幸运抽奖页（ui/child/ChildLuckyDrawScreen.kt）文案表（阶段 2 W4）。
 *
 * 数量词一律 [pointsWord]。**落库字面量**：`adjustPoints(won, "每日幸运抽奖")` 的 note 是账本数据，
 * 保持 canonical 中文原样、不进本表（等价闸门 Task 5 登记披露"每日幸运抽奖"）。
 */
data class ChildLuckyDrawStrings(
    val title: String,
    val headline: String,
    val subtitle: String,
    val pointsWonValue: (points: Int) -> String,
    val winMessage: (points: Int) -> String,
    val alreadyDrawn: String,
    val drawSnackbar: (points: Int) -> String,
    val drawing: String,
    val drawButton: String,
    val comeTomorrow: String,
    val infoTitle: String,
    val infoPool: String,
    val infoStreak: String,
)

val ChildLuckyDrawStringsZh = ChildLuckyDrawStrings(
    title = "🎰 每日幸运抽奖",                                                  // :63
    headline = "试试你的手气！",                                                // :72
    subtitle = "每天可抽奖一次，随机获得奖励积分",                                // :73
    pointsWonValue = { points -> "+$points 分" },                              // :86
    winMessage = { points -> "恭喜获得 $points 积分奖励！" },                    // :96
    alreadyDrawn = "今天已经抽过奖啦，明天再来吧～",                              // :99
    drawSnackbar = { points -> "🎉 抽中 $points 积分！" },                      // :117
    drawing = "抽奖中…",                                                       // :124
    drawButton = "🎲 立即抽奖",                                                 // :124
    comeTomorrow = "明日再来",                                                  // :124
    infoTitle = "💡 奖励说明",                                                  // :135
    infoPool = "奖池包含 1~5 积分，小奖励更容易抽中，大奖凭运气！",                // :136
    infoStreak = "坚持每天打卡后再来抽奖，养成好习惯～",                          // :138
)

val ChildLuckyDrawStringsEn = ChildLuckyDrawStrings(
    title = "🎰 Daily Lucky Draw",
    headline = "Try your luck!",
    subtitle = "One draw per day — win bonus points at random",
    pointsWonValue = { points -> "+$points ${pointsWord(points)}" },
    winMessage = { points -> "Congratulations — you won $points ${pointsWord(points)}!" },
    alreadyDrawn = "You've already drawn today — come back tomorrow!",
    drawSnackbar = { points -> "🎉 You won $points ${pointsWord(points)}!" },
    drawing = "Drawing…",
    drawButton = "🎲 Draw now",
    comeTomorrow = "Come back tomorrow",
    infoTitle = "💡 Prize info",
    infoPool = "The prize pool holds 1–5 points — small prizes are more likely, and big wins are pure luck!",
    infoStreak = "Check in every day, then come draw — build a good habit!",
)

package com.hstudy.app.i18n

/**
 * 学习效率报表页（`ui/habit/ChildEfficiencyReportScreen.kt`）文案表（阶段 2 W8 T3）。
 *
 * 覆盖面 = 迁移前页面的全部 CJK 代码字面量（85 条去重后落成 **55 个静态字段 + 13 个函数型字段**）：
 * 标题/三张统计卡/周月对比标题与四句评语×2/四个指标行的值+单位/学科分析/错因分析（5 条建议+兜底）/
 * 学习建议（5 条函数型 + 3 条静态 + 兜底）/7 日趋势/专注洞察/专注投入分布/两条折线图/时段名 6 + 时段函数。
 *
 * **不进本表的 canonical 值**：五个学科名（数学/语文/英语/科学/其他）与五种错因名（粗心马虎/
 * 概念不清/方法不会/审题错误/计算错误）是**存储值**，页面对它们只包 [localizedPresetText] 取词
 * （注册表已有两组同值键，W5/W8 T2 落地），因此没有逐科/逐错因的字段——那两个 canonical 列表
 * 继续以字面量留在页面，`W8CopyTest.efficiencyReportCopyDiffers` 钉住注册表两语命中。
 *
 * ZH 值逐字节等于迁移前页面的内插模板（空格、量词、全角括号原样保留）；函数型字段的英文由
 * `W8CopyTest.efficiencyReportInterpolatedPinsBothLanguages` 逐条两语钉死（Ruling 20-b：
 * data class 的 `toString()` 看不见 lambda 返回值，整表扫描对它们无效）。
 * 单位同形异义：[focusUnit]（分钟）与 [scoreUnit]（评分 pts）在中文都是「分」，英文必须分开。
 */
data class EfficiencyReportStrings(
    val title: String,
    val statPointsLabel: String,
    val statFocusLabel: String,
    val statCheckinLabel: String,
    val weekTitle: String,
    val weekVerdictNew: String,
    val weekVerdictUp: String,
    val weekVerdictFlat: String,
    val weekVerdictDown: String,
    val focusMetricLabel: String,
    val checkinMetricLabel: String,
    val reviewMetricLabel: String,
    val avgScoreLabel: String,
    val focusValue: (n: Int) -> String,
    val focusUnit: String,
    val checkinValue: (n: Int) -> String,
    val checkinUnit: String,
    val reviewValue: (n: Int) -> String,
    val reviewUnit: String,
    val scoreValue: (n: Int) -> String,
    val scoreUnit: String,
    val monthTitle: String,
    val monthVerdictNew: String,
    val monthVerdictUp: String,
    val monthVerdictFlat: String,
    val monthVerdictDown: String,
    val subjectTitle: String,
    val subjectSubtitle: String,
    val errorTitle: String,
    val errorSubtitle: String,
    val errorAdviceCareless: String,
    val errorAdviceConcepts: String,
    val errorAdviceMethod: String,
    val errorAdviceReading: String,
    val errorAdviceCalculation: String,
    val errorAdviceFallback: String,
    val suggestionsTitle: String,
    val scoreTrendTitle: String,
    val scoreTrendEmpty: String,
    val scoreTrendAverage: (avgScore: Int) -> String,
    val focusInsightTitle: String,
    val bestPeriodLabel: String,
    val avgSessionLabel: String,
    val focusCountLabel: String,
    val bestPeriodHint: (period: String) -> String,
    val freeStudy: String,
    val distributionTitle: String,
    val distributionSubtitle: String,
    val distributionTopHint: (target: String, minutes: Int) -> String,
    val focusTrendTitle: String,
    val noData: String,
    val checkinTrendTitle: String,
    val deltaFlat: String,
    val periodEarlyMorning: String,
    val periodMorning: String,
    val periodNoon: String,
    val periodAfternoon: String,
    val periodEvening: String,
    val periodLateNight: String,
    val hourLabel: (period: String, hour: Int) -> String,
    val suggestionWeakSubject: (name: String, rate: Int) -> String,
    val suggestionDueReview: (count: Int) -> String,
    val suggestionUnmastered: (count: Int) -> String,
    val suggestionPomodoro: String,
    val suggestionFocusGood: (totalFocusMin: Int) -> String,
    val suggestionBestPeriod: (period: String) -> String,
    val suggestionMastered: String,
    val suggestionEmpty: String,
)

val EfficiencyReportStringsZh = EfficiencyReportStrings(
    title = "📊 学习效率报表",                                              // :129
    statPointsLabel = "累计积分",                                          // :144
    statFocusLabel = "累计专注",                                           // :145
    statCheckinLabel = "打卡次数",                                         // :146
    weekTitle = "📅 本周 vs 上周",                                        // :153
    weekVerdictNew = "🌱 新的开始，继续加油！",                             // :155
    weekVerdictUp = "📈 比上周进步了，继续保持！",                          // :156
    weekVerdictFlat = "➡️ 和上周持平，稳定发挥！",                          // :157
    weekVerdictDown = "💪 比上周略有下降，加把劲追回来！",                  // :158
    focusMetricLabel = "🍅 专注时长",                                      // :162
    checkinMetricLabel = "✏️ 打卡次数",                                    // :163
    reviewMetricLabel = "📚 复习错题",                                     // :164
    avgScoreLabel = "⭐ 平均评分",                                         // :165
    focusValue = { n -> "${n}分" },                                        // :162
    focusUnit = "分",                                                      // :162（增减行单位，分钟）
    checkinValue = { n -> "${n}次" },                                      // :163
    checkinUnit = "次",                                                    // :163
    reviewValue = { n -> "${n}道" },                                       // :164
    reviewUnit = "道",                                                     // :164
    scoreValue = { n -> "${n}分" },                                        // :165
    scoreUnit = "分",                                                      // :165（评分单位，非积分）
    monthTitle = "🗓️ 本月 vs 上月",                                       // :173
    monthVerdictNew = "🌱 本月是新的开始，继续加油！",                       // :175
    monthVerdictUp = "📈 本月比上月进步了，太棒了！",                        // :176
    monthVerdictFlat = "➡️ 本月和上月持平，稳定发挥！",                      // :177
    monthVerdictDown = "💪 本月比上月略有下降，下个月加把劲！",              // :178
    subjectTitle = "📊 学科掌握度分析",                                     // :199
    subjectSubtitle = "根据错题掌握情况，找出需要重点突破的科目",             // :200
    errorTitle = "🔍 错因分析",                                            // :236
    errorSubtitle = "分析错误类型分布，找到根本原因针对性改进",               // :237
    errorAdviceCareless = "💡 你最常因「粗心马虎」出错！建议：做完后回头检查一遍，圈出关键词，放慢审题速度。",     // :250
    errorAdviceConcepts = "💡 你最常因「概念不清」出错！建议：回归课本把基础概念弄懂，多问为什么，别急着刷题。",   // :251
    errorAdviceMethod = "💡 你最常因「方法不会」出错！建议：总结同类题的解题套路，看例题学习标准方法。",          // :252
    errorAdviceReading = "💡 你最常因「审题错误」出错！建议：读题时圈出条件和问题，看清要求再动笔。",            // :253
    errorAdviceCalculation = "💡 你最常因「计算错误」出错！建议：草稿纸写工整，关键步骤验算一遍。",              // :254
    errorAdviceFallback = "💡 针对常见错误类型，制定专门的改进计划吧！",                                      // :255
    suggestionsTitle = "💡 学习方法建议",                                   // :269
    scoreTrendTitle = "📈 学习表现7日趋势",                                 // :281
    scoreTrendEmpty = "暂无数据，开始学习后这里会显示你的进步轨迹",           // :284
    scoreTrendAverage = { avgScore -> "7日平均 $avgScore 分" },            // :308
    focusInsightTitle = "⏰ 专注效率洞察",                                   // :319
    bestPeriodLabel = "黄金专注时段",                                       // :324
    avgSessionLabel = "平均每次专注",                                       // :325
    focusCountLabel = "专注次数",                                          // :326
    bestPeriodHint = { period -> "💡 你在 $period 专注力最好，建议把难题和重要学习安排在这个时段。" },             // :329
    freeStudy = "自由学习",                                                // :338
    distributionTitle = "📊 专注投入分布",                                  // :346
    distributionSubtitle = "按专注目标统计时间投入，看看精力都花在了哪里",     // :347
    distributionTopHint = { target, minutes -> "💡 你在「$target」上投入最多（${minutes}分钟），注意各科均衡分配哦。" }, // :363
    focusTrendTitle = "📈 专注时长趋势 (14天)",                             // :373
    noData = "暂无数据",                                                   // :378 / :433
    checkinTrendTitle = "✅ 打卡完成率趋势 (14天)",                          // :427
    deltaFlat = "→ 持平",                                                  // :499
    periodEarlyMorning = "早上",                                           // :510
    periodMorning = "上午",                                                // :511
    periodNoon = "中午",                                                   // :512
    periodAfternoon = "下午",                                              // :513
    periodEvening = "晚上",                                                // :514
    periodLateNight = "深夜",                                              // :515
    hourLabel = { period, hour -> "$period${hour}点" },                    // :517
    suggestionWeakSubject = { name, rate -> "${name}是你的薄弱科目（掌握率${rate}%），建议多花时间复习${name}错题。" }, // :548
    suggestionDueReview = { count -> "有 $count 道错题到了复习时间，按遗忘曲线及时复习，记忆保持率可提升一倍以上。" },   // :554
    suggestionUnmastered = { count -> "还有 $count 道错题未掌握，建议每天攻克 2-3 道，逐步清零。" },                    // :560
    suggestionPomodoro = "试试用番茄钟专注学习（25分钟专注+5分钟休息），专注效率会更高。",                              // :565
    suggestionFocusGood = { totalFocusMin -> "累计专注 ${totalFocusMin} 分钟，保持专注是高效学习的关键，继续加油！" },  // :567
    suggestionBestPeriod = { period -> "你在 $period 专注力最好，把难题和重要科目安排在这个时段，效率更高。" },          // :572
    suggestionMastered = "大部分错题都已掌握，你的复习方法很有效，继续保持！",                                          // :577
    suggestionEmpty = "开始记录错题并坚持复习，这里会为你生成个性化学习建议。",                                          // :581
)

val EfficiencyReportStringsEn = EfficiencyReportStrings(
    title = "📊 Learning efficiency report",
    statPointsLabel = "Total points earned",
    statFocusLabel = "Total focus time",
    statCheckinLabel = "Check-in count",
    weekTitle = "📅 This week vs last week",
    weekVerdictNew = "🌱 A fresh start — keep going!",
    weekVerdictUp = "📈 Up from last week — keep it up!",
    weekVerdictFlat = "➡️ On par with last week — steady!",
    weekVerdictDown = "💪 Slightly down from last week — push to catch up!",
    focusMetricLabel = "🍅 Focus time",
    checkinMetricLabel = "✏️ Check-ins",
    reviewMetricLabel = "📚 Mistakes reviewed",
    avgScoreLabel = "⭐ Average score",
    focusValue = { n -> "$n min" },
    focusUnit = "min",
    checkinValue = { n -> "$n times" },
    checkinUnit = "times",
    reviewValue = { n -> "$n" },
    reviewUnit = "mistakes",
    scoreValue = { n -> "$n pts" },
    scoreUnit = "pts",
    monthTitle = "🗓️ This month vs last month",
    monthVerdictNew = "🌱 A fresh month — keep going!",
    monthVerdictUp = "📈 Up from last month — wonderful!",
    monthVerdictFlat = "➡️ On par with last month — steady!",
    monthVerdictDown = "💪 Slightly down from last month — push next month!",
    subjectTitle = "📊 Subject mastery analysis",
    subjectSubtitle = "Find the subjects that need attention from mistake mastery",
    errorTitle = "🔍 Error cause analysis",
    errorSubtitle = "Analyze error-type distribution to find root causes and improve",
    errorAdviceCareless = "💡 Your most common error is “carelessness”! Suggestion: check your work afterwards, circle key words, and slow down when reading.",
    errorAdviceConcepts = "💡 Your most common error is “unclear concepts”! Suggestion: go back to the textbook, understand the basics, ask why, and don't rush to drill.",
    errorAdviceMethod = "💡 Your most common error is “unknown method”! Suggestion: summarize patterns for similar problems and study worked examples.",
    errorAdviceReading = "💡 Your most common error is “misreading the question”! Suggestion: circle conditions and questions, and make sure of requirements before writing.",
    errorAdviceCalculation = "💡 Your most common error is “calculation errors”! Suggestion: keep scratch work neat and verify key steps.",
    errorAdviceFallback = "💡 Make a dedicated improvement plan for common error types.",
    suggestionsTitle = "💡 Study method suggestions",
    scoreTrendTitle = "📈 7-day performance trend",
    scoreTrendEmpty = "No data yet — your progress will appear here once you start learning",
    scoreTrendAverage = { avgScore -> "7-day average: $avgScore pts" },
    focusInsightTitle = "⏰ Focus efficiency insights",
    bestPeriodLabel = "Golden focus period",
    avgSessionLabel = "Average per session",
    focusCountLabel = "Focus sessions",
    bestPeriodHint = { period -> "💡 You focus best during $period — schedule hard problems and important study then." },
    freeStudy = "Free study",
    distributionTitle = "📊 Focus time distribution",
    distributionSubtitle = "Time invested per focus target — see where your energy goes",
    distributionTopHint = { target, minutes -> "💡 You invested the most in “$target” ($minutes min) — keep the balance across subjects." },
    focusTrendTitle = "📈 Focus time trend (14 days)",
    noData = "No data yet",
    checkinTrendTitle = "✅ Check-in rate trend (14 days)",
    deltaFlat = "→ Flat",
    periodEarlyMorning = "Morning",
    periodMorning = "Late morning",
    periodNoon = "Noon",
    periodAfternoon = "Afternoon",
    periodEvening = "Evening",
    periodLateNight = "Late night",
    hourLabel = { period, hour -> "$period $hour:00" },
    suggestionWeakSubject = { name, rate -> "$name is a weak subject (mastery $rate%) — spend more time reviewing $name mistakes." },
    suggestionDueReview = { count -> "$count mistakes are due for review — reviewing on the forgetting curve can more than double retention." },
    suggestionUnmastered = { count -> "$count mistakes are still unmastered — clear 2–3 a day to work down to zero." },
    suggestionPomodoro = "Try studying with Pomodoro (25 min focus + 5 min break) for higher focus efficiency.",
    suggestionFocusGood = { totalFocusMin -> "$totalFocusMin minutes of total focus — staying focused is the key to efficient study. Keep going!" },
    suggestionBestPeriod = { period -> "You focus best during $period — schedule hard problems and key subjects then for higher efficiency." },
    suggestionMastered = "Most mistakes are mastered — your review method works. Keep it up!",
    suggestionEmpty = "Start recording mistakes and reviewing regularly — personalized study advice will appear here.",
)

package com.hstudy.app.i18n

/**
 * 家长数据看板（ui/parent/ParentDashboardScreen.kt）文案表（Task 10）。
 *
 * 清单口径：`python scripts/i18n_inventory.py parent/ParentDashboardScreen.kt` 迁移前给出**去重 36 条 /
 * 含插值 6 条**。本表落成 **36 个字段（其中 6 个函数型）**，与清单的差额是**一进一出**两条，逐条可查：
 * - **出去一条**：`:171` 传给 `ParentCompareRow(…, unit)` 的 `"分"` 是**分钟**，与
 *   [CommonStrings.minuteLabel] 同值同义，按"复用不新增"的约定直接取 [AppStrings.common]，
 *   不在本表重开一个 `分` 槽（理由与 `PomodoroStrings.durationChip` 完全一样：同一个中文值有两处
 *   出处就会漂移）。这条复用由 `StringsCopyTest.parentDashboardDurationUnitComesFromCommon` 钉住，
 *   并在 `scripts/i18n_template_diff.py` 的 DISCLOSED_RESIDUALS 里登记为**页面单边**残差；
 * - **进来一条**：`:142` 的 `"${childScore.total}分"` 一条清单拆成 [scoreValue]（数值位）+
 *   [scoreUnit]（评分单位位）两个字段，页面写 `strings.scoreValue(total, strings.scoreUnit)`。
 *   拆的理由是这枚 `分` 与上面那枚 `分` **不同义**（评分 pts vs 分钟 min），
 *   留在同一条模板里就等于让两个槽共用一个写法、英文侧的间距也没地方放；
 *   登记为**表侧单边**残差（塌成 `⟨$⟩⟨$⟩` 后页面侧不再含汉字，见脚本注释例外①同一类）。
 * 其余 34 条逐字来自迁移前对应行（行号在行尾注释里），迁移后两页页面内 CJK 字面量各 0 条。
 *
 * **存储边界（Ruling 25，本页实测为"只读页"**）：整页 0 个写库调用——所有 `app.*Repository` 出场都是
 * 读流（`get` 前缀配 `collectAsState`），没有 `adjustPoints` / `spendPoints` / `assignTask`
 * 也没有 DAO 的 `insert` 与 `update`，
 * 连 `showSnackbar` 都没有。所以本表**不需要** `Canonical…` 规范值对象（[CanonicalChildHomeText] 那一族），
 * 也没有参与幂等判等的值。四类闸门盲区（helper 返回值 / 字段赋值 / 无关键字再赋值 / 跨行与三引号）的逐条审结见 `docs/i18n-stage1-verification.md` §5（任务报告随分支作废，plan Ruling 35）。
 *
 * 本表要交代两处**跨文件聚合**进来的中文，它们不属于本页清单、本任务也不迁：
 * - [childPerformanceLine] 的第一个实参 `grade` 出自 `ui/common/LearningScoreCalculator.kt:36-42`
 *   （`优秀/良好/不错/起步/待开始`），同页 [scoreValue] 旁边渲染的 `childScore.total` 是数字、不受影响。
 *   英文态这一格会读出 "优秀 · Check-in 3/5 …"，是**已知未迁完**而不是本表写错：
 *   `LearningScoreCalculator` 是全仓共用计算器（`ChildHomeScreen`、`ChildEfficiencyReportScreen`
 *   也在调），按计划属 Task 11/12 之后的收尾，已进 Task 12 人工验证清单；
 * - `childScore.encouragement`（`:145` 的 `💡 ${…}`）同族同因，页面只是把它前面拼一枚 💡，
 *   迁移没有把它挪进表（那会改动数据源，brief 明令禁止）。
 * 这两条**都不进存储**（`DailyLearningScore` 是 UI 模型，`data` 包里没有任何字段接它），
 * 所以本地化它们没有幂等风险，但也正因为它们不是本表字段，整表 CJK 扫描与模板等价脚本都看不见它们。
 *
 * 同形异义（`分`）在本页**三种语义同时出现**，逐处判断、各占一槽：
 * 1. **分钟（时长）**：[childPerformanceLine] 里的 `专注${m}分`、[focusMinutesValue]（`⟨$⟩分`）、
 *    [weakAreaFocusNote] 的 `${m}分钟`，以及页面 `:171` 第 4 个实参 `"分"`（复用 [CommonStrings.minuteLabel]）
 *    ——英文全部走 min / 分钟，**不得**出现 points；
 * 2. **评分单位**：[scoreUnit]（`:142` 里紧跟 `${childScore.total}` 的 0–100 得分，由 [scoreValue] 拼接）
 *    → 英文 `pts`，
 *    与 [ChildHomeStrings.scoreUnit] 同一个概念、同一个词形，`assertDiffers` 对"两处评分单位互换"是瞎的，
 *    所以由 `StringsCopyTest.parentDashboardScoreUnitIsNotPoints` 逐字钉住两张表；
 * 3. **积分**：本页的积分概念全部以名词词组出场（"当前总积分""⭐ 获得积分""最近积分变动"），
 *    **没有**一处写成"N分"，因此本表不需要 [pointsWord]，也就没有单复数分支。
 * 另有一种更隐蔽的同形：[reviewCountUnit]（`道`）与 [mistakesReviewedValue] 里的 `道` 是题数量词，
 * 中文里 `3道` 与 `3分` 只差一个字，实参混用会静默走错——两处都写成了显式字段名与显式实参。
 *
 * Ruling 20 的例外（每条都在对应字段 KDoc 里再点一次名）：
 * - [mistakesReviewedValue] / [reviewCountUnit]：`道` 是中文量词，英文没有对应词，
 *   照 ChildHomeStrings `今日有 3 道错题待复习` → `3 mistakes to review today` 的既有处置**整词删掉**，
 *   于是英文值分别是 `"$n"` 与 `""`（空串不是漏译，是"英文不需要量词"的诚实表达，
 *   由 `parentDashboardEnglishDropsTheMeasureWord` 断言并解释）；ZH 侧逐字节等于页面原文；
 * - [childPerformanceLine]：五个实参都是**内容位**（等级名 / 完成数 / 总数 / 分钟 / 题数），
 *   参数显式命名、取数逻辑仍全部留在页面（`todayCompletedIds.size`、`tasks.size`、`todayFocusMin`、
 *   `todayReviewCount`、`childScore.grade`），本表只搬句式；
 * - [scoreUnit]：`pts` 与 [ChildHomeStrings.scoreUnit] 是两张表各存一份同一个术语，
 *   刻意不复用 `childHome` 的字段——家长页反向依赖孩子端表会把两张表的演化绑在一起，
 *   一致性由测试断言（而不是由引用）保证。
 *
 * 函数型字段的返回值不在 data class `toString()` 里（Ruling 20-b 的盲区），整表 CJK 扫描看不见，
 * 因此 6 条每个都有逐字打出来的双语期望串，见
 * `StringsCopyTest.parentDashboardInterpolatedCopyPinsBothLanguages`；
 * 静态字段 30 条全部进 `StringsCopyTest.parentDashboardCopyDiffers` 的 ZH≠EN 穷举清单。
 *
 * 术语按设计文档 §11：积分 = Points、专注 = Focus、番茄钟 = Pomodoro、错题 = Mistakes、
 * 打卡 = Check in / Checked in、连续打卡 = Streak、基础/提升/习惯 = Basic / Advanced / Habits
 * （与 [TaskListStrings.filterBasic] / [filterEnhancement] / [filterHabits] 同一组词，但本表的三条
 * 各带一枚 emoji、是图表行标签而不是筛选芯片，所以是独立字段而不是跨表复用，英文词形必须一致，
 * 由 `StringsCopyTest.categoryWordsMatchTheTaskListFilters` 钉住）。
 */
data class ParentDashboardStrings(
    // ===== 顶栏与积分大卡片 =====
    val pageTitle: String,
    val totalPointsLabel: String,

    // ===== 统计网格 =====
    val weeklyEarnedLabel: String,
    val weeklySpentLabel: String,
    val totalFocusLabel: String,
    val mistakesMasteredLabel: String,

    // ===== 孩子今日学习表现 =====
    val childPerformanceTitle: String,
    val childPerformanceLine: (grade: String, completed: Int, total: Int, focusMinutes: Int, reviewed: Int) -> String,
    val scoreValue: (total: Int, scoreUnit: String) -> String,
    val scoreUnit: String,

    // ===== 本周 vs 上周 =====
    val verdictNewStart: String,
    val verdictImproved: String,
    val verdictLevel: String,
    val verdictDeclined: String,
    val weekCompareTitle: String,
    val compareFocusLabel: String,
    val focusMinutesValue: (minutes: Int, minuteLabel: String) -> String,
    val compareReviewLabel: String,
    val mistakesReviewedValue: (count: Int) -> String,
    val reviewCountUnit: String,
    val comparePointsLabel: String,

    // ===== 图表与分类 =====
    val trendTitle: String,
    val noDataLabel: String,
    val categoryTitle: String,
    val categoryBasicLabel: String,
    val categoryEnhanceLabel: String,
    val categoryHabitLabel: String,

    // ===== 薄弱环节提示 =====
    val weakAreasTitle: String,
    val weakAreaMistakesNote: (mastered: Int, total: Int) -> String,
    val weakAreaFocusNote: (minutes: Int) -> String,
    val weakAreaNoTasksNote: String,
    val goodStatusTitle: String,
    val goodStatusNote: String,

    // ===== 最近积分变动 =====
    val recentChangesTitle: String,
    val changeFallbackLabel: String,
    val deltaFlatLabel: String,
)

val ParentDashboardStringsZh = ParentDashboardStrings(
    // ===== 顶栏与积分大卡片 =====
    pageTitle = "📊 数据看板",                                               // :95
    totalPointsLabel = "当前总积分",                                          // :110

    // ===== 统计网格 =====
    weeklyEarnedLabel = "本周获得",                                           // :118
    weeklySpentLabel = "本周消耗",                                            // :119
    totalFocusLabel = "累计专注",                                             // :120
    mistakesMasteredLabel = "错题掌握",                                        // :121

    // ===== 孩子今日学习表现 =====
    childPerformanceTitle = "孩子今日学习表现",                                  // :138
    childPerformanceLine = { grade, completed, total, minutes, reviewed ->
        "$grade · 打卡${completed}/${total} · 专注${minutes}分 · 复习${reviewed}道"   // :139
    },
    scoreValue = { total, unit -> "$total$unit" },                         // :142（单位来自下面 scoreUnit 槽）
    scoreUnit = "分",                                                      // :142（评分单位，非积分）

    // ===== 本周 vs 上周 =====
    verdictNewStart = "🌱 新的开始，陪孩子一起加油！",                             // :163
    verdictImproved = "📈 孩子本周比上周进步了！",                                // :164
    verdictLevel = "➡️ 孩子本周和上周持平，稳定发挥。",                            // :165
    verdictDeclined = "💪 孩子本周略有下降，多鼓励加把劲！",                         // :166
    weekCompareTitle = "📅 孩子本周 vs 上周",                                  // :168
    compareFocusLabel = "🍅 专注时长",                                         // :171
    focusMinutesValue = { minutes, unit -> "$minutes$unit" },              // :171（分钟，单位来自 common.minuteLabel）
    compareReviewLabel = "📚 复习错题",                                        // :172
    mistakesReviewedValue = { count -> "${count}道" },                       // :172
    reviewCountUnit = "道",                                                  // :172（增减行用的量词）
    comparePointsLabel = "⭐ 获得积分",                                        // :173

    // ===== 图表与分类 =====
    trendTitle = "📈 7日积分获取趋势",                                         // :185
    noDataLabel = "暂无数据",                                                // :189
    categoryTitle = "📋 任务分类分布",                                         // :230
    categoryBasicLabel = "📝 基础",                                           // :232
    categoryEnhanceLabel = "📈 提升",                                         // :233
    categoryHabitLabel = "🏆 习惯",                                           // :234

    // ===== 薄弱环节提示 =====
    weakAreasTitle = "⚠️ 薄弱项提示",                                          // :258
    weakAreaMistakesNote = { mastered, total ->
        "📚 错题掌握率较低 (${mastered}/${total})，建议加强复习"                  // :243
    },
    weakAreaFocusNote = { minutes -> "🍅 专注时长不足 (${minutes}分钟)，建议使用番茄钟" }, // :246
    weakAreaNoTasksNote = "📝 暂无学习任务，请使用积分模板或手动添加",                // :249
    goodStatusTitle = "✅ 学习状态良好",                                        // :273
    goodStatusNote = "继续保持当前的学习节奏！",                                   // :275

    // ===== 最近积分变动 =====
    recentChangesTitle = "最近积分变动",                                       // :282
    changeFallbackLabel = "积分变动",                                         // :298（note 为空时的兜底显示）
    deltaFlatLabel = "→ 持平",                                               // :338
)

val ParentDashboardStringsEn = ParentDashboardStrings(
    // ===== 顶栏与积分大卡片 =====
    pageTitle = "📊 Data dashboard",
    totalPointsLabel = "Current total points",

    // ===== 统计网格 =====
    weeklyEarnedLabel = "Earned this week",
    weeklySpentLabel = "Spent this week",
    totalFocusLabel = "Total focus",
    mistakesMasteredLabel = "Mistakes mastered",

    // ===== 孩子今日学习表现 =====
    childPerformanceTitle = "Your child's study performance today",
    // Ruling 20：五个实参都是内容位，取数逻辑留在页面；`grade` 来自 LearningScoreCalculator，
    // 英文态目前仍会读出中文等级名（见类注释"跨文件聚合"那段，Task 12 人工清单）。
    childPerformanceLine = { grade, completed, total, minutes, reviewed ->
        "$grade · Check-in $completed/$total · Focus $minutes min · Reviewed $reviewed"
    },
    scoreValue = { total, unit -> "$total $unit" },    // 英文数值与单位之间要空格（ZH 侧不要，见类注释 Ruling 20 段）
    scoreUnit = "pts",                                 // 评分单位 pts，不是 points（见类注释同形异义第 2 条）

    // ===== 本周 vs 上周 =====
    verdictNewStart = "🌱 A fresh start — let's build the habit together!",
    verdictImproved = "📈 Your child did more this week than last week!",
    verdictLevel = "➡️ Level with last week — a steady, solid effort.",
    verdictDeclined = "💪 A little below last week — keep encouraging them.",
    weekCompareTitle = "📅 This week vs last week",
    compareFocusLabel = "🍅 Focus time",
    focusMinutesValue = { minutes, unit -> "$minutes $unit" },
    compareReviewLabel = "📚 Mistakes reviewed",
    // Ruling 20：英文没有"道"这个量词，值位退化成纯数字、增减位的单位是空串（不是漏译，见类注释）
    mistakesReviewedValue = { count -> "$count" },
    reviewCountUnit = "",
    comparePointsLabel = "⭐ Points earned",

    // ===== 图表与分类 =====
    trendTitle = "📈 Points earned over 7 days",
    noDataLabel = "No data yet",
    categoryTitle = "📋 Task categories",
    categoryBasicLabel = "📝 Basic",
    categoryEnhanceLabel = "📈 Advanced",
    categoryHabitLabel = "🏆 Habits",

    // ===== 薄弱环节提示 =====
    weakAreasTitle = "⚠️ Areas to work on",
    weakAreaMistakesNote = { mastered, total ->
        "📚 Only $mastered of $total mistakes are mastered — review them more often"
    },
    weakAreaFocusNote = { minutes ->
        "🍅 Only $minutes min of focus time — try the Pomodoro timer"
    },
    weakAreaNoTasksNote = "📝 No study tasks yet — apply a points template or add one by hand",
    goodStatusTitle = "✅ Everything looks good",
    goodStatusNote = "Keep the current study rhythm going!",

    // ===== 最近积分变动 =====
    recentChangesTitle = "Recent points activity",
    changeFallbackLabel = "Points change",
    deltaFlatLabel = "→ No change",
)

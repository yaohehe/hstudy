package com.hstudy.app.i18n

/**
 * 专注页四屏文案表：Task 7 的两屏 `ui/focus/ChildFocusStatsScreen.kt`（迁移前 17 条去重字面量）+
 * `ui/focus/ChildFocusTrainingScreen.kt`（16 条，另有 1 处"注意力热身"在迁移前第 22 行的 KDoc 块注释里，
 * 按 Ruling 18 不是可迁移文案、留在注释里）；Task 7b 再加两屏 `ui/focus/ChildMemoryTrainingScreen.kt`
 * （17 条）与 `ui/focus/ChildDailyPlanScreen.kt`（14 条），**按 Ruling 23 不再为页面文案新建第三张表**。
 *
 * 两屏合用一张表，因为 `🎯 专注力训练` 在两页各出现一次且同值同义（统计页的分区标题 /
 * 训练页的顶栏标题）——拆成两个字段就会有两个英文值可以互相漂移。于是
 * 33 条去重字面量 → **31 个字段**（另 `${todayMinutes}分钟` 与 `${totalMinutes}分钟`
 * 是同一个 [minutesStatValue] 的两个使用点）。
 * Task 7b 的 31 条 → **30 个新字段**（少的一条是 `🚀 开始训练`：记忆子页第 82 行与舒尔特页第 148 行
 * 同值同义，**复用 [startTrainingButton]** 而不是再造一个）。
 * 第 1 轮审查后又减到 **27 个**：`确认` / `删除` / `取消` 三条是跨页通用按钮词，
 * 上收到 [CommonStrings.confirmButton] / [deleteContentDescription] / [cancelButton]（中文值逐字节不变），
 * 留在页面表里就是 Task 10/11 再造一份的起点——理由写在 [CommonStrings] 的头注释里。
 *
 * 17 条函数型字段（Task 7 的 11 条 + Task 7b 记忆子页的 6 条）：设计文档 §3 规定带参数文案存函数而不是 `{0}` 模板（中英语序不同）。
 * 它们进不了 `StringsCopyTest.englishTableContainsNoChineseLeftovers`（data class `toString()`
 * 打印的是 lambda 对象本身，不含返回值），所以每个都由
 * `StringsCopyTest.focusInterpolatedCopyPinsBothLanguages`（Task 7 的 11 条）或
 * `StringsCopyTest.memoryTrainingInterpolatedCopyPinsBothLanguages`（Task 7b 的 6 条）
 * 用**逐字打出来的期望串**双语钉死（Ruling 20-b），
 * 单复数另见 `pomodoroAndFocusEnglishPluralsCoverOneAndOther` 与 `task7bEnglishPluralsCoverOneAndOther`。
 *
 * **时长单位字段刻意有四个几乎同形的**（[minutesStatValue] `45分钟` / [recordMinutesValue] `45 分钟` /
 * [secondsStatValue] `12.3秒` / [elapsedValue] `37.5 秒`）：源文件里空格位置本来就不同，
 * 合并任何两个都会让其中一处中文态输出变样，违反 Ruling 20 的 (b)（中文态逐字节不变）。
 * 四个字段都用字面量断言钉住，见测试里那组紧邻的 assertEquals。
 * 顺带一句实现细节：`${minutes}分钟` 必须带花括号、`$minutes 分钟` 可以不带，取决于中文前面
 * 有没有空格——别把这两种写法"统一"成同一种，那会改变输出（空格就是要钉住的东西）。
 *
 * **`分` / `秒` 在本表按 occurrence 说，不按整表说**（裁定 1；Task 7 时这里写的是"本表没有任何字段
 * 调用 [pointsWord]"，那句话在 Task 7b 之后一度不成立，**到第 1 轮审查修复后又成立了** —— 正因为它
 * 一会儿成立一会儿不成立，这里只逐条点名，不写绝对句）：
 * · 时长一侧：[minutesStatValue] / [recordMinutesValue] / [hoursStatValue] / [secondsStatValue] /
 *   [elapsedValue] / [personalBestValue] 六条全是时长、不接 [pointsWord]，
 *   英文用不随单复数变化的缩写 min / hr / s（与 [CommonStrings] 的 `minuteLabel = "min"`、
 *   `hourLabel = "hr"` 同源，且这些是 StatCard 里 `titleMedium` 的紧凑数值位）；
 * · 积分名词一侧：[dailyPracticeHint]（`还能赚积分～`）里"积分"是名词、不接数值，所以不需要复数函数，
 *   它是本表**唯一**提到积分的英文句子；
 * · **`+N分` 这一处不是积分账（第 1 轮审查的 Minor 修复）**：[memorySuccessLine]（记忆子页第 106 行）
 *   中文写 `记住了 $level 位数字！+${points}分`，同句既有"位数"又有"分"，所以两个槽做成两个**具名实参**
 *   `level` / `points`（页面两处都传同一个 `level`，与迁移前一致）。但那一页**不加积分**：
 *   `ChildMemoryTrainingScreen.kt:52` 明写 `val points = 0  // 训练不直接加分（防止无限刷分）`，
 *   中文的 `+3分` 是"这轮得 3 分"的口语分数，英文照写 `+3 points` 就成了**断言一笔 never 发生的入账**，
 *   所以英文改成 `You remembered 3 of 3 digits!`——两个实参都还在（中文侧照旧用 [points] 槽写 `+3分`，
 *   逐字节未动），只是英文不再把它读成积分。因此**本表目前没有任何字段调用 [pointsWord]**，
 *   这条仍按 occurrence 记，不要改写成"本表永不出现积分"那种绝对句（上面那句话已经被推翻过一次）。
 *   分槽与"英文不得把分数写成入账"由 `StringsCopyTest.pointsAndMinutesSharingOneLineUseSeparateSlots`
 *   与 `StringsCopyTest.pomodoroAndFocusMinuteUnitsAreMinutesNotPoints` 一起钉住。
 * 记忆子页的 `位数字` 英文写成不带分支的 "digits"，因为页面可达取值最小是 3
 * （`level` 初值 3，`回到 ${level - 1} 位` 那把按钮要求 `level > 3`）；若以后放开下限，
 * 按 [Plurals.kt] 的约定去那里加一个同样窄的 digitsWord，而不是写 `plural(n, word)` 泛化模板。
 *
 * **非 1:1 改动全清单（Ruling 20 四条全部满足，逐条点名披露）** —— 形态只有一种：
 * **数值表达式从字符串模板内部挪到实参位**，Task 7 一共 **7 处**：本表 6 处 + [PomodoroStrings] 的
 * [PomodoroStrings.deepBreathCount] 1 处（下面第 7 行一并列出，免得只翻一张表就以为清单齐了）。
 * 下面 `strFormat` 均指页面里的 `com.hstudy.app.ui.dt.strFormat`，括号里是表接管的文案 token：
 * 1. [secondsStatValue]：`"${strFormat("%.1f", trainingBestMs / 1000f)}秒"` →
 *    `strings.secondsStatValue(strFormat("%.1f", trainingBestMs / 1000f))`（`秒`）
 * 2. [hoursStatValue]：`"${totalMinutes / 60}小时"` → `strings.hoursStatValue(totalMinutes / 60)`（`小时`）
 * 3. [elapsedValue]：`strFormat("%.1f 秒", elapsedMs / 1000f)` →
 *    `strings.elapsedValue(strFormat("%.1f", elapsedMs / 1000f))`（` 秒`，空格在单位前面）
 *    —— **7 处里唯一一处单位原本写在数字格式串内部**的，也就是把 ` 秒` 从 `%.1f` 后面拆进表里；
 *    等价性靠 strFormat 对转换符之后普通字符的原样追加（`DateTimeCompat.kt:251-277`），
 *    由 `StringsCopyTest.elapsedValueKeepsTheSplitFormatPatternByteIdentical` 专门钉住，
 *    不靠"读了一遍 strFormat 说相同"；比对脚本 `scripts/i18n_template_diff.py` 的 `[strict]` 层
 *    会把这一处单独打出来（其余 61 条整串逐字节相同）。
 * 4. [personalBestValue]：`"🏆 个人最佳：${strFormat("%.1f", bestMs / 1000f)} 秒"` →
 *    `strings.personalBestValue(strFormat("%.1f", bestMs / 1000f))`（`🏆 个人最佳：`、` 秒`）
 * 5. [finishedLine]：`"🎉 完成！用时 ${strFormat("%.1f", seconds)} 秒 · 错误 $mistakes 次"` →
 *    `strings.finishedLine(strFormat("%.1f", seconds), mistakes)`
 *    （`🎉 完成！用时 `、` 秒 · 错误 `、` 次`；`·` 是文案分隔符，归表管）
 * 6. [trainingDoneSnackbar]：`"🎯 训练完成！用时${strFormat("%.1f", seconds)}秒，注意力提升了！"` →
 *    `strings.trainingDoneSnackbar(strFormat("%.1f", seconds))`（`🎯 训练完成！用时`、`秒，注意力提升了！`）
 * 7. [PomodoroStrings.deepBreathCount]（在另一张表）：
 *    `"第 ${(breathCycle + 1).coerceAtMost(totalBreathCycles)}/$totalBreathCycles 次深呼吸"` →
 *    `strings.deepBreathCount((breathCycle + 1).coerceAtMost(totalBreathCycles), totalBreathCycles)`
 *    （`第 `、`/`、` 次深呼吸`）
 *
 * **Task 7b 在本表新增的非 1:1 位点（3 处，形态仍是"表达式挪到实参位"，逐条点名）**：
 * 8. [memoryChallengeButton]：`"挑战 ${level + 1} 位 →"` → `strings.memoryChallengeButton(level + 1)`
 *    （`挑战 `、` 位 →`；加法表达式从模板内部挪到实参位，与上面第 7 条同形）
 * 9. [memoryStepBackButton]：`"回到 ${level - 1} 位"` → `strings.memoryStepBackButton(level - 1)`
 *    （`回到 `、` 位`；同上）
 * 10. [memorySuccessLine]：`"🎉 太棒了！记住了 $level 位数字！+${level}分"` →
 *     `strings.memorySuccessLine(level, level)`。迁移前同一句里两个槽位都由同一个 `level` 填，
 *     迁移后表内把它们做成两个**具名实参** `level`（位数）与 `points`（那一页的口语分数），页面仍传同一个变量：
 *     目的是让"位数"与"分数"在结构上分槽（裁定 1）。英文侧第 1 轮审查后不再写 `+N points`
 *     ——那一页不加积分，理由与逐字节自查见上面"` 分` / `秒` 按 occurrence 说"那一节。
 *     （`🎉 太棒了！记住了 `、` 位数字！+`、`分`）
 * 宿主页 `ui/focus/ChildPomodoroScreen.kt` 的 4 处（todayFocusSummary/overHoursNote 拆分、
 * durationChip 的单位来自另一张表、pointsPerFocusRule 的两个数字、goldenHourLabel 加 `@Composable`）
 * 列在 [PomodoroStrings] 的类注释里——**两张表的清单合起来才是本次的完整清单**。
 *
 * 四条自查：(a) 挪动的每个 token 都是文案（单位、分隔符、`·`、语序）；
 * (b) 中文态输出逐字节相同，由 [focusInterpolatedCopyPinsBothLanguages] 等字面量断言 +
 * `scripts/i18n_template_diff.py` 证明，不是口头论证；
 * (c) 未动控制流、数据源（同一个 `strFormat` 调用、同一个除法、同一批实参）、组件与布局参数；
 * (d) 此处 + `scripts/i18n_template_diff.py`：它的 strict 层把这一处识别为**格式串拆分位点**并自动点名（`--max-splits` 默认 1 就是为这一条设的上限）。任务报告随分支作废，披露落点不再放 `.superpowers/`（plan Ruling 35）。数值格式化（`%.1f`）**留在页面里**、不进文案表：spec §6 明确本阶段不引入
 * locale 相关的数字格式，若把 `%.1f` 写进语言表就等于开了那个口子。
 * 反过来，**不算例外**的插值位点是"模板里本来就是一个普通变量"那一类（brief 自己给的形态）：
 * [totalSessionsLine]（原 `🎯 累计训练 $trainingCount 次`）、[sessionsStatValue]（原 `${trainingCount}次`）、
 * [PomodoroStrings.breakOverSummary]、[nextTargetLabel]（原 `下一个要点：$nextExpected`，本表字段，
 *  Task 7 的这行注释当时把它写成了 `PomodoroStrings.nextTargetLabel`，是个失效的文档链接，本次顺手改对）、
 * [PomodoroStrings.remainTime] —— 变量本来就在实参位上算好了，只是搬进表里，属于 1:1 等价替换。
 * Task 7b 里属于这一类的还有 [memoryBestLine] / [memoryReadyLine] / [memoryInputPrompt]
 * （实参分别是页面上已有的 `bestLevel` / `level`）与宿主页的 [PomodoroStrings.completedPomodorosLine] /
 * [PomodoroStrings.focusRecordDescription] / [PomodoroStrings.focusDoneSnackbar] /
 * [PomodoroStrings.goldenHourNowNote] / [PomodoroStrings.goldenHourGeneralNote]。
 *
 * **复用而不复制的同形/同义位点（Task 7b 第二件事）**：
 * · 记忆子页第 82 行 `🚀 开始训练` == 舒尔特页第 148 行 → 复用 [startTrainingButton]，不新增字段；
 * · 宿主页两处 `${min}分` → 一个 [PomodoroStrings.durationChip]，且单位取自
 *   [CommonStrings.minuteLabel]（本表与 [PomodoroStrings] 都不开第三个 `分` 字段）；
 * · **没有**为记忆子页另造一个"专注力训练"标题字段：训练子页的标题类文案在本表只有
 *   [trainingTitle]（`🎯 专注力训练`）与 [schulteTitle]（`舒尔特方格`）两个出处，
 *   记忆子页的 `🧠 记忆广度训练` 是**另一种训练**（工作记忆，不是选择性注意），
 *   与那两个字段不同值也不同义，所以做成新字段 [memoryTitle] 而不是复用（复用会把两种训练混成一个名字）；
 *   宿主页那两个入口按钮（`🎯 专注力训练·舒尔特方格（专注前热身）` /
 *   `🧠 记忆广度训练·数字记忆（练工作记忆）`）在 [PomodoroStrings.schulteEntryButton] /
 *   [PomodoroStrings.memoryEntryButton]，是**整句 CTA**、不是这两个标题字段的同义复制：
 *   把它们写成 `trainingTitle + "·" + schulteTitle + "（专注前热身）"` 才会真的越界——
 *   `·` 与 `（…）` 是文案 token，必须留在表里，而且行首 🎯 已经在 [trainingTitle] 内、拼不出第二份。
 *   Task 7 的 [trainingBlurb] 早就是同一种做法（整句里出现"舒尔特方格"和"数字记忆"，没有反查 schulteTitle）。
 *
 * 行首 emoji 归文案表（Ruling 17）：`📊`、`🎯`、`🏆`、`🎉` 在中文原文里就贴在文案上，
 * 英文同位置保留；[emptyRecordsHint] 的 🍅 在句尾，两边都在句尾。Task 7b 的两屏同样照搬：
 * [memoryTitle] 🧠、[memoryMissPrompt] 😊、[memoryRetryButton] 🔄、[dailyPlanTitle] 📋、
 * [planAddedSnackbar] ✅、[addPlanDialogTitle] 📋，函数型的 [memoryBestLine] 🏆 /
 * [memorySuccessLine] 🎉 也在表内；**行中没有 emoji 的那几条**（[memoryHeading]、[memoryPracticeAgainButton]、
 * [planSubjectLabel] 等）中文原文就没有，英文也不补。由
 * `StringsCopyTest.emojiStaysInsideThePomodoroAndFocusTables` 钉住。
 * 统计页记录行右侧的 `"${data.totalMinutes}m"` 与 `Text("📅")` 不含中文、英文态也已成立，
 * 与 [CommonStrings]（Task 6）保留 `Text("⭐")` 的既有做法一致，留在页面里未动；
 * 每日计划页的 `Text("${completedCount}/${plans.size}")`、`Text("~")`、`Text("📅")`、
 * `Text("$label ")`（TimePickerField 的单位后置空格）同一道理，不含中文、留在页面。
 *
 * 术语按设计文档 §11：专注 = Focus、番茄钟 = Pomodoro、积分 = Points；
 * 新增：舒尔特方格 = Schulte grid、专注力训练 = Focus training、个人最佳 = Personal best
 * （已补进 §11 术语表）。Task 7b 新用的四个词也已同批补进 §11：
 * 记忆广度 = memory span、工作记忆 = working memory、黄金专注时段 = golden focus window、
 * 每日计划/今日计划 = today's plan。
 */
data class FocusStrings(
    val statsTitle: String,
    val todayFocusLabel: String,
    val totalFocusLabel: String,
    val approxLabel: String,
    val trainingTitle: String,
    val sessionsLabel: String,
    val gridBestLabel: String,
    val trainingBlurb: String,
    val last7DaysTitle: String,
    val recordsTitle: String,
    val emptyRecordsHint: String,
    val minutesStatValue: (minutes: Int) -> String,
    val recordMinutesValue: (minutes: Int) -> String,
    val hoursStatValue: (hours: Int) -> String,
    val sessionsStatValue: (count: Int) -> String,
    val secondsStatValue: (seconds: String) -> String,
    val schulteTitle: String,
    val schulteHowTo: String,
    val elapsedValue: (seconds: String) -> String,
    val personalBestValue: (seconds: String) -> String,
    val totalSessionsLine: (count: Int) -> String,
    val verdictExcellent: String,
    val verdictGood: String,
    val verdictPractice: String,
    val newRecordBanner: String,
    val finishedLine: (seconds: String, mistakes: Int) -> String,
    val tryAgainButton: String,
    val dailyPracticeHint: String,
    val startTrainingButton: String,
    val nextTargetLabel: (next: Int) -> String,
    val trainingDoneSnackbar: (seconds: String) -> String,

    // ===== ui/focus/ChildMemoryTrainingScreen.kt（Task 7b 记忆广度子页，17 条 → 本表 15 个字段）=====
    // 少的第一条是 `🚀 开始训练`：与舒尔特页第 148 行同值同义，复用已有的 [startTrainingButton]。
    // 少的第二条是 `确认`：通用按钮词，第 1 轮审查后上收为 [CommonStrings.confirmButton]（值逐字节不变）。
    val memoryTitle: String,
    val memoryHeading: String,
    val memoryHowTo: String,
    val memoryBestLine: (level: Int) -> String,
    val memoryReadyLine: (level: Int) -> String,
    val memoryShowPrompt: String,
    val memoryWatchHint: String,
    val memoryInputPrompt: (level: Int) -> String,
    val memorySuccessLine: (level: Int, points: Int) -> String,
    val memoryChallengeButton: (level: Int) -> String,
    val memoryPracticeAgainButton: String,
    val memoryMissPrompt: String,
    val memoryEncourageHint: String,
    val memoryRetryButton: String,
    val memoryStepBackButton: (level: Int) -> String,

    // ===== ui/focus/ChildDailyPlanScreen.kt（Task 7b 每日计划子页，14 条 → 本表 12 个字段，全非函数型）=====
    // `删除`（:155）与 `取消`（:199）是通用控件词，第 1 轮审查后上收到 CommonStrings（中文值逐字节不变）。
    val dailyPlanTitle: String,
    val addPlanDescription: String,
    val dailyPlanEmptyHint: String,
    val planAddedSnackbar: String,
    val planCompletedDescription: String,
    val planUncompletedDescription: String,
    val addPlanDialogTitle: String,
    val planSubjectLabel: String,
    val planTimeRangeLabel: String,
    val planStartLabel: String,
    val planEndLabel: String,
    val addPlanButton: String,
)

val FocusStringsZh = FocusStrings(
    statsTitle = "📊 专注统计",
    todayFocusLabel = "今日专注",
    totalFocusLabel = "累计专注",
    approxLabel = "约",
    trainingTitle = "🎯 专注力训练",
    sessionsLabel = "累计训练",
    gridBestLabel = "方格最佳",
    trainingBlurb = "舒尔特方格练选择性注意，数字记忆练工作记忆，坚持训练专注力更强！",
    last7DaysTitle = "最近7天专注时长",
    recordsTitle = "专注记录",
    emptyRecordsHint = "暂无专注记录，开始你的第一个番茄钟吧！🍅",
    minutesStatValue = { minutes -> "${minutes}分钟" },
    recordMinutesValue = { minutes -> "$minutes 分钟" },
    hoursStatValue = { hours -> "${hours}小时" },
    sessionsStatValue = { count -> "${count}次" },
    secondsStatValue = { seconds -> "${seconds}秒" },
    schulteTitle = "舒尔特方格",
    schulteHowTo = "按 1→25 的顺序尽快点击，训练注意力集中",
    elapsedValue = { seconds -> "$seconds 秒" },
    personalBestValue = { seconds -> "🏆 个人最佳：$seconds 秒" },
    totalSessionsLine = { count -> "🎯 累计训练 $count 次" },
    verdictExcellent = "🌟 超棒！注意力非常集中！",
    verdictGood = "👍 不错！继续保持！",
    verdictPractice = "💪 完成训练，多练会更快！",
    newRecordBanner = "🎊 太厉害了！打破个人纪录！",
    finishedLine = { seconds, mistakes -> "🎉 完成！用时 $seconds 秒 · 错误 $mistakes 次" },
    tryAgainButton = "🔄 再来一次",
    dailyPracticeHint = "每天练一练，专注力up up！还能赚积分～",
    startTrainingButton = "🚀 开始训练",
    nextTargetLabel = { next -> "下一个要点：$next" },
    trainingDoneSnackbar = { seconds -> "🎯 训练完成！用时${seconds}秒，注意力提升了！" },

    // ===== ui/focus/ChildMemoryTrainingScreen.kt（Task 7b）：逐字来自该页迁移前的对应行 =====
    memoryTitle = "🧠 记忆广度训练",                                       // :59
    memoryHeading = "数字记忆广度",                                        // :67
    memoryHowTo = "记住闪现的数字串，凭记忆输出来，训练工作记忆",              // :68
    memoryBestLine = { level -> "🏆 本次最佳：记住 $level 位数字" },         // :73
    memoryReadyLine = { level -> "准备好了吗？从 $level 位数字开始！" },      // :81
    memoryShowPrompt = "集中注意力，记住这些数字：",                          // :85
    memoryWatchHint = "认真看…",                                          // :88
    memoryInputPrompt = { level -> "数字是什么？凭记忆输入（$level 位）：" },  // :92
    // `确认`（:102）第 1 轮审查后上收为 CommonStrings.confirmButton，中文值未变
    memorySuccessLine = { level, points -> "🎉 太棒了！记住了 $level 位数字！+${points}分" }, // :106
    memoryChallengeButton = { level -> "挑战 $level 位 →" },                // :110（页面传 level + 1）
    memoryPracticeAgainButton = "再练一次",                                 // :111
    memoryMissPrompt = "😊 没关系！正确答案是：",                            // :114
    memoryEncourageHint = "多练几次，记忆力会越来越强！",                     // :117
    memoryRetryButton = "🔄 再试一次",                                     // :120
    memoryStepBackButton = { level -> "回到 $level 位" },                   // :122（页面传 level - 1）

    // ===== ui/focus/ChildDailyPlanScreen.kt（Task 7b）=====
    dailyPlanTitle = "📋 今日计划",                                        // :40
    addPlanDescription = "添加计划",                                       // :50（FAB 的 contentDescription）
    dailyPlanEmptyHint = "还没有今日计划，点击下方 + 添加",                   // :63
    planAddedSnackbar = "✅ 计划已添加",                                    // :107
    planCompletedDescription = "已完成",                                   // :135
    planUncompletedDescription = "未完成",                                  // :138
    // `删除`（:155）与 `取消`（:199）第 1 轮审查后上收为 CommonStrings 的同值字段，中文未变
    addPlanDialogTitle = "📋 添加学习计划",                                  // :175
    planSubjectLabel = "学习内容",                                          // :180
    planTimeRangeLabel = "时间段",                                          // :183
    planStartLabel = "开始",                                               // :185
    planEndLabel = "结束",                                                 // :189
    addPlanButton = "添加",                                                // :197
)

val FocusStringsEn = FocusStrings(
    statsTitle = "📊 Focus stats",
    todayFocusLabel = "Today's focus",
    totalFocusLabel = "All-time focus",
    approxLabel = "About",
    trainingTitle = "🎯 Focus training",
    sessionsLabel = "Sessions",
    gridBestLabel = "Best grid",
    trainingBlurb = "The Schulte grid trains selective attention; number memory trains working memory. " +
        "Keep at it and your focus gets stronger!",
    last7DaysTitle = "Focus minutes over the last 7 days",
    recordsTitle = "Focus log",
    emptyRecordsHint = "No focus records yet — run your first Pomodoro! 🍅",
    minutesStatValue = { minutes -> "$minutes min" },
    recordMinutesValue = { minutes -> "$minutes min" },
    hoursStatValue = { hours -> "$hours hr" },
    sessionsStatValue = { count -> if (count == 1) "1 session" else "$count sessions" },
    secondsStatValue = { seconds -> "$seconds s" },
    schulteTitle = "Schulte Grid",
    schulteHowTo = "Tap the numbers in order, 1→25, as fast as you can — it trains focused attention.",
    elapsedValue = { seconds -> "$seconds s" },
    personalBestValue = { seconds -> "🏆 Personal best: $seconds s" },
    totalSessionsLine = { count -> if (count == 1) "🎯 1 session so far" else "🎯 $count sessions so far" },
    verdictExcellent = "🌟 Outstanding! Your attention was locked in!",
    verdictGood = "👍 Nice work — keep it up!",
    verdictPractice = "💪 Training done — you'll get faster with practice!",
    newRecordBanner = "🎊 Amazing — you beat your personal record!",
    finishedLine = { seconds, mistakes ->
        "🎉 Done in $seconds s · $mistakes ${if (mistakes == 1) "mistake" else "mistakes"}"
    },
    tryAgainButton = "🔄 Try again",
    dailyPracticeHint = "Practice a little every day and your focus keeps growing — and you earn points too!",
    startTrainingButton = "🚀 Start training",
    nextTargetLabel = { next -> "Next, tap $next" },
    trainingDoneSnackbar = { seconds -> "🎯 Training done in $seconds s — your attention is warmed up!" },

    // ===== ui/focus/ChildMemoryTrainingScreen.kt（Task 7b）=====
    // "位数字" 的最小真实取值是 3（level 初值 3，"回到 N 位" 按钮要求 level > 3），
    // 所以英文 "digits" 不带 one/other 分支；可达性论证见类注释，若以后放开下限要在 Plurals.kt 加窄函数。
    memoryTitle = "🧠 Memory span training",
    memoryHeading = "Digit memory span",
    memoryHowTo = "Watch the digits while they flash, then type them back from memory — " +
        "this trains your working memory.",
    memoryBestLine = { level -> "🏆 Best this round: $level digits remembered" },
    memoryReadyLine = { level -> "Ready? Let's start with $level digits!" },
    memoryShowPrompt = "Concentrate and remember these digits:",
    memoryWatchHint = "Watch closely…",
    memoryInputPrompt = { level -> "What were the digits? Type them from memory ($level digits):" },
    memorySuccessLine = { level, points ->
        "🎉 Amazing! You remembered $points of $level digits!"
    },
    memoryChallengeButton = { level -> "Try $level digits →" },
    memoryPracticeAgainButton = "Practice again",
    memoryMissPrompt = "😊 That's okay! The answer was:",
    memoryEncourageHint = "Practice a few more times and your memory keeps getting stronger!",
    memoryRetryButton = "🔄 Try once more",
    memoryStepBackButton = { level -> "Back to $level digits" },

    // ===== ui/focus/ChildDailyPlanScreen.kt（Task 7b）=====
    dailyPlanTitle = "📋 Today's plan",
    addPlanDescription = "Add plan",
    dailyPlanEmptyHint = "No plan for today yet — tap the + below to add one",
    planAddedSnackbar = "✅ Plan added",
    planCompletedDescription = "Done",
    planUncompletedDescription = "Not done",
    addPlanDialogTitle = "📋 Add a study plan",
    planSubjectLabel = "What to study",
    planTimeRangeLabel = "Time range",
    planStartLabel = "Start",
    planEndLabel = "End",
    addPlanButton = "Add",
)

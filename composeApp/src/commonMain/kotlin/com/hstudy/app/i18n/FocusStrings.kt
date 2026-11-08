package com.hstudy.app.i18n

/**
 * 专注页两屏文案表（Task 7）：`ui/focus/ChildFocusStatsScreen.kt`（迁移前 17 条去重字面量）+
 * `ui/focus/ChildFocusTrainingScreen.kt`（16 条，另有 1 处"注意力热身"在迁移前第 22 行的 KDoc 块注释里，
 * 按 Ruling 18 不是可迁移文案、留在注释里）。
 *
 * 两屏合用一张表，因为 `🎯 专注力训练` 在两页各出现一次且同值同义（统计页的分区标题 /
 * 训练页的顶栏标题）——拆成两个字段就会有两个英文值可以互相漂移。于是
 * 33 条去重字面量 → **31 个字段**（另 `${todayMinutes}分钟` 与 `${totalMinutes}分钟`
 * 是同一个 [minutesStatValue] 的两个使用点）。
 *
 * 11 条函数型字段：设计文档 §3 规定带参数文案存函数而不是 `{0}` 模板（中英语序不同）。
 * 它们进不了 `StringsCopyTest.englishTableContainsNoChineseLeftovers`（data class `toString()`
 * 打印的是 lambda 对象本身，不含返回值），所以每个都由
 * `StringsCopyTest.focusInterpolatedCopyPinsBothLanguages` 用**逐字打出来的期望串**双语钉死（Ruling 20-b），
 * 单复数另见 `pomodoroAndFocusEnglishPluralsCoverOneAndOther`。
 *
 * **时长单位字段刻意有四个几乎同形的**（[minutesStatValue] `45分钟` / [recordMinutesValue] `45 分钟` /
 * [secondsStatValue] `12.3秒` / [elapsedValue] `37.5 秒`）：源文件里空格位置本来就不同，
 * 合并任何两个都会让其中一处中文态输出变样，违反 Ruling 20 的 (b)（中文态逐字节不变）。
 * 四个字段都用字面量断言钉住，见测试里那组紧邻的 assertEquals。
 * 顺带一句实现细节：`${minutes}分钟` 必须带花括号、`$minutes 分钟` 可以不带，取决于中文前面
 * 有没有空格——别把这两种写法"统一"成同一种，那会改变输出（空格就是要钉住的东西）。
 *
 * **`分` / `秒` 在这里全是时长，不是积分**（裁定 1）：本页没有任何字段调用 [pointsWord]，
 * 英文用不随单复数变化的缩写 min / hr / s（与 [CommonStrings] 的 `minuteLabel = "min"`、
 * `hourLabel = "hr"` 同源，且这些是 StatCard 里 `titleMedium` 的紧凑数值位）。
 * 唯一提到积分的是 [dailyPracticeHint]（`还能赚积分～`）——"积分"在那里是名词不是单位、不接数值，
 * 所以同样不需要复数函数。这条事实由
 * `StringsCopyTest.pomodoroAndFocusMinuteUnitsAreMinutesNotPoints` 钉住。
 *
 * 非 1:1 改动（Ruling 20 四条全部满足并在此披露）只有一种形态：**数值表达式从字符串模板内部挪到实参位**。
 * 页面原本写 `"${com.hstudy.app.ui.dt.strFormat("%.1f", x)}秒"`，函数化后写
 * `strings.secondsStatValue(com.hstudy.app.ui.dt.strFormat("%.1f", x))`；
 * `"${totalMinutes / 60}小时"` → `strings.hoursStatValue(totalMinutes / 60)`。
 * (a) 表接管的是文案 token（`秒`、`小时`、` 分钟`、`次`、`·`、语序）；
 * (b) 中文态输出逐字节相同，由字面量断言证明而非口头论证；
 * (c) 未动控制流、数据源（同一个 `strFormat` / 同一个除法）、组件与布局参数；
 * (d) 此处 + 任务报告。数值格式化（`%.1f`）**留在页面里**、不进文案表：spec §6 明确本阶段不引入
 * locale 相关的数字格式，若把 `%.1f` 写进语言表就等于开了那个口子。
 *
 * 行首 emoji 归文案表（Ruling 17）：`📊`、`🎯`、`🏆`、`🎉` 在中文原文里就贴在文案上，
 * 英文同位置保留；[emptyRecordsHint] 的 🍅 在句尾，两边都在句尾。由
 * `StringsCopyTest.emojiStaysInsideThePomodoroAndFocusTables` 钉住。
 * 统计页记录行右侧的 `"${data.totalMinutes}m"` 与 `Text("📅")` 不含中文、英文态也已成立，
 * 与 [CommonStrings]（Task 6）保留 `Text("⭐")` 的既有做法一致，留在页面里未动。
 *
 * 术语按设计文档 §11：专注 = Focus、番茄钟 = Pomodoro、积分 = Points；
 * 新增：舒尔特方格 = Schulte grid、专注力训练 = Focus training、个人最佳 = Personal best
 * （已补进 §11 术语表）。
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
)

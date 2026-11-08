package com.hstudy.app.i18n

/**
 * 番茄钟组件（ui/common/PomodoroTimer.kt）文案表（Task 7）。
 *
 * 31 条逐字来自该文件迁移前的第 102/103/138-142/164/169/220/221/251/264/276-281/289/292/
 * 317/328/335/344/355/371/381/388 行。脚本清单是"出现 34 次 / 去重 31 条"：
 * `☕ 休息中` 与 `🍅 专注中` 各出现在两处（息屏运行通知的标题 + 圆环下方状态文字）、
 * `结束` 出现在两处按钮（RUNNING / PAUSED），**同值同义所以共用一个字段**，
 * 于是 31 条去重字面量落成 31 个字段（不是 34 个）。
 *
 * 另有两条**不含中文**的页面字面量按既有做法留在页面里、没有进表：
 * `Text(com.hstudy.app.ui.dt.strFormat("%02d:%02d", minutes, seconds))`（倒计时数字本身）与
 * `Text("🍅 × ${completedPomodoros}")`（已完成个数）。前者是纯数字格式（spec §6 不动格式），
 * 后者的 `🍅 ×` 两种语言同形、且 `Task 6` 的 `Text("⭐")` / `Text(":")` 已确立同样的边界：
 * 页面里只承担排版的符号不算文案。
 *
 * **文案从这个"半状态文件"的哪一层离开**：本文件除 `PomodoroState` 枚举和文件末尾的
 * `private fun playCompletionAlert()` 外只有一个 `@Composable fun PomodoroTimer(...)`，
 * 而那两处都不产生文案（只做平台调用 / 只有枚举常量名）。计时循环、呼吸引导循环都在
 * Composable **内部**的 `LaunchedEffect(state) { … }` 里，文案就在那里交给系统通知，
 * 所以取文案的缝就在 Composable 顶部：`val strings = LocalStrings.current.pomodoro`，
 * 由协程闭包捕获。没有把 `CompositionLocal` 的值存进单例、类字段或 `remember` 之外的任何变量，
 * 也没有把 [PomodoroStrings] 作为参数穿过非 UI 代码。代价写在报告里：语言在计时进行中切换时，
 * **已经发出去的那条常驻通知**要等下一次状态切换才会用新语言重写（`LaunchedEffect` 的 key 是
 * `state`，不是 strings；改 key 会重启计时循环，属于 Ruling 20(c) 禁止的控制流改动）。
 *
 * 三条函数型字段（[remainTime]、[deepBreathCount]、[breakOverSummary]）：设计文档 §3 规定带参数
 * 文案存函数而不是 `{0}` 模板，因为中英语序不同。函数型字段的返回值不在 data class `toString()` 里，
 * 整表 CJK 守卫看不见它们（Ruling 20-b），所以由 `StringsCopyTest.pomodoroInterpolatedCopyPinsBothLanguages`
 * 用逐字打出来的期望串钉住两种语言，另有 [breakOverSummary] 的 Pomodoro/Pomodoros 单复数断言。
 *
 * **`分` 在本页只表示分钟，一处也不是积分**（裁定 1）：唯一出现单字"分"的是
 * `剩余 ${min}分${sec}秒`，它是时长而不是分值，因此 [remainTime] 不调用 [pointsWord]，
 * 英文按计划 brief 给定的串写成不随单复数变化的缩写 `min` / `s`。由
 * `StringsCopyTest.pomodoroAndFocusMinuteUnitsAreMinutesNotPoints` 钉住。
 *
 * 非 1:1 改动（Ruling 20 四条全部满足并在此披露）只有一处形态：[deepBreathCount] 的中文原文是
 * `第 ${(breathCycle + 1).coerceAtMost(totalBreathCycles)}/$totalBreathCycles 次深呼吸`，
 * 那个 `.coerceAtMost(...)` 是**在字面量内部**算出来的数值，函数化后它变成实参
 * `strings.deepBreathCount((breathCycle + 1).coerceAtMost(totalBreathCycles), totalBreathCycles)`。
 * (a) 表只接管了文案 token（`第`、`/`、` 次深呼吸`）；(b) 中文态输出逐字节相同，由字面量断言证明；
 * (c) 表达式本身、控制流、组件、布局参数一律未动，只是把它从字符串模板里挪到实参位；(d) 见此处与报告。
 * [remainTime] 的两个实参在页面里本就是 `val min` / `val sec`，所以那条是纯字面量替换。
 *
 * 行首 emoji 属于文案表而不是页面代码（Ruling 17）：中文原文就写着 `🍅 专注中`，
 * 所以 ZH 侧照抄、EN 侧同位置保留，页面不再拼 `"🍅 ${strings.x}"`。由
 * `StringsCopyTest.emojiStaysInsideThePomodoroAndFocusTables` 钉住。
 * brief Step 2 的示例值 `phaseFocus = "Focusing"` 没带 emoji，这里按本工程的 emoji 入表约定补成
 * `🍅 Focusing`；示例串 `remainTime` 的英文逐字照用。
 *
 * 术语按设计文档 §11：番茄钟 = Pomodoro、专注 = Focus、深呼吸 = Breath。
 * [breakOverSummary] 的英文带 one/other（`completedPomodoros` 首完成就是 1，"1 Pomodoros" 是真会
 * 出现在设备上的语法错误）；`、` 之类分隔符与 `·` 一样归文案表管。
 */
data class PomodoroStrings(
    val phaseBreak: String,
    val phaseFocus: String,
    val remainTime: (min: Int, sec: Int) -> String,
    val cheerHalfway: String,
    val cheerProudest: String,
    val cheerAlmostDone: String,
    val cheerBrainWorking: String,
    val cheerEveryMinute: String,
    val breatheIn: String,
    val breatheOut: String,
    val deepBreathCount: (current: Int, total: Int) -> String,
    val phaseStartReady: String,
    val phaseBreakOver: String,
    val breakOverSummary: (completed: Int) -> String,
    val breathingGuide: String,
    val tipWalkAround: String,
    val tipLookFarAway: String,
    val tipDrinkWater: String,
    val tipBreathe: String,
    val tipStretch: String,
    val tipListenMusic: String,
    val breakCardTitle: String,
    val breakCardNote: String,
    val startFocusButton: String,
    val skipBreathingButton: String,
    val pauseButton: String,
    val stopButton: String,
    val resumeButton: String,
    val skipBreakButton: String,
    val startNextRoundButton: String,
    val restFirstButton: String,
)

val PomodoroStringsZh = PomodoroStrings(
    phaseBreak = "☕ 休息中",
    phaseFocus = "🍅 专注中",
    remainTime = { min, sec -> "剩余 ${min}分${sec}秒 · 息屏后仍会继续计时" },
    cheerHalfway = "💪 已经过半了，继续保持！",
    cheerProudest = "🌟 专注的你最棒了！",
    cheerAlmostDone = "🔥 坚持住，马上就完成了！",
    cheerBrainWorking = "🧠 大脑正在高效运转！",
    cheerEveryMinute = "✨ 每一分钟都在积累成长！",
    breatheIn = "慢慢吸气",
    breatheOut = "缓缓呼气",
    deepBreathCount = { current, total -> "第 ${current}/$total 次深呼吸" },
    phaseStartReady = "🍅 准备开始",
    phaseBreakOver = "🎉 休息结束",
    breakOverSummary = { completed -> "🎉 休息结束！已完成 $completed 个番茄钟，继续加油！" },
    breathingGuide = "跟随圆圈深呼吸，让心平静下来，准备进入专注状态",
    tipWalkAround = "🚶 站起来走动一下，活动活动身体",
    tipLookFarAway = "👀 看看窗外远处，让眼睛放松一下",
    tipDrinkWater = "💧 喝杯水，补充水分",
    tipBreathe = "🧘 做几个深呼吸，放松身心",
    tipStretch = "🤸 伸个懒腰，伸展一下筋骨",
    tipListenMusic = "🎵 听一首喜欢的歌，放松心情",
    breakCardTitle = "☕ 科学休息",
    breakCardNote = "好好休息，下一轮专注更高效～",
    startFocusButton = "开始专注",
    skipBreathingButton = "跳过，直接开始",
    pauseButton = "暂停",
    stopButton = "结束",
    resumeButton = "继续",
    skipBreakButton = "跳过休息",
    startNextRoundButton = "开始下一轮专注",
    restFirstButton = "先休息一下",
)

val PomodoroStringsEn = PomodoroStrings(
    phaseBreak = "☕ On a break",
    phaseFocus = "🍅 Focusing",
    remainTime = { min, sec -> "$min min $sec s left · Keeps counting with the screen off" },
    cheerHalfway = "💪 You're past the halfway mark — keep going!",
    cheerProudest = "🌟 You're at your best when you focus!",
    cheerAlmostDone = "🔥 Hang in there — you're almost done!",
    cheerBrainWorking = "🧠 Your brain is running at full speed!",
    cheerEveryMinute = "✨ Every minute adds up to your growth!",
    breatheIn = "Breathe in slowly",
    breatheOut = "Breathe out slowly",
    deepBreathCount = { current, total -> "Deep breath $current of $total" },
    phaseStartReady = "🍅 Ready to start",
    phaseBreakOver = "🎉 Break over",
    breakOverSummary = { completed ->
        if (completed == 1) "🎉 Break over! 1 Pomodoro done — keep going!"
        else "🎉 Break over! $completed Pomodoros done — keep going!"
    },
    breathingGuide = "Breathe along with the circle and let your mind settle — then get ready to focus.",
    tipWalkAround = "🚶 Stand up and walk around a bit",
    tipLookFarAway = "👀 Look out the window at something far away to rest your eyes",
    tipDrinkWater = "💧 Have a glass of water",
    tipBreathe = "🧘 Take a few deep breaths and relax",
    tipStretch = "🤸 Stretch and loosen up",
    tipListenMusic = "🎵 Listen to a song you like",
    breakCardTitle = "☕ Smart break",
    breakCardNote = "Rest well now and the next focus round goes even better.",
    startFocusButton = "Start focusing",
    skipBreathingButton = "Skip and start now",
    pauseButton = "Pause",
    stopButton = "Stop",
    resumeButton = "Resume",
    skipBreakButton = "Skip the break",
    startNextRoundButton = "Start the next round",
    restFirstButton = "Take a break first",
)

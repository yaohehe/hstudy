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
 * 也没有把 [PomodoroStrings] 作为参数穿过非 UI 代码。
 *
 * **Task 7b 修正（本次授权的功能修正，不属于等价替换）**：Task 7 留下的代价是"语言在计时进行中切换时，
 * 已发出去的那条常驻通知要等下一次状态切换才换语言"，因为把 `strings` 加进 `LaunchedEffect` 的 key
 * 会重启计时循环（Ruling 20(c) 禁止）。现在 `PomodoroTimer.kt` 顶部多了一行
 * `val currentStrings by rememberUpdatedState(strings)`，循环体内读 `currentStrings`、**key 仍是 `state`**：
 * 委托读的是最新实例，所以最迟 15 秒后下一次通知刷新就用新语言重写，循环不重启、不丢时长。
 * 中文态输出逐字节不变（不改语言时 `currentStrings === strings`），由
 * `StringsCopyTest.runningNotificationCopyIsPinnedInBothLanguages` 把 [phaseFocus] / [phaseBreak] /
 * [remainTime] 三条双语钉死。**没被这一行覆盖到的两处仍然会滞留旧语言**，理由与范围都写在报告里：
 * · [cheerHalfway] 等 5 条欢呼在 `remember(completedPomodoros) { … .random() }` 里，
 *   key 不含语言，所以要等下一个番茄钟完成才换（把 remember 改成"记索引、渲染时取词"才是控制流改动，
 *   超出本次授权，按"STOP 并报告"处理）；
 * · 宿主页交给 `onComplete` 的 snackbar 闭包捕获的是**页面**当时的 `strings`，与本行无关。
 *
 * 组件的三条函数型字段（[remainTime]、[deepBreathCount]、[breakOverSummary]）+ Task 7b 宿主页的十条
 * （[goldenHourNowNote]、[goldenHourGeneralNote]、[goldenPeriodHour]、[todayFocusSummary]、
 * [overHoursNote]、[completedPomodorosLine]、[focusRecordDescription]、[focusDoneSnackbar]、
 * [durationChip]、[pointsPerFocusRule]）：设计文档 §3 规定带参数
 * 文案存函数而不是 `{0}` 模板，因为中英语序不同。函数型字段的返回值不在 data class `toString()` 里，
 * 整表 CJK 守卫看不见它们（Ruling 20-b），所以由 `StringsCopyTest.pomodoroInterpolatedCopyPinsBothLanguages`
 * 与 `StringsCopyTest.pomodoroPageInterpolatedCopyPinsBothLanguages`
 * 用逐字打出来的期望串钉住两种语言，另有 [breakOverSummary] / [completedPomodorosLine] 的
 * Pomodoro/Pomodoros 单复数断言。
 *
 * **`分` 在本表有两种含义，逐处点名（裁定 1；Task 7b 之后本表同时装着组件与宿主页两类文案）**。
 * 原先这里写"唯一出现单字『分』的是 `剩余 ${min}分${sec}秒`"，**那句话在本表内就是假的**；
 * Task 7 修成"整页只剩时长一种"之后，那句**对组件成立、对整表不再成立**，所以这里改成逐 occurrence：
 *
 * 时长一侧（都不接 [pointsWord]，英文用不随单复数变化的 `min` / `s` / `hr`，§11）：
 * · [remainTime] `剩余 ${min}分${sec}秒 · …`：组件里唯一一处"裸 分 = 分钟"；
 * · [cheerEveryMinute] `✨ 每一分钟都在积累成长！`：`分钟` 作为词整体出现；
 * · [tipDrinkWater] `💧 喝杯水，补充水分`：`水分` = moisture，**既不是分钟也不是积分**，
 *   英文 `Have a glass of water` 里没有单位，别把它当成漏译的单位字段去"修正"；
 * · [todayFocusSummary] `今日已专注 $minutes 分钟$overHours`：宿主页 :145，`分钟` 前**有空格**
 *   （与 [FocusStrings.minutesStatValue] 的 `45分钟` 无空格不同，不能合并）；
 * · [overHoursNote] `（超${hours}小时）`：同一行的分支注记，单位是小时；
 * · [focusRecordDescription] `专注「$target」${minutes}分钟`：宿主页 :174，`分钟` 前**没有**空格。
 *
 * 积分一侧（英文词形必须出自 [pointsWord]，数值 1 在本页是**真实取值**：`points = minutes / 5`，
 * 15 分钟 → 3 分、专注不足 5 分钟 → 0 分不上报，但 :246 那条规则句写的就是 1 分）：
 * · [pointsPerFocusRule] `每专注 $minutes 分钟 = +$points 分（所有时长均有效）`（宿主页 :246）
 *   —— **同一行两种含义并存**，所以做成两个具名实参 `minutes` / `points`：
 *   时长槽只能渲染成 `分钟`/`min`、积分槽只能渲染成 `分`/`+N ${pointsWord(points)}`，
 *   互换在类型上写不出来；判错方向的渲染是 "Focus 5 points = +1 min" 这种句子。
 * · [pointsLadderNote]（:247）`15分钟+3分 · …` 同形四组，英文四处词形都走 [pointsWord]。
 * · [focusDoneSnackbar]（:178）`…+${points}分已提交家长审核`：页面传的是算好的 `points = minutes / 5`。
 * · [pointsRulesTitle]（:245）`💡 积分规则`：`积分` 是名词、不接数值，所以不接 [pointsWord]。
 *
 * **本表刻意没有第三个 `分` 字段**：宿主页的时长 chip（:197 / :212 的 `${min}分`）走
 * [durationChip]，单位实参由页面从 [CommonStrings.minuteLabel] 取来传入，
 * 全工程 `分` 的两个含义仍然只有 `pointsUnit` / `minuteLabel` 两个出处（Task 6+8 的字面量同形异义
 * 守卫才能继续管住它们）。以上事实分别由 `…MinuteUnitsAreMinutesNotPoints`、
 * `…PointsAndMinutesSharingOneLineUseSeparateSlots`、`…DurationChipReusesTheCommonMinuteLabel` 三处钉住。
 *
 * 非 1:1 改动（Ruling 20 四条全部满足并在此披露）只有一处形态：[deepBreathCount] 的中文原文是
 * `第 ${(breathCycle + 1).coerceAtMost(totalBreathCycles)}/$totalBreathCycles 次深呼吸`，
 * 那个 `.coerceAtMost(...)` 是**在字面量内部**算出来的数值，函数化后它变成实参
 * `strings.deepBreathCount((breathCycle + 1).coerceAtMost(totalBreathCycles), totalBreathCycles)`。
 * (a) 表只接管了文案 token（`第`、`/`、` 次深呼吸`）；(b) 中文态输出逐字节相同，由字面量断言证明；
 * (c) 表达式本身、控制流、组件、布局参数一律未动，只是把它从字符串模板里挪到实参位；(d) 见此处与报告。
 * [remainTime] 的两个实参在页面里本就是 `val min` / `val sec`，所以那条是纯字面量替换。
 * **本页这一处是"全 Task 7 七处非 1:1 位点"里的第 7 处**，其余 6 处（全在 [FocusStrings]）逐一点名，
 * 清单在 `FocusStrings` 的类注释里——两边合起来才是完整清单，只读本表会漏数。
 *
 * **Task 7b 在本表新增的非 1:1 位点（同样逐条点名，四条各核一遍；形态仍只有"表达式挪到实参位"一类，
 * 外加两处本表独有的形态）**：
 * 1. [overHoursNote] + [todayFocusSummary]（宿主页 :145）—— **"模板里带 if"那一类**，也就是
 *    Task 7 注释早就预告过的那条。原来一条字面量 `今日已专注 $todayFocusMin 分钟${if (>=60) "（超…）" else ""}`
 *    拆成两个字段，页面写成 `strings.todayFocusSummary(m, if (m >= 60) strings.overHoursNote(m / 60) else "")`。
 *    (a) 挪动的 token 是 `今日已专注`、` 分钟`、`（超`、`小时）` 全是文案；
 *    (b) 中文态逐字节相同（两个分支各一条字面量断言：`今日已专注 90 分钟（超1小时）` / `今日已专注 25 分钟`）；
 *    (c) 条件表达式 `todayFocusMin >= 60`、除法 `/ 60`、`else ""`、Text 组件与样式参数一律未动，
 *        只是将原本就地求值的模板子表达式挪进实参位；(d) 此处 + 报告。
 * 2. [durationChip]（:197 / :212）—— **本表独有的第二类形态：单位来自另一张表**。
 *    原来 `Text("${min}分")` 里的 `分` 由 [CommonStrings.minuteLabel] 供值，页面写成
 *    `strings.durationChip(min, common.minuteLabel)`（`val common = LocalStrings.current.common`，
 *    即 Ruling 13 的取值形态多读一个分组，不是新机制）。中文 `$minutes$unit` = `15分` 逐字节相同；
 *    英文 `$minutes $unit` 的空格留在表里、没有回到页面。
 * 3. [pointsPerFocusRule]（:246）—— 字面量里的两个数字 `5` / `1` 变成具名实参 `strings.pointsPerFocusRule(5, 1)`。
 *    这是为了把同行两种单位在**结构上**分开（见上面 `分` 段），(c) 未动任何判断；数值仍是页面里写死的规则常量，
 *    与迁移前一样不来自任何仓库/偏好（spec §5 无关）。
 * 4. [goldenPeriodHour]（:274）+ 同页私有函数 `goldenHourLabel` 加了 `@Composable`：
 *    那个函数原来返回 `"$period${hour}点"`，六个时段词与 `点` 全部进表后它必须能读到表。
 *    选择加 `@Composable` 而不是加 `strings` 形参，是为了让**调用点一行不变**（少一处越界行）；
 *    它在组合期被直接调用，`when` 的分支结构、区间常量、`hour` 实参、返回类型都未动，
 *    变的只有"字面量 → `strings.<字段>`"和函数多了一个 `@Composable` 标记。
 *
 * 行首 emoji 属于文案表而不是页面代码（Ruling 17）：中文原文就写着 `🍅 专注中`，
 * 所以 ZH 侧照抄、EN 侧同位置保留，页面不再拼 `"🍅 ${strings.x}"`。Task 7b 的宿主页同一条规矩：
 * [pageTitle] 🍅、[targetHeading] 🎯、[lengthLockedNote] ⏱️、[breathingSwitchTitle] 🧘、
 * [pointsRulesTitle] 💡、[schulteEntryButton] 🎯、[memoryEntryButton] 🧠 与三条 lambda
 * （[goldenHourNowNote] 🌟、[goldenHourGeneralNote] 💡、[focusDoneSnackbar] 🍅）的行首 emoji 都在表内。
 * 由
 * `StringsCopyTest.emojiStaysInsideThePomodoroAndFocusTables` 钉住。
 * brief Step 2 的示例值 `phaseFocus = "Focusing"` 没带 emoji，这里按本工程的 emoji 入表约定补成
 * `🍅 Focusing`；示例串 `remainTime` 的英文逐字照用。
 *
 * 术语按设计文档 §11：番茄钟 = Pomodoro、专注 = Focus、深呼吸 = Breath、积分 = Points、
 * 专注力训练 = Focus training、舒尔特方格 = Schulte grid、打卡 = Check in（本页未出现）。
 * Task 7b 新用的三个词已按规矩先补进 §11：黄金专注时段 = golden focus window、
 * 记忆广度 = memory span、每日计划 = today's plan。
 * [breakOverSummary] / [completedPomodorosLine] 的英文带 one/other（`completedPomodoros` 首完成就是 1，
 * "1 Pomodoros" 是真会出现在设备上的语法错误）；`、` 之类分隔符与 `·` 一样归文案表管。
 *
 * **宿主页有两条文案会被写进数据库**（不是 seed，是用户动作记录，spec §5 的禁令针对的是 seed）：
 * [focusRecordDescription] 交给 `pointApprovalRepository.submitApproval(…, description, …)`、
 * `focusRepository.recordFocusSession(minutes, focusTarget, 0)` 的 `focusTarget` 又来自
 * [targetFreeStudy] 等四个预设项。语言切换后**新**记录的描述用当下界面语言、历史记录不变
 * （`focusTarget` 的 `remember { mutableStateOf(...) }` key 里没有 strings，切换语言不会重选默认项，
 * 这是刻意保留的：加了 key 会把用户已选的目标冲掉）。这条后果写进报告 §"concerns"。
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

    // ===== ui/focus/ChildPomodoroScreen.kt（Task 7b 宿主页面）=====
    // 32 条去重字面量 → 33 个字段：`自由学习`（预设项 + focusTarget 初值）与 `${min}分`
    // （专注 chips + 休息 chips）各两处同值同义、共用一个字段，第 145 行一条按"时长 + 超 N 小时注记"
    // 拆成 todayFocusSummary / overHoursNote 两个字段。逐条说明见类注释的 Task 7b 段。
    val pageTitle: String,
    val targetHeading: String,
    val targetFreeStudy: String,
    val targetReviewMistakes: String,
    val targetPreviewLesson: String,
    val targetFinishHomework: String,
    val goldenHourNowNote: (label: String) -> String,
    val goldenHourGeneralNote: (label: String) -> String,
    val goldenPeriodEarlyMorning: String,
    val goldenPeriodMorning: String,
    val goldenPeriodMidday: String,
    val goldenPeriodAfternoon: String,
    val goldenPeriodEvening: String,
    val goldenPeriodLateNight: String,
    val goldenPeriodHour: (period: String, hour: Int) -> String,
    val todayFocusSummary: (minutes: Int, overHoursNote: String) -> String,
    val overHoursNote: (hours: Int) -> String,
    val completedPomodorosLine: (count: Int) -> String,
    val focusRecordDescription: (target: String, minutes: Int) -> String,
    val focusDoneSnackbar: (target: String, points: Int) -> String,
    val focusLengthLabel: String,
    val breakLengthLabel: String,
    val durationChip: (minutes: Int, minuteLabel: String) -> String,
    val lengthLockedNote: String,
    val breathingSwitchTitle: String,
    val breathingStateSkipped: String,
    val breathingStateOn: String,
    val pointsRulesTitle: String,
    val pointsPerFocusRule: (minutes: Int, points: Int) -> String,
    val pointsLadderNote: String,
    val lockScreenNote: String,
    val schulteEntryButton: String,
    val memoryEntryButton: String,
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

    // ===== ui/focus/ChildPomodoroScreen.kt（Task 7b）：以下每个中文值逐字来自该页迁移前的对应行 =====
    pageTitle = "🍅 番茄钟",                                              // :62
    targetHeading = "🎯 本次专注目标",                                    // :80
    targetFreeStudy = "自由学习",                                         // :42 与 :44（focusTarget 初值）
    targetReviewMistakes = "复习错题",                                    // :42
    targetPreviewLesson = "预习新课",                                     // :42
    targetFinishHomework = "完成作业",                                    // :42
    goldenHourNowNote = { label -> "🌟 现在正是你的黄金专注时段（$label），效率最高，抓紧专注！" },   // :120
    goldenHourGeneralNote = { label -> "💡 你的黄金专注时段是 $label，那时专注效率最高哦～" },        // :121
    goldenPeriodEarlyMorning = "早上",                                    // :267
    goldenPeriodMorning = "上午",                                         // :268
    goldenPeriodMidday = "中午",                                          // :269
    goldenPeriodAfternoon = "下午",                                       // :270
    goldenPeriodEvening = "晚上",                                         // :271
    goldenPeriodLateNight = "深夜",                                       // :272
    goldenPeriodHour = { period, hour -> "$period${hour}点" },            // :274
    todayFocusSummary = { minutes, overHours -> "今日已专注 $minutes 分钟$overHours" },               // :145
    overHoursNote = { hours -> "（超${hours}小时）" },                    // :145 的 ${if} 分支内部
    completedPomodorosLine = { count -> "完成 $count 个番茄钟 🍅×$count" },                            // :152
    focusRecordDescription = { target, minutes -> "专注「$target」${minutes}分钟" },                   // :174
    focusDoneSnackbar = { target, points -> "🍅 「$target」专注完成！+${points}分已提交家长审核" },   // :178
    focusLengthLabel = "专注:",                                           // :190
    breakLengthLabel = "休息:",                                           // :205
    durationChip = { minutes, unit -> "$minutes$unit" },                  // :197 与 :212，单位来自 common.minuteLabel
    lengthLockedNote = "⏱️ 计时中，时长设置已锁定",                        // :217
    breathingSwitchTitle = "🧘 专注前呼吸引导",                            // :226
    breathingStateSkipped = "已跳过",                                     // :228
    breathingStateOn = "开启",                                            // :228
    pointsRulesTitle = "💡 积分规则",                                     // :245
    pointsPerFocusRule = { minutes, points -> "每专注 $minutes 分钟 = +$points 分（所有时长均有效）" }, // :246
    pointsLadderNote = "15分钟+3分 · 25分钟+5分 · 30分钟+6分 · 45分钟+9分",                            // :247
    lockScreenNote = "息屏后仍会继续计时，放心锁屏专注！",                  // :248
    schulteEntryButton = "🎯 专注力训练·舒尔特方格（专注前热身）",           // :254
    memoryEntryButton = "🧠 记忆广度训练·数字记忆（练工作记忆）",            // :257
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

    // ===== ui/focus/ChildPomodoroScreen.kt（Task 7b）=====
    // 积分词形一律出自 pointsWord（本文件唯一出处），时长词形用不随单复数变化的 min / hr（§11）。
    pageTitle = "🍅 Pomodoro",
    targetHeading = "🎯 Goal for this session",
    targetFreeStudy = "Free study",
    targetReviewMistakes = "Review mistakes",
    targetPreviewLesson = "Preview tomorrow's lesson",
    targetFinishHomework = "Finish homework",
    goldenHourNowNote = { label ->
        "🌟 Right now is your golden focus window ($label) — that's your best time, make it count!"
    },
    goldenHourGeneralNote = { label -> "💡 Your golden focus window is $label — that's when you focus best." },
    goldenPeriodEarlyMorning = "Early morning",
    goldenPeriodMorning = "Morning",
    goldenPeriodMidday = "Midday",
    goldenPeriodAfternoon = "Afternoon",
    goldenPeriodEvening = "Evening",
    goldenPeriodLateNight = "Late night",
    goldenPeriodHour = { period, hour -> "$period · $hour:00" },
    todayFocusSummary = { minutes, overHours -> "You've focused $minutes min today$overHours" },
    overHoursNote = { hours -> " (over $hours hr)" },
    completedPomodorosLine = { count ->
        if (count == 1) "Completed 1 Pomodoro 🍅×1" else "Completed $count Pomodoros 🍅×$count"
    },
    focusRecordDescription = { target, minutes -> "Focus on $target for $minutes min" },
    focusDoneSnackbar = { target, points ->
        "🍅 Focus on '$target' done! +$points ${pointsWord(points)} sent for approval"
    },
    focusLengthLabel = "Focus:",
    breakLengthLabel = "Break:",
    durationChip = { minutes, unit -> "$minutes $unit" },
    lengthLockedNote = "⏱️ Timer running — length settings are locked",
    breathingSwitchTitle = "🧘 Breathing guide before focusing",
    breathingStateSkipped = "Skipped",
    breathingStateOn = "On",
    pointsRulesTitle = "💡 Points rules",
    pointsPerFocusRule = { minutes, points ->
        "Every $minutes min of focus = +$points ${pointsWord(points)} (every length counts)"
    },
    pointsLadderNote =
        "15 min +3 ${pointsWord(3)} · 25 min +5 ${pointsWord(5)} · 30 min +6 ${pointsWord(6)} · 45 min +9 ${pointsWord(9)}",
    lockScreenNote = "The timer keeps running with your screen off — lock it and focus on.",
    schulteEntryButton = "🎯 Focus training · Schulte grid (warm-up before focusing)",
    memoryEntryButton = "🧠 Memory span training · number memory (trains working memory)",
)

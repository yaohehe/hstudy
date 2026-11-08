package com.hstudy.app.i18n

import com.hstudy.app.ui.dt.strFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [englishTableContainsNoChineseLeftovers] 的显式豁免表：语言的**自称词**（endonym）。
 *
 * 为什么"中文"可以出现在英文文案表里：语言选项在两种 locale 下都写作该语言自己的名字
 * （中文 / English），不随界面语言翻译——这是语言切换控件的通行惯例（iOS/Android 系统设置、
 * 各家 App 均如此），否则切到英文的用户看到的是 "Chinese"，反而不容易认出自己在选哪一种语言，
 * 而且 "English/中文" 一对里混进译名会破坏"两项都是自称"的对称性。
 * 该同值由 [languageOptionLabelsAreEndonymsInBothLocales] 用等值断言钉死，
 * 两个自称词字段也因此刻意不进 assertDiffers 漏译清单（见 Ruling 6）。
 *
 * 维护规则：只登记确属自称词的 token。**不要**为了跑绿把漏译字段加进来，
 * 也**不要**把 SettingsStringsEn.languageZh 顺手译成 "Chinese" 来消除本豁免（见 SettingsStrings.kt 头注释）。
 * 完整性没有被削弱：新增字段直接抄中文进英文表仍会被整表扫描抓到（已用变异测试证明：
 * 把 languageSystem 的 EN 值改成"跟随系统"后，本断言是唯一失败的断言）。
 */
private val EN_TABLE_ENDONYMS = setOf("中文")

/**
 * 英文文案表漏译守卫（Task 4-11 复制本文件的模板）。
 *
 * 两条断言互补，缺一不可：
 * - [englishTableContainsNoChineseLeftovers]：整表扫描，不依赖任何手工维护的字段清单，
 *   因此"新增字段直接把中文字面量抄进英文表"也会被抓到（豁免表见 [EN_TABLE_ENDONYMS]，只放自称词）；
 * - [roleCopyDiffersBetweenLanguages] / [settingsLanguageCopyDiffers]：逐字段 ZH≠EN，
 *   报错信息能点名是哪条中文没译。
 *
 * 已知上限（评审指出）：data class 的 `toString()` 打印函数型字段本身（本机实测是 lambda 对象的
 * `类名$$Lambda/0x…@哈希`），但**不含它们的返回值**，所以整表扫描对插在函数里的中文是瞎的
 * （Task 6 的 CommonStrings 起出现，Task 7 起大量出现）。
 * 补偿办法是硬性约定：**每个函数型字段都必须有显式双语断言，且期望串逐字打出来**
 * （见 [commonInterpolatedCopyPinsBothLanguages] / [taskListInterpolatedCopyPinsBothLanguages]），
 * 不允许"调 lambda 再和 lambda 自己拼的串比"——那种断言对漏译和语序错都是自证通过的。
 * 该约定是计划全局约束里的 Ruling 20-b（审查闸门强制项：任务审查必须逐字段列出 lambda 与
 * 其断言位置，漏一个即 spec ❌），不是可选加固；类型系统层面不做 `Copy1` 之类的值类型改造。
 *
 * 另有一类 assertDiffers 也抓不到的错：**同形异义**字段的英文值互换（见 [commonUnitLabelsPinnedByLiteral]）。
 */
class StringsCopyTest {
    @Test
    fun roleCopyDiffersBetweenLanguages() {
        val zh = AppStringsZh.role
        val en = AppStringsEn.role
        assertDiffers(
            zh.brandTitle to en.brandTitle,
            zh.tagline to en.tagline,
            zh.parentEntry to en.parentEntry,
            zh.parentDesc to en.parentDesc,
            zh.childEntry to en.childEntry,
            zh.childDesc to en.childDesc,
        )
    }

    /**
     * 整表兜底：英文聚合根 toString() 里不应出现任何汉字（U+4E00–U+9FFF）。
     *
     * 覆盖上面那份手工清单之外的所有 String 字段；标点/空白级差异不属于本断言的职责，
     * 由逐字段清单负责报错。
     *
     * 唯一的豁免是 [EN_TABLE_ENDONYMS]（语言自称词"中文"）：先从渲染串里剔除这些 token，
     * 再扫剩余汉字，因此新增字段的漏译不会被绕过；报错信息把残留字符直接打出来，便于定位。
     */
    @Test
    fun englishTableContainsNoChineseLeftovers() {
        var rendered = AppStringsEn.toString()
        EN_TABLE_ENDONYMS.forEach { token -> rendered = rendered.replace(token, "") }
        val leftovers = rendered.filter { it in '一'..'鿿' }
        assertFalse(
            leftovers.isNotEmpty(),
            "英文文案表中残留未翻译的中文字面量：$leftovers（已豁免自称词 $EN_TABLE_ENDONYMS）",
        )
    }

    /**
     * partialTranslationNote 只在**英文态**渲染（spec §7："英文态在该页附一行说明"）：
     * 这句话是给英文读者看的——他打开设置看到界面还是中文，需要知道"是还没适配"而不是"有 bug"。
     * 中文态读同一句是不通的，Task 11 之后更是直接变成假话，所以页面用
     * `LocalDisplayLanguage.current == DisplayLanguage.EN` 把它守卫起来。
     *
     * 本文件**无法**单测这条渲染分支：它发生在组合期，而仓库没有 Compose UI 测试依赖
     * （Task 12 之前的约定是不新增该依赖）。能被自动证明的只有下面这组 ZH≠EN 漏译守卫，
     * 以及整表扫描；守卫本身靠 ParentSettingsScreen 处的注释与代码路径承担。
     * ZH 侧的值仍然保留：data class 要求两份表字段对齐，Task 11 会复用同一字段。
     */
    @Test
    fun settingsLanguageCopyDiffers() {
        assertDiffers(
            AppStringsZh.settings.languageTitle to AppStringsEn.settings.languageTitle,
            AppStringsZh.settings.languageNote to AppStringsEn.settings.languageNote,
            AppStringsZh.settings.partialTranslationNote to AppStringsEn.settings.partialTranslationNote,
        )
    }

    @Test
    fun languageOptionLabelsAreEndonymsInBothLocales() {
        // 语言选项名不随界面语言翻译：中文/English 在两种语言下都这样显示
        assertEquals("中文", AppStringsZh.settings.languageZh)
        assertEquals("中文", AppStringsEn.settings.languageZh)
        assertEquals("English", AppStringsZh.settings.languageEn)
        assertEquals("English", AppStringsEn.settings.languageEn)
    }

    /**
     * 分区标题的装饰性 emoji 属于文案表，不属于页面代码。
     *
     * 该页既有四个分区标题都写成 "🔒 家长密码" 这种"emoji 在字面量内部"的形式，
     * Task 11 迁移这些标题时是按整串搬到表里做的；如果新代码把 emoji 拼在页面里
     * （`"🌐 ${strings.languageTitle}"`），就凭空多出一条页面内 UI 字面量，
     * 既违反"UI 文案只在文案表"的约定，也会让 Task 11 的整串替换模式对不上。
     * 本断言钉住约定：两种 locale 的 languageTitle 都以 🌐 开头，页面只渲染 strings.languageTitle。
     */
    @Test
    fun sectionTitleCarriesItsEmojiInsideTheTable() {
        assertTrue(
            AppStringsZh.settings.languageTitle.startsWith("🌐"),
            "ZH languageTitle 应自带 🌐，实际：${AppStringsZh.settings.languageTitle}",
        )
        assertTrue(
            AppStringsEn.settings.languageTitle.startsWith("🌐"),
            "EN languageTitle 应自带 🌐，实际：${AppStringsEn.settings.languageTitle}",
        )
    }

    /**
     * 公共控件（Task 6，`ui/common/CommonComponents.kt`）漏译守卫。
     *
     * 清单按值去重后是 5 条，但落成 6 个字段：`"分"` 在该文件出现两次而语义不同——
     * 一次是积分徽章的单位（PointsBadge:127），一次是分钟步进器的标签（HourMinuteStepper:159）。
     * 去重把它们合成一条，英文却必须分写（`points` vs `min`），所以拆成 pointsUnit / minuteLabel。
     * 教训：清单的"去重条数"是**下限**，不能当成字段数直接验收。
     *
     * 另：assertDiffers 只比较**同一字段的 ZH vs EN**，绝不跨字段比——
     * `pointsUnit` 与 `minuteLabel` 的中文都是"分"，若把 ZH(分) vs EN(min) 写成跨字段配对，
     * 两条断言会互相掩盖。**但同形异义本身还有第二种掩盖**：互换两个字段的英文值时，
     * 每个字段各自仍然 ZH≠EN，本清单完全无感。那一条由
     * [commonUnitLabelsPinnedByLiteral] 用字面量钉死。
     * 函数型字段（decreaseLabel / increaseLabel）不在此清单内，
     * 由 [commonInterpolatedCopyPinsBothLanguages] 覆盖。
     */
    @Test
    fun commonCopyDiffers() {
        val zh = AppStringsZh.common
        val en = AppStringsEn.common
        assertDiffers(
            zh.backContentDescription to en.backContentDescription,
            zh.pointsUnit to en.pointsUnit,
            zh.hourLabel to en.hourLabel,
            zh.minuteLabel to en.minuteLabel,
        )
    }

    /**
     * 同形异义单位的字面量钉死（Task 6 评审 Important 1）。
     *
     * [commonCopyDiffers] 只断言"同一字段的 ZH ≠ EN"，因此**对互换两个英文值完全无感**：
     * 把 `pointsUnit` 写成 "min"、`minuteLabel` 写成 "points"，四条 ZH≠EN 配对依旧全部成立，
     * 整表 CJK 扫描也照样干净（两边都是英文），但设备上会渲染出 `⭐ 12 min`，
     * 且分钟步进器下面挂着 `points` 标签。这类"两个字段中文同形、英文分工"的字段
     * 只能靠**逐字打出来的字面量**钉住，不能靠比较关系钉住——所以本测试不比较任何两个字段，
     * 只把每个字段各自钉在预期的那一个串上。
     *
     * 覆盖范围：pointsUnit / minuteLabel（中文同为 `分`）+ hourLabel（`hr` / `min` 同属步进器
     * 标签，互换是同一类错误，顺手一起钉）。已用变异测试证明本测试是唯一红的：
     * 交换 CommonStringsEn 的 pointsUnit 与 minuteLabel 后，其余测试全部通过。
     */
    @Test
    fun commonUnitLabelsPinnedByLiteral() {
        assertEquals("分", AppStringsZh.common.pointsUnit)
        assertEquals("points", AppStringsEn.common.pointsUnit)
        assertEquals("分", AppStringsZh.common.minuteLabel)
        assertEquals("min", AppStringsEn.common.minuteLabel)
        assertEquals("时", AppStringsZh.common.hourLabel)
        assertEquals("hr", AppStringsEn.common.hourLabel)
    }

    /**
     * 函数型字段的双语显式断言（spec §8.3）——本工程第一批。
     *
     * 期望串全部**手打钉死**，不是把 lambda 的返回值再拼一遍：
     * 若写成 `assertEquals("减少" + label, ...)`，那么把中文原文塞进英文表的漏译、
     * 或者英文语序写歪（"Decrease by hr 5"）都照样绿，等于没测。
     * 参数用页面真实传入的单位标签（中文态 "时"/"分"、英文态 "hr"/"min"），
     * 这样断言覆盖的就是设备上真正渲染出来的那两种组合。
     */
    @Test
    fun commonInterpolatedCopyPinsBothLanguages() {
        // 中文态必须逐字等于迁移前的插值串："减少$label" / "增加$label"
        assertEquals("减少时", AppStringsZh.common.decreaseLabel("时"))
        assertEquals("减少分", AppStringsZh.common.decreaseLabel("分"))
        assertEquals("增加时", AppStringsZh.common.increaseLabel("时"))
        assertEquals("增加分", AppStringsZh.common.increaseLabel("分"))
        // 英文态是 a11y 朗读文本，按英文语序写，不套用中文句式
        assertEquals("Decrease hr", AppStringsEn.common.decreaseLabel("hr"))
        assertEquals("Decrease min", AppStringsEn.common.decreaseLabel("min"))
        assertEquals("Increase hr", AppStringsEn.common.increaseLabel("hr"))
        assertEquals("Increase min", AppStringsEn.common.increaseLabel("min"))
    }

    /**
     * 孩子任务列表页（Task 8，`ui/child/ChildTaskListScreen.kt`）漏译守卫：16 个字面量 → 16 个字段，
     * 一一对应（本页没有 CommonComponents 那种"同形异义"合并，也没有一条字面量需要拆成两个字段）。
     *
     * 7 个函数型字段不在本清单里（`assertDiffers` 收 `Pair<String, String>`，函数不可比），
     * 由下面两个测试覆盖；整表 CJK 扫描同样看不见它们。
     */
    @Test
    fun taskListCopyDiffers() {
        val zh = AppStringsZh.taskList
        val en = AppStringsEn.taskList
        assertDiffers(
            zh.checkInTitle to en.checkInTitle,
            zh.filterAll to en.filterAll,
            zh.filterBasic to en.filterBasic,
            zh.filterEnhancement to en.filterEnhancement,
            zh.filterHabits to en.filterHabits,
            zh.undoButton to en.undoButton,
            zh.completedContentDescription to en.completedContentDescription,
            zh.mandatoryBadge to en.mandatoryBadge,
            zh.proofButton to en.proofButton,
        )
    }

    /**
     * 7 条插值文案的双语显式断言（spec §8.3）。期望串逐字手打，参数取页面真实传入的形状
     * （任务标题、积分整数、成就名列表），这样钉住的是设备上真正渲染出来的句子。
     *
     * 中文侧的期望串同时是"迁移前后字节相同"的证据：空格位置照抄源文件
     * （`扣回 $clawedBack 分` 有空格、`+$earned分` 没有），任何人改动中文措辞都会在这里红。
     */
    @Test
    fun taskListInterpolatedCopyPinsBothLanguages() {
        assertEquals(
            "↩️ 已撤销 每日阅读，扣回 5 分",
            AppStringsZh.taskList.undoClawedBackMessage("每日阅读", 5),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading · 5 points removed",
            AppStringsEn.taskList.undoClawedBackMessage("Daily reading", 5),
        )
        assertEquals(
            "↩️ 已撤销 每日阅读（尚未审批，无积分变动）",
            AppStringsZh.taskList.undoPendingApprovalMessage("每日阅读"),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading (not approved yet, so no points changed)",
            AppStringsEn.taskList.undoPendingApprovalMessage("Daily reading"),
        )
        assertEquals(
            "🏅 解锁成就：启航、周冠军！",
            AppStringsZh.taskList.achievementUnlockedMessage(listOf("启航", "周冠军")),
        )
        assertEquals(
            "🏅 Achievements unlocked: Blast Off, Weekly Champion!",
            AppStringsEn.taskList.achievementUnlockedMessage(listOf("Blast Off", "Weekly Champion")),
        )
        assertEquals("✅ 打卡成功！+5分待家长审核", AppStringsZh.taskList.checkInSuccessMessage(5))
        assertEquals(
            "✅ Checked in! +5 points pending approval",
            AppStringsEn.taskList.checkInSuccessMessage(5),
        )
        assertEquals("✅ 每日阅读 +5分（待审核）", AppStringsZh.taskList.checkInUndoPrompt("每日阅读", 5))
        assertEquals(
            "✅ Daily reading +5 points (pending approval)",
            AppStringsEn.taskList.checkInUndoPrompt("Daily reading", 5),
        )
        assertEquals("+5分", AppStringsZh.taskList.pointsEarnedLabel(5))
        assertEquals("+5 points", AppStringsEn.taskList.pointsEarnedLabel(5))
        assertEquals("奖励+3分", AppStringsZh.taskList.bonusPointsLabel(3))
        assertEquals("Reward +3 points", AppStringsEn.taskList.bonusPointsLabel(3))
    }

    /**
     * 英文 one/other 分支（spec §3 末句 + §8.3 的"覆盖英文 one/other"）。
     *
     * 本页 5 条把积分数值插进句子的文案都必须走单复数：`+1 points` 是英文语法错误，
     * 而 1 分在本工程是真实取值（`TaskEntity.points` 可为 1，`bonusPoints` 同理，
     * `undoTaskCheckIn` 返回值也可为 1），不是假想边界。
     * 同时钉住**中文没有单复数**：中文侧 1 与 5 同形，防止有人把英文的 if 复制回中文表。
     */
    @Test
    fun taskListEnglishPluralsCoverOneAndOther() {
        assertEquals("+1 point", AppStringsEn.taskList.pointsEarnedLabel(1))
        assertEquals("+2 points", AppStringsEn.taskList.pointsEarnedLabel(2))
        assertEquals("Reward +1 point", AppStringsEn.taskList.bonusPointsLabel(1))
        assertEquals("Reward +8 points", AppStringsEn.taskList.bonusPointsLabel(8))
        assertEquals(
            "✅ Checked in! +1 point pending approval",
            AppStringsEn.taskList.checkInSuccessMessage(1),
        )
        assertEquals(
            "✅ Daily reading +1 point (pending approval)",
            AppStringsEn.taskList.checkInUndoPrompt("Daily reading", 1),
        )
        assertEquals(
            "↩️ Check-in undone for Daily reading · 1 point removed",
            AppStringsEn.taskList.undoClawedBackMessage("Daily reading", 1),
        )
        // 成就同理：一条用单数，两条用复数（上面那条已钉复数）
        assertEquals(
            "🏅 Achievement unlocked: Blast Off!",
            AppStringsEn.taskList.achievementUnlockedMessage(listOf("Blast Off")),
        )
        // 中文没有单复数变化：1 与 5 同形，别把英文的 if 复制回中文表
        assertEquals("+1分", AppStringsZh.taskList.pointsEarnedLabel(1))
        assertEquals("奖励+1分", AppStringsZh.taskList.bonusPointsLabel(1))
        assertEquals("✅ 打卡成功！+1分待家长审核", AppStringsZh.taskList.checkInSuccessMessage(1))
    }

    /**
     * 番茄钟组件（Task 7，`ui/common/PomodoroTimer.kt`）漏译守卫：去重 31 条字面量 → 31 个字段，
     * 一一对应（其中 3 条含插值，见 [pomodoroInterpolatedCopyPinsBothLanguages]）。
     *
     * `☕ 休息中` / `🍅 专注中` 各出现在两个位置（运行通知标题、圆环下方状态文字），
     * `结束` 出现在两个按钮（RUNNING / PAUSED）——**同一条字面量共用一个字段**，
     * 所以"出现 34 次 / 去重 31 条"落地成 31 个字段，而不是 34 个。
     */
    @Test
    fun pomodoroCopyDiffers() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertDiffers(
            zh.phaseBreak to en.phaseBreak,
            zh.phaseFocus to en.phaseFocus,
            zh.cheerHalfway to en.cheerHalfway,
            zh.cheerProudest to en.cheerProudest,
            zh.cheerAlmostDone to en.cheerAlmostDone,
            zh.cheerBrainWorking to en.cheerBrainWorking,
            zh.cheerEveryMinute to en.cheerEveryMinute,
            zh.breatheIn to en.breatheIn,
            zh.breatheOut to en.breatheOut,
            zh.phaseStartReady to en.phaseStartReady,
            zh.phaseBreakOver to en.phaseBreakOver,
            zh.breathingGuide to en.breathingGuide,
            zh.tipWalkAround to en.tipWalkAround,
            zh.tipLookFarAway to en.tipLookFarAway,
            zh.tipDrinkWater to en.tipDrinkWater,
            zh.tipBreathe to en.tipBreathe,
            zh.tipStretch to en.tipStretch,
            zh.tipListenMusic to en.tipListenMusic,
            zh.breakCardTitle to en.breakCardTitle,
            zh.breakCardNote to en.breakCardNote,
            zh.startFocusButton to en.startFocusButton,
            zh.skipBreathingButton to en.skipBreathingButton,
            zh.pauseButton to en.pauseButton,
            zh.stopButton to en.stopButton,
            zh.resumeButton to en.resumeButton,
            zh.skipBreakButton to en.skipBreakButton,
            zh.startNextRoundButton to en.startNextRoundButton,
            zh.restFirstButton to en.restFirstButton,
        )
    }

    /**
     * 专注统计页 + 专注力训练页（Task 7，`ui/focus/ChildFocusStatsScreen.kt` /
     * `ChildFocusTrainingScreen.kt`）漏译守卫：两页去重后 16 + 17 = 33 条字面量，跨页同值的
     * `🎯 专注力训练` 只算一个字段、`${todayMinutes}分钟` 与 `${totalMinutes}分钟` 共用一个字段，
     * 于是落成 **31 个字段，其中 11 条函数型**（函数型不在本清单里，见
     * [focusInterpolatedCopyPinsBothLanguages]）。
     */
    @Test
    fun focusCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.statsTitle to en.statsTitle,
            zh.todayFocusLabel to en.todayFocusLabel,
            zh.totalFocusLabel to en.totalFocusLabel,
            zh.approxLabel to en.approxLabel,
            zh.trainingTitle to en.trainingTitle,
            zh.sessionsLabel to en.sessionsLabel,
            zh.gridBestLabel to en.gridBestLabel,
            zh.trainingBlurb to en.trainingBlurb,
            zh.last7DaysTitle to en.last7DaysTitle,
            zh.recordsTitle to en.recordsTitle,
            zh.emptyRecordsHint to en.emptyRecordsHint,
            zh.schulteTitle to en.schulteTitle,
            zh.schulteHowTo to en.schulteHowTo,
            zh.verdictExcellent to en.verdictExcellent,
            zh.verdictGood to en.verdictGood,
            zh.verdictPractice to en.verdictPractice,
            zh.newRecordBanner to en.newRecordBanner,
            zh.tryAgainButton to en.tryAgainButton,
            zh.dailyPracticeHint to en.dailyPracticeHint,
            zh.startTrainingButton to en.startTrainingButton,
        )
    }

    /**
     * PomodoroStrings 3 条插值的双语断言（Ruling 20-b：lambda 字段整表 CJK 守卫看不见，
     * 必须逐字钉死，期望串手打而不是把 lambda 再拼一遍）。
     *
     * 中文侧的期望串同时是"迁移前后逐字节相同"的证据：空格位置照抄源文件
     * （`剩余 ${min}分${sec}秒` 只在"剩余"后有空格、分/秒前后没有；`第 X/Y 次深呼吸` 同理），
     * 任何人把空格挪动一下都会在这里红。
     */
    @Test
    fun pomodoroInterpolatedCopyPinsBothLanguages() {
        assertEquals("剩余 12分30秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(12, 30))
        assertEquals("12 min 30 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
        assertEquals("第 2/3 次深呼吸", AppStringsZh.pomodoro.deepBreathCount(2, 3))
        assertEquals("Deep breath 2 of 3", AppStringsEn.pomodoro.deepBreathCount(2, 3))
        assertEquals(
            "🎉 休息结束！已完成 4 个番茄钟，继续加油！",
            AppStringsZh.pomodoro.breakOverSummary(4),
        )
        assertEquals(
            "🎉 Break over! 4 Pomodoros done — keep going!",
            AppStringsEn.pomodoro.breakOverSummary(4),
        )
    }

    /**
     * FocusStrings 11 条插值的双语断言（同上，逐字手打）。
     *
     * 秒数类字段（[FocusStrings.secondsStatValue] / [FocusStrings.elapsedValue] /
     * [FocusStrings.personalBestValue] / [FocusStrings.finishedLine] /
     * [FocusStrings.trainingDoneSnackbar]）的参数类型是**已格式化好的字符串**而不是 Float，
     * 因为页面上的数值来自 `strFormat("%.1f", …)`；断言用 "12.3" 这种真实形状，
     * 保证钉住的就是设备上渲染出来的那一串。
     *
     * 四个"分钟/秒"字段的中文空格位置互不相同（`45分钟` / `45 分钟` / `12.3秒` / `37.5 秒`），
     * 是源文件本来就有的差异；合并任何两个都会让其中一处中文态输出变样，Ruling 20 的 (b) 不允许，
     * 所以这里按源文件逐字钉住四个字段。
     */
    @Test
    fun focusInterpolatedCopyPinsBothLanguages() {
        assertEquals("45分钟", AppStringsZh.focus.minutesStatValue(45))
        assertEquals("45 min", AppStringsEn.focus.minutesStatValue(45))
        assertEquals("45 分钟", AppStringsZh.focus.recordMinutesValue(45))
        assertEquals("45 min", AppStringsEn.focus.recordMinutesValue(45))
        assertEquals("3小时", AppStringsZh.focus.hoursStatValue(3))
        assertEquals("3 hr", AppStringsEn.focus.hoursStatValue(3))
        assertEquals("12次", AppStringsZh.focus.sessionsStatValue(12))
        assertEquals("12 sessions", AppStringsEn.focus.sessionsStatValue(12))
        assertEquals("12.3秒", AppStringsZh.focus.secondsStatValue("12.3"))
        assertEquals("12.3 s", AppStringsEn.focus.secondsStatValue("12.3"))
        assertEquals("37.5 秒", AppStringsZh.focus.elapsedValue("37.5"))
        assertEquals("37.5 s", AppStringsEn.focus.elapsedValue("37.5"))
        assertEquals("🏆 个人最佳：31.2 秒", AppStringsZh.focus.personalBestValue("31.2"))
        assertEquals("🏆 Personal best: 31.2 s", AppStringsEn.focus.personalBestValue("31.2"))
        assertEquals("🎯 累计训练 7 次", AppStringsZh.focus.totalSessionsLine(7))
        assertEquals("🎯 7 sessions so far", AppStringsEn.focus.totalSessionsLine(7))
        assertEquals("🎉 完成！用时 28.4 秒 · 错误 2 次", AppStringsZh.focus.finishedLine("28.4", 2))
        assertEquals("🎉 Done in 28.4 s · 2 mistakes", AppStringsEn.focus.finishedLine("28.4", 2))
        assertEquals(
            "🎯 训练完成！用时32.6秒，注意力提升了！",
            AppStringsZh.focus.trainingDoneSnackbar("32.6"),
        )
        assertEquals(
            "🎯 Training done in 32.6 s — your attention is warmed up!",
            AppStringsEn.focus.trainingDoneSnackbar("32.6"),
        )
        assertEquals("下一个要点：13", AppStringsZh.focus.nextTargetLabel(13))
        assertEquals("Next, tap 13", AppStringsEn.focus.nextTargetLabel(13))
    }

    /**
     * Ruling 20 里唯一一处**把单位从数字格式串内部拆出来**的位点的等价性证据（报告 §7 第 4 行、
     * Task 7 评审 Important 1）：`ui/focus/ChildFocusTrainingScreen.kt:111`（迁移前）写
     * `strFormat("%.1f 秒", elapsedMs / 1000f)`，单位 ` 秒` 在格式串**内部**；
     * 迁移后写 `strings.elapsedValue(strFormat("%.1f", elapsedMs / 1000f))`，单位进了文案表。
     * 62 条模板里只有这一条不是整串搬进表里的，所以它必须有自己的断言而不是靠口头论证
     * （§4 的逐字节比对脚本把 `%.1f` 和 `${…}` 一起归一化，恰好会把它显示成"也相同"）。
     *
     * 等价性依赖两个事实，这里一个一条断言：
     * 1) [com.hstudy.app.ui.dt.strFormat] 对转换符之后的普通字符原样追加
     *    （`DateTimeCompat.kt:251-277`：处理完 `%.Nf` 后 `i = j + 1`，剩下的 ` 秒` 走 `sb.append(c)` 分支），
     *    所以"拆开写"和"整串写"输出同一串；
     * 2) [FocusStrings.elapsedValue] 的中文值就是那个带空格的 ` 秒`（已由
     *    [focusInterpolatedCopyPinsBothLanguages] 逐字钉住），即单位确实归文案表接管。
     *
     * 第三条是绝对值断言，防止前两条"两边一起变"仍然相等而自证通过（本文件的硬约定：期望串逐字打）。
     *
     * 为什么放在 StringsCopyTest 而不是新建一个 dt 测试类：这里钉的是**这次迁移没改变渲染输出**
     * 这条 Ruling 20 纪律，不是 strFormat 的通用格式行为（那是 DateTimeCompat 自己的测试职责，
     * 本任务禁止改它）；而且 Task 7 的覆盖测试就是 `--tests "*StringsCopyTest*"`，
     * 新类不在这条命令的覆盖范围内，评审闸门会看不见它。
     *
     * 反证见任务报告"Fix report (round 1)"：把断言 1 改成"strFormat 会丢掉转换符后面的文本"
     * 这一假命题、以及把 DateTimeCompat 的追加分支临时改掉，本测试都变红。
     */
    @Test
    fun elapsedValueKeepsTheSplitFormatPatternByteIdentical() {
        val elapsedMs = 37500L
        val before = strFormat("%.1f 秒", elapsedMs / 1000f)
        val after = AppStringsZh.focus.elapsedValue(strFormat("%.1f", elapsedMs / 1000f))
        assertEquals(strFormat("%.1f", elapsedMs / 1000f) + " 秒", before)
        assertEquals(before, after)
        assertEquals("37.5 秒", before)
        assertEquals("37.5 秒", after)
    }

    /**
     * 英文 one/other（spec §3 末句 + §8.3），并钉住**中文没有单复数**。
     *
     * 本页三个可数名词有真实取值 1：番茄钟个数（`completedPomodoros` 可为 1）、
     * 训练次数（`focusTrainingCount` 首完成即为 1）、错误次数（`mistakes` 可为 1），
     * 所以 "1 Pomodoros" / "1 sessions" / "1 mistakes" 是会真出现在设备上的语法错误。
     * 单位缩写（min / hr / s）按英文惯例不随数变化，故不带 if —— 一并断言，防止有人"顺手加复数"。
     */
    @Test
    fun pomodoroAndFocusEnglishPluralsCoverOneAndOther() {
        assertEquals(
            "🎉 Break over! 1 Pomodoro done — keep going!",
            AppStringsEn.pomodoro.breakOverSummary(1),
        )
        assertEquals(
            "🎉 休息结束！已完成 1 个番茄钟，继续加油！",
            AppStringsZh.pomodoro.breakOverSummary(1),
        )
        assertEquals("1 session", AppStringsEn.focus.sessionsStatValue(1))
        assertEquals("12次", AppStringsZh.focus.sessionsStatValue(12))
        assertEquals("1次", AppStringsZh.focus.sessionsStatValue(1))
        assertEquals("🎯 1 session so far", AppStringsEn.focus.totalSessionsLine(1))
        assertEquals("🎯 累计训练 1 次", AppStringsZh.focus.totalSessionsLine(1))
        assertEquals("🎉 Done in 28.4 s · 1 mistake", AppStringsEn.focus.finishedLine("28.4", 1))
        assertEquals("🎉 完成！用时 28.4 秒 · 错误 1 次", AppStringsZh.focus.finishedLine("28.4", 1))
        // 单位缩写没有单复数
        assertEquals("1 min", AppStringsEn.focus.minutesStatValue(1))
        assertEquals("1 hr", AppStringsEn.focus.hoursStatValue(1))
        assertEquals("1 min 1 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(1, 1))
    }

    /**
     * 同形异义单位（裁定 1 在本页的落点）：`分` 在番茄钟/专注页**只表示分钟，一处也不是积分**。
     *
     * 与 Task 6 的反例对照：CommonComponents 里 `"分"` 既做积分单位又做分钟标签，所以那里必须拆
     * `pointsUnit` / `minuteLabel` 并靠字面量钉死；本页的 `分` 全部出现在时长里
     * （`剩余 N分M秒`、`N分钟`、`N 分钟`），因此**没有任何字段调用 [pointsWord]**，
     * 英文一律用不随单复数变化的缩写 min / hr / s（与 CommonStringsEn.minuteLabel = "min" 同源）。
     *
     * 唯一提到积分的字段是 [FocusStrings.dailyPracticeHint]（`还能赚积分～`）：那里"积分"是名词
     * 而不是单位，不接数值，所以同样不需要 [pointsWord]。本测试把这个事实也钉住：
     * 两表里 "point" 只允许出现在这一条里，出现别处即说明有人把分钟改成了积分。
     */
    @Test
    fun pomodoroAndFocusMinuteUnitsAreMinutesNotPoints() {
        // 分钟/小时/秒：中文是时长，英文是缩写，两边都不含积分
        assertEquals("12 min 30 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(12, 30))
        assertEquals("90分钟", AppStringsZh.focus.minutesStatValue(90))
        assertEquals("90 min", AppStringsEn.focus.minutesStatValue(90))
        assertEquals("90 分钟", AppStringsZh.focus.recordMinutesValue(90))
        assertEquals("2小时", AppStringsZh.focus.hoursStatValue(2))
        assertEquals("2 hr", AppStringsEn.focus.hoursStatValue(2))
        assertEquals("60秒", AppStringsZh.focus.secondsStatValue("60"))
        assertFalse(
            AppStringsZh.pomodoro.toString().contains("积分"),
            "番茄钟表里不该出现积分单位：这里的 分 全是分钟",
        )
        assertFalse(
            AppStringsEn.pomodoro.toString().contains(pointsWord(2)),
            "番茄钟表（英文）不该出现 points：分 在本页只表示分钟",
        )
        val focusEnUnits = listOf(
            AppStringsEn.focus.minutesStatValue(30),
            AppStringsEn.focus.recordMinutesValue(30),
            AppStringsEn.focus.hoursStatValue(5),
            AppStringsEn.focus.secondsStatValue("12.3"),
            AppStringsEn.focus.elapsedValue("12.3"),
            AppStringsEn.focus.personalBestValue("12.3"),
        )
        assertTrue(
            focusEnUnits.none { it.contains(pointsWord(2)) },
            "专注页的时长字段英文必须用 min/hr/s，不得混进 points：$focusEnUnits",
        )
        assertTrue(
            AppStringsEn.focus.dailyPracticeHint.contains("points"),
            "本页只有 dailyPracticeHint（还能赚积分～）该提到 points，实际：" +
                AppStringsEn.focus.dailyPracticeHint,
        )
    }

    /**
     * emoji 归文案表（Ruling 17 在本页的落点）：装饰性 emoji 必须在两种语言里都留在表内，
     * 页面代码不得再拼 `"🍅 ${strings.x}"` 这种串——那等于把 UI 文案放回页面，
     * 而且中文态会多出一份对不上的前缀。
     *
     * 用"第一个空格之前的片段"比较：本页所有带 emoji 的文案都是 `emoji + 空格 + 正文`，
     * 所以两边取到的正好是那枚 emoji（代理对整体比较，不截半）。
     */
    @Test
    fun emojiStaysInsideThePomodoroAndFocusTables() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        val pairs = listOf(
            zh.phaseBreak to en.phaseBreak,
            zh.phaseFocus to en.phaseFocus,
            zh.phaseStartReady to en.phaseStartReady,
            zh.phaseBreakOver to en.phaseBreakOver,
            zh.cheerHalfway to en.cheerHalfway,
            zh.cheerProudest to en.cheerProudest,
            zh.cheerAlmostDone to en.cheerAlmostDone,
            zh.cheerBrainWorking to en.cheerBrainWorking,
            zh.cheerEveryMinute to en.cheerEveryMinute,
            zh.tipWalkAround to en.tipWalkAround,
            zh.tipLookFarAway to en.tipLookFarAway,
            zh.tipDrinkWater to en.tipDrinkWater,
            zh.tipBreathe to en.tipBreathe,
            zh.tipStretch to en.tipStretch,
            zh.tipListenMusic to en.tipListenMusic,
            zh.breakCardTitle to en.breakCardTitle,
        )
        pairs.forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertTrue(
            AppStringsEn.pomodoro.breakOverSummary(2).startsWith("🎉"),
            "breakOverSummary 的 🎉 应在表内，实际：${AppStringsEn.pomodoro.breakOverSummary(2)}",
        )
        val focusZh = AppStringsZh.focus
        val focusEn = AppStringsEn.focus
        listOf(
            focusZh.statsTitle to focusEn.statsTitle,
            focusZh.trainingTitle to focusEn.trainingTitle,
            focusZh.verdictExcellent to focusEn.verdictExcellent,
            focusZh.verdictGood to focusEn.verdictGood,
            focusZh.verdictPractice to focusEn.verdictPractice,
            focusZh.newRecordBanner to focusEn.newRecordBanner,
            focusZh.tryAgainButton to focusEn.tryAgainButton,
            focusZh.startTrainingButton to focusEn.startTrainingButton,
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertTrue(
            AppStringsEn.focus.emptyRecordsHint.endsWith("🍅"),
            "空状态提示的 🍅 留在句尾（与中文态同位置），实际：${AppStringsEn.focus.emptyRecordsHint}",
        )
    }

    @Test
    fun stringsForPicksInstance() {
        // assertSame 而非 assertEquals：数据类的结构相等会让"新建一个内容相同实例"也通过，
        // 这里要钉死的是"命中单例表"这一事实。
        assertSame(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertSame(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}

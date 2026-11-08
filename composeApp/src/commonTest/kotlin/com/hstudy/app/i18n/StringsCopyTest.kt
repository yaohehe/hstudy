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
     * 同形异义单位（裁定 1）：**这个事实按"字段"说，不按"表"说**。
     *
     * Task 7 时这一条写的是"番茄钟表里没有积分单位"，那句话**只对 `common/PomodoroTimer.kt`
     * 那 31 条成立**；Task 7b 把宿主页面 `ui/focus/ChildPomodoroScreen.kt` 迁进同一张表之后，
     * 表里就同时有了两种 `分`（`每专注 5 分钟 = +1 分` 那一行是两种含义同行），
     * 所以整表 `toString()` 式的绝对断言在这里必然是假的，改成**逐字段点名**：
     * · 时长字段的英文只能是 min / hr / s，出现 points 就是把分钟译成了积分；
     * · 积分字段的英文词形只能出自 [pointsWord]，见 [pointsAndMinutesSharingOneLineUseSeparateSlots]。
     *
     * 与 Task 6 的反例对照：CommonComponents 里 `"分"` 既做积分单位又做分钟标签，所以那里必须拆
     * `pointsUnit` / `minuteLabel` 并靠字面量钉死；本表的时长字段没有一个接 [pointsWord]，
     * 英文一律用不随单复数变化的缩写 min / hr / s（与 [CommonStringsEn.minuteLabel] = "min" 同源）。
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
        // 组件本体（Task 7 的 31 条）里没有积分单位，逐条点名而不是扫整表 toString()
        assertFalse(
            AppStringsZh.pomodoro.remainTime(45, 30).contains("积分") ||
                AppStringsZh.pomodoro.phaseFocus.contains("积分") ||
                AppStringsZh.pomodoro.cheerEveryMinute.contains("积分") ||
                AppStringsZh.pomodoro.tipDrinkWater.contains("积分"),
            "组件文案里不该出现积分单位：PomodoroTimer 的 分 全是分钟",
        )
        val durationEnOutputs = listOf(
            AppStringsEn.pomodoro.remainTime(30, 30),
            AppStringsEn.focus.minutesStatValue(30),
            AppStringsEn.focus.recordMinutesValue(30),
            AppStringsEn.focus.hoursStatValue(5),
            AppStringsEn.focus.secondsStatValue("12.3"),
            AppStringsEn.focus.elapsedValue("12.3"),
            AppStringsEn.focus.personalBestValue("12.3"),
            // Task 7b 宿主页新增的三条时长渲染
            AppStringsEn.pomodoro.durationChip(45, AppStringsEn.common.minuteLabel),
            AppStringsEn.pomodoro.overHoursNote(2),
            AppStringsEn.pomodoro.todayFocusSummary(45, ""),
        )
        assertTrue(
            durationEnOutputs.none { it.contains(pointsWord(2)) },
            "时长字段的英文必须用 min/hr/s，不得混进 points：$durationEnOutputs",
        )
        // 提到 points 的字段是穷举出来的，不是"整表只允许一条"：Task 7 一条 + Task 7b 四条
        listOf(
            AppStringsEn.focus.dailyPracticeHint,
            AppStringsEn.pomodoro.focusDoneSnackbar("Free study", 5),
            AppStringsEn.pomodoro.pointsPerFocusRule(5, 1),
            AppStringsEn.pomodoro.pointsLadderNote,
            AppStringsEn.focus.memorySuccessLine(3, 3),
        ).forEach {
            assertTrue(it.contains("point"), "本该提到积分的字段丢了 points：$it")
        }
        // 组件本体里唯一含"水分"的一处与单位无关，别把它当漏译的单位字段改
        assertEquals("💧 喝杯水，补充水分", AppStringsZh.pomodoro.tipDrinkWater)
        assertEquals("💧 Have a glass of water", AppStringsEn.pomodoro.tipDrinkWater)
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
            // Task 7b 宿主页面（ui/focus/ChildPomodoroScreen.kt）新增的字段
            zh.pageTitle to en.pageTitle,
            zh.targetHeading to en.targetHeading,
            zh.lengthLockedNote to en.lengthLockedNote,
            zh.breathingSwitchTitle to en.breathingSwitchTitle,
            zh.pointsRulesTitle to en.pointsRulesTitle,
            zh.schulteEntryButton to en.schulteEntryButton,
            zh.memoryEntryButton to en.memoryEntryButton,
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
            // Task 7b 记忆子页 + 每日计划子页
            focusZh.memoryTitle to focusEn.memoryTitle,
            focusZh.memoryMissPrompt to focusEn.memoryMissPrompt,
            focusZh.memoryRetryButton to focusEn.memoryRetryButton,
            focusZh.dailyPlanTitle to focusEn.dailyPlanTitle,
            focusZh.planAddedSnackbar to focusEn.planAddedSnackbar,
            focusZh.addPlanDialogTitle to focusEn.addPlanDialogTitle,
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "行首 emoji 应两表一致")
        }
        assertTrue(
            AppStringsEn.focus.emptyRecordsHint.endsWith("🍅"),
            "空状态提示的 🍅 留在句尾（与中文态同位置），实际：${AppStringsEn.focus.emptyRecordsHint}",
        )
        // 函数型字段的行首 emoji 同样在表内（Ruling 17 对 lambda 字段的落点，整表扫描看不见它们）
        listOf(
            AppStringsZh.pomodoro.goldenHourNowNote("早上8点") to AppStringsEn.pomodoro.goldenHourNowNote("8:00"),
            AppStringsZh.pomodoro.goldenHourGeneralNote("早上8点") to AppStringsEn.pomodoro.goldenHourGeneralNote("8:00"),
            AppStringsZh.pomodoro.focusDoneSnackbar("自由学习", 5) to AppStringsEn.pomodoro.focusDoneSnackbar("Free study", 5),
            AppStringsZh.focus.memoryBestLine(3) to AppStringsEn.focus.memoryBestLine(3),
        ).forEach { (chinese, english) ->
            assertEquals(chinese.substringBefore(' '), english.substringBefore(' '), "lambda 的行首 emoji 应两表一致")
        }
    }

    /**
     * 番茄钟宿主页面（Task 7b，`ui/focus/ChildPomodoroScreen.kt`）漏译守卫。
     *
     * 清单：去重 32 条字面量 → **33 个新字段**，其中 10 个函数型（见
     * [pomodoroPageInterpolatedCopyPinsBothLanguages]），本清单只穷举其余 23 个 String 字段。
     * 32 → 33 的差额来自两处反向修正，都不是"多写一条"：
     * · `自由学习` 出现两处（预设项列表 + `focusTarget` 初值）、`${min}分` 出现两处（专注 chips /
     *   休息 chips），同值同义各**共用一个字段**（-2）；
     * · 第 145 行 `今日已专注 $todayFocusMin 分钟${if (…超N小时…)}` 一条字面量落成
     *   [PomodoroStrings.todayFocusSummary] + [PomodoroStrings.overHoursNote] 两个字段（+1），
     *   见该字段的 Ruling 20 披露。
     *
     * `已跳过` / `开启` 与 `专注:` / `休息:` 各自成对，是同一控件的两种状态/两个轴，
     * 没有同形异义风险（`分` 的同形异义处理在 [durationChipReusesTheCommonMinuteLabel]）。
     */
    @Test
    fun pomodoroPageCopyDiffers() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertDiffers(
            zh.pageTitle to en.pageTitle,
            zh.targetFreeStudy to en.targetFreeStudy,
            zh.targetReviewMistakes to en.targetReviewMistakes,
            zh.targetPreviewLesson to en.targetPreviewLesson,
            zh.targetFinishHomework to en.targetFinishHomework,
            zh.targetHeading to en.targetHeading,
            zh.focusLengthLabel to en.focusLengthLabel,
            zh.breakLengthLabel to en.breakLengthLabel,
            zh.lengthLockedNote to en.lengthLockedNote,
            zh.breathingSwitchTitle to en.breathingSwitchTitle,
            zh.breathingStateSkipped to en.breathingStateSkipped,
            zh.breathingStateOn to en.breathingStateOn,
            zh.pointsRulesTitle to en.pointsRulesTitle,
            zh.pointsLadderNote to en.pointsLadderNote,
            zh.lockScreenNote to en.lockScreenNote,
            zh.schulteEntryButton to en.schulteEntryButton,
            zh.memoryEntryButton to en.memoryEntryButton,
            zh.goldenPeriodEarlyMorning to en.goldenPeriodEarlyMorning,
            zh.goldenPeriodMorning to en.goldenPeriodMorning,
            zh.goldenPeriodMidday to en.goldenPeriodMidday,
            zh.goldenPeriodAfternoon to en.goldenPeriodAfternoon,
            zh.goldenPeriodEvening to en.goldenPeriodEvening,
            zh.goldenPeriodLateNight to en.goldenPeriodLateNight,
        )
    }

    /**
     * 番茄钟宿主页 10 条函数型字段的双语字面量断言（Ruling 20-b）。
     *
     * 参数取页面真实传入的形状：`goldenLabel` 是 [PomodoroStrings.goldenPeriodHour] 的产物
     * （中文态 `早上8点`、英文态 `Early morning · 8:00`），`todayFocusSummary` 的第二实参是
     * [PomodoroStrings.overHoursNote] 的产物或空串（`else ""` 那条分支同样是设备真实输出），
     * `durationChip` 的单位实参就是页面从 [CommonStrings.minuteLabel] 取到的那一个值。
     */
    @Test
    fun pomodoroPageInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        assertEquals(
            "🌟 现在正是你的黄金专注时段（早上8点），效率最高，抓紧专注！",
            zh.goldenHourNowNote("早上8点"),
        )
        assertEquals(
            "🌟 Right now is your golden focus window (Early morning · 8:00) — that's your best time, make it count!",
            en.goldenHourNowNote("Early morning · 8:00"),
        )
        assertEquals("💡 你的黄金专注时段是 早上8点，那时专注效率最高哦～", zh.goldenHourGeneralNote("早上8点"))
        assertEquals(
            "💡 Your golden focus window is Early morning · 8:00 — that's when you focus best.",
            en.goldenHourGeneralNote("Early morning · 8:00"),
        )
        // 第 145 行拆出来的两个字段：有注记 / 无注记两种都要钉（else "" 是真实分支）
        assertEquals("（超2小时）", zh.overHoursNote(2))
        assertEquals(" (over 2 hr)", en.overHoursNote(2))
        assertEquals("今日已专注 90 分钟（超1小时）", zh.todayFocusSummary(90, zh.overHoursNote(1)))
        assertEquals("You've focused 90 min today (over 1 hr)", en.todayFocusSummary(90, en.overHoursNote(1)))
        assertEquals("今日已专注 25 分钟", zh.todayFocusSummary(25, ""))
        assertEquals("You've focused 25 min today", en.todayFocusSummary(25, ""))
        assertEquals("完成 3 个番茄钟 🍅×3", zh.completedPomodorosLine(3))
        assertEquals("Completed 3 Pomodoros 🍅×3", en.completedPomodorosLine(3))
        assertEquals("专注「自由学习」25分钟", zh.focusRecordDescription("自由学习", 25))
        assertEquals("Focus on Free study for 25 min", en.focusRecordDescription("Free study", 25))
        assertEquals("🍅 「自由学习」专注完成！+5分已提交家长审核", zh.focusDoneSnackbar("自由学习", 5))
        assertEquals(
            "🍅 Focus on 'Free study' done! +5 points sent for approval",
            en.focusDoneSnackbar("Free study", 5),
        )
        assertEquals("15分", zh.durationChip(15, AppStringsZh.common.minuteLabel))
        assertEquals("15 min", en.durationChip(15, AppStringsEn.common.minuteLabel))
        assertEquals("每专注 5 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(5, 1))
        assertEquals("Every 5 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(5, 1))
        assertEquals("早上8点", zh.goldenPeriodHour("早上", 8))
        assertEquals("Early morning · 8:00", en.goldenPeriodHour("Early morning", 8))
    }

    /**
     * 数字记忆广度子页（Task 7b，`ui/focus/ChildMemoryTrainingScreen.kt`）漏译守卫：
     * 去重 17 条 → 本表 16 个新字段（10 个 String 在本清单 + 6 个函数型见
     * [memoryTrainingInterpolatedCopyPinsBothLanguages]）+ **复用 [FocusStrings.startTrainingButton]**
     * 一条：第 82 行的 `🚀 开始训练` 与舒尔特页第 148 行同值同义，共用字段而不是再造一个。
     */
    @Test
    fun memoryTrainingCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.memoryTitle to en.memoryTitle,
            zh.memoryHeading to en.memoryHeading,
            zh.memoryHowTo to en.memoryHowTo,
            zh.memoryShowPrompt to en.memoryShowPrompt,
            zh.memoryWatchHint to en.memoryWatchHint,
            zh.memoryConfirmButton to en.memoryConfirmButton,
            zh.memoryPracticeAgainButton to en.memoryPracticeAgainButton,
            zh.memoryMissPrompt to en.memoryMissPrompt,
            zh.memoryEncourageHint to en.memoryEncourageHint,
            zh.memoryRetryButton to en.memoryRetryButton,
        )
    }

    /**
     * 记忆子页 6 条函数型字段的双语字面量断言。
     *
     * 参数是页面上真实算好的值：`挑战 ${level + 1} 位` 传的是 `level + 1`、`回到 ${level - 1} 位`
     * 传的是 `level - 1`（两处 Ruling 20 的"表达式挪到实参位"，与 Task 7 的 deepBreathCount 同形），
     * 所以断言里的 4 和 3 就是设备上出现的数字，而不是把 `level` 又拼一遍。
     */
    @Test
    fun memoryTrainingInterpolatedCopyPinsBothLanguages() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertEquals("🏆 本次最佳：记住 5 位数字", zh.memoryBestLine(5))
        assertEquals("🏆 Best this round: 5 digits remembered", en.memoryBestLine(5))
        assertEquals("准备好了吗？从 3 位数字开始！", zh.memoryReadyLine(3))
        assertEquals("Ready? Let's start with 3 digits!", en.memoryReadyLine(3))
        assertEquals("数字是什么？凭记忆输入（4 位）：", zh.memoryInputPrompt(4))
        assertEquals("What were the digits? Type them from memory (4 digits):", en.memoryInputPrompt(4))
        assertEquals("🎉 太棒了！记住了 3 位数字！+3分", zh.memorySuccessLine(3, 3))
        assertEquals("🎉 Amazing! You remembered 3 digits — +3 points", en.memorySuccessLine(3, 3))
        assertEquals("挑战 4 位 →", zh.memoryChallengeButton(4))
        assertEquals("Try 4 digits →", en.memoryChallengeButton(4))
        assertEquals("回到 3 位", zh.memoryStepBackButton(3))
        assertEquals("Back to 3 digits", en.memoryStepBackButton(3))
    }

    /**
     * 每日计划子页（Task 7b，`ui/focus/ChildDailyPlanScreen.kt`）漏译守卫：14 条字面量 → 14 个字段，
     * 全是非函数型（清单里插值 0 条）。落在 [FocusStrings] 而不是新造第三张表（Ruling 23）。
     *
     * `已完成` / `未完成` 是 `contentDescription`（读屏），与 Task 8 的
     * [TaskListStrings.completedContentDescription]（`已完成(点击撤销)`）**不同值**，所以不能复用那条；
     * `开始` / `结束` 是时分步进器的轴标签，与 [CommonStrings.hourLabel] / [minuteLabel] 无关
     * （那两个是单位 `时` / `分`，这里是"Start"/"End"），同理不复用。
     */
    @Test
    fun dailyPlanCopyDiffers() {
        val zh = AppStringsZh.focus
        val en = AppStringsEn.focus
        assertDiffers(
            zh.dailyPlanTitle to en.dailyPlanTitle,
            zh.addPlanDescription to en.addPlanDescription,
            zh.dailyPlanEmptyHint to en.dailyPlanEmptyHint,
            zh.planAddedSnackbar to en.planAddedSnackbar,
            zh.planCompletedDescription to en.planCompletedDescription,
            zh.planUncompletedDescription to en.planUncompletedDescription,
            zh.deletePlanDescription to en.deletePlanDescription,
            zh.addPlanDialogTitle to en.addPlanDialogTitle,
            zh.planSubjectLabel to en.planSubjectLabel,
            zh.planTimeRangeLabel to en.planTimeRangeLabel,
            zh.planStartLabel to en.planStartLabel,
            zh.planEndLabel to en.planEndLabel,
            zh.addPlanButton to en.addPlanButton,
            zh.cancelButton to en.cancelButton,
        )
    }

    /**
     * **同一行两种 `分`**（Task 7b 必须做对的第一件事，裁定 1 在本页的落点）。
     *
     * 宿主页第 246 行 `每专注 5 分钟 = +1 分（所有时长均有效）` 和第 247 行
     * `15分钟+3分 · …`、记忆子页第 106 行 `记住了 $level 位数字！+${level}分`：
     * 前者 `5 分钟` 是时长、`+1 分` 是积分。判错方向的渲染是 "Focus 5 points = +1 min"。
     *
     * 防错手段是**结构上的**，不是口头上的：[PomodoroStrings.pointsPerFocusRule] 把两个数值做成
     * 两个**具名实参** `minutes` / `points`，积分词形只能由 `pointsWord(points)` 产生，
     * 时长词形只能是 `分钟` / `min`，位置互换在类型上就写不出来。下面每个用例都**两条一起断言**
     * （brief Step 5：同行双单位必须两条都断言），并另加一条 `points = 1` 的边界，
     * 因为"1 分 = 1 point"正是这一行的实际取值。
     */
    @Test
    fun pointsAndMinutesSharingOneLineUseSeparateSlots() {
        val zh = AppStringsZh.pomodoro
        val en = AppStringsEn.pomodoro
        // 第 246 行：时长槽 + 积分槽
        assertEquals("每专注 5 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(5, 1))
        assertEquals("Every 5 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(5, 1))
        // 同一个字段的边界取值：积分 1 用单数、时长 1 仍用 min（缩写不随单复数变化，§11）
        assertEquals("每专注 1 分钟 = +1 分（所有时长均有效）", zh.pointsPerFocusRule(1, 1))
        assertEquals("Every 1 min of focus = +1 point (every length counts)", en.pointsPerFocusRule(1, 1))
        assertFalse(en.pointsPerFocusRule(5, 1).contains("min = +1 min"), "积分槽被写成了时长槽")
        // 第 247 行：四个"时长+积分"对，中文一个字段照抄，英文每处积分词形都出自 pointsWord
        assertEquals("15分钟+3分 · 25分钟+5分 · 30分钟+6分 · 45分钟+9分", zh.pointsLadderNote)
        assertEquals(
            "15 min +3 points · 25 min +5 points · 30 min +6 points · 45 min +9 points",
            en.pointsLadderNote,
        )
        // 记忆子页第 106 行：`位数字` 是位数、`+N分` 是积分，两个实参在页面里同源（都是 level）
        assertEquals("🎉 太棒了！记住了 6 位数字！+6分", AppStringsZh.focus.memorySuccessLine(6, 6))
        assertEquals("🎉 Amazing! You remembered 6 digits — +6 points", AppStringsEn.focus.memorySuccessLine(6, 6))
        assertEquals(
            "🍅 「自由学习」专注完成！+1分已提交家长审核",
            zh.focusDoneSnackbar("自由学习", 1),
        )
        assertEquals(
            "🍅 Focus on 'Free study' done! +1 point sent for approval",
            en.focusDoneSnackbar("Free study", 1),
        )
    }

    /**
     * 步进器/时长 chip 的 `分` **必须复用 [CommonStrings.minuteLabel]**（Task 7b 第二件事），
     * 也就是全工程只有两个 `分` 字段：积分的 `pointsUnit` 与分钟的 `minuteLabel`，
     * 宿主页不再开第三个（否则 Task 6+8 的字面量同形异义守卫就管不到这一处）。
     *
     * [PomodoroStrings.durationChip] 因此把单位做成实参，页面传 `common.minuteLabel` 进来：
     * 中文 `15` + `分` = `15分`（与迁移前逐字节相同，中文模板里本来就没有空格），
     * 英文 `15` + ` ` + `min`（空格在表里，不在页面里）。
     *
     * 两条英文断言合起来才抓得住互换错误：若有人把 `minuteLabel` 的英文改成 "points"，
     * `durationChip` 会渲染出 "15 points"，第三条 assertFalse 立刻红；
     * 若有人把 pointsUnit 改成 "min"，[commonUnitLabelsPinnedByLiteral] 那边红。
     */
    @Test
    fun durationChipReusesTheCommonMinuteLabel() {
        assertEquals("15分", AppStringsZh.pomodoro.durationChip(15, AppStringsZh.common.minuteLabel))
        assertEquals("25分", AppStringsZh.pomodoro.durationChip(25, AppStringsZh.common.minuteLabel))
        assertEquals("3分", AppStringsZh.pomodoro.durationChip(3, AppStringsZh.common.minuteLabel))
        val chipFromMinuteLabel = AppStringsEn.pomodoro.durationChip(15, AppStringsEn.common.minuteLabel)
        assertEquals("15 min", chipFromMinuteLabel)
        assertFalse(
            chipFromMinuteLabel.contains(AppStringsEn.common.pointsUnit),
            "时长 chip 里出现了积分单位：单位实参必须来自 common.minuteLabel，不是 pointsUnit",
        )
        // 休息 chips 与专注 chips 共用同一个字段（同值同义），所以 3/5/10 与 15/25/30/45 走同一条渲染
        assertEquals("5 min", AppStringsEn.pomodoro.durationChip(5, AppStringsEn.common.minuteLabel))
    }

    /**
     * Task 7b 新增英文 one/other（spec §3 末句 + §8.3），并钉住中文没有单复数。
     *
     * 可达性逐条核过：`todayPomodoroCount` 首完成即 1（`Completed 1 Pomodoros` 是真会出现的错误）；
     * `overHoursNote` 的 hours 在 60–119 分钟时是 1，但 `hr` 是 §11 定的缩写、不随数变化，
     * 所以那条**不该**有 if —— 一并断言，防止有人"顺手加复数"（与 Task 7 的 min/hr/s 断言同一理由）。
     * 记忆子页的"位数字"最小取值是 3（`level` 初值 3，`回到 N 位` 按钮要求 `level > 3`），
     * 所以英文用不带分支的 "digits"；这条可达性写在 [FocusStrings.memoryBestLine] 的字段注释里，
     * 若后续任务放开下限，需要在 `Plurals.kt` 加一个同样窄的 digitsWord（本任务禁止改那个文件）。
     */
    @Test
    fun task7bEnglishPluralsCoverOneAndOther() {
        assertEquals("完成 1 个番茄钟 🍅×1", AppStringsZh.pomodoro.completedPomodorosLine(1))
        assertEquals("Completed 1 Pomodoro 🍅×1", AppStringsEn.pomodoro.completedPomodorosLine(1))
        assertEquals("Completed 2 Pomodoros 🍅×2", AppStringsEn.pomodoro.completedPomodorosLine(2))
        // 缩写单位没有单复数
        assertEquals(" (over 1 hr)", AppStringsEn.pomodoro.overHoursNote(1))
        assertEquals("（超1小时）", AppStringsZh.pomodoro.overHoursNote(1))
        assertEquals("1 min", AppStringsEn.pomodoro.durationChip(1, AppStringsEn.common.minuteLabel))
        assertEquals(
            "Every 15 min of focus = +15 points (every length counts)",
            AppStringsEn.pomodoro.pointsPerFocusRule(15, 15),
        )
        // 中文侧 1 与 15 同形，防止有人把英文的 if 复制回中文表
        assertEquals("完成 15 个番茄钟 🍅×15", AppStringsZh.pomodoro.completedPomodorosLine(15))
    }

    /**
     * Step 4 功能修正（`rememberUpdatedState`）的字面量证据。
     *
     * 改动本身在 `PomodoroTimer.kt` 的 `LaunchedEffect(state)` 里：把闭包捕获的 `strings` 换成
     * `currentStrings`（key 仍是 `state`，计时循环不重启）。本文件不能测组合期行为（没有 Compose UI
     * 测试依赖，spec §8 / Task 12 之前的约定），能被自动证明的是**这两条被通知使用的文案**在两种语言里
     * 逐字是什么——`currentStrings` 与 `strings` 取的是同一个表实例，所以钉住表值就钉住了通知内容；
     * 中文态输出逐字节不变（Ruling 20(b)）由第 1、2、3 条断言承担。
     */
    @Test
    fun runningNotificationCopyIsPinnedInBothLanguages() {
        assertEquals("🍅 专注中", AppStringsZh.pomodoro.phaseFocus)
        assertEquals("☕ 休息中", AppStringsZh.pomodoro.phaseBreak)
        assertEquals("剩余 8分20秒 · 息屏后仍会继续计时", AppStringsZh.pomodoro.remainTime(8, 20))
        assertEquals("🍅 Focusing", AppStringsEn.pomodoro.phaseFocus)
        assertEquals("☕ On a break", AppStringsEn.pomodoro.phaseBreak)
        assertEquals("8 min 20 s left · Keeps counting with the screen off", AppStringsEn.pomodoro.remainTime(8, 20))
    }

    @Test
    fun stringsForPicksInstance() {
        // assertSame 而非 assertEquals：数据类的结构相等会让"新建一个内容相同实例"也通过，
        // 这里要钉死的是"命中单例表"这一事实。
        assertSame(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertSame(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}

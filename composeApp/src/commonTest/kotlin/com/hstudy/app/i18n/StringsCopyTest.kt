package com.hstudy.app.i18n

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
 * 已知上限（评审指出）：data class 的 `toString()` 不包含函数型字段，
 * 所以带插值的文案（Task 6 的 CommonStrings 起出现，Task 7 起大量出现）无法靠整表扫描覆盖。
 * 补偿办法是硬性约定：**每个函数型字段都必须有显式双语断言，且期望串逐字打出来**
 * （见 [commonInterpolatedCopyPinsBothLanguages] / [taskListInterpolatedCopyPinsBothLanguages]），
 * 不允许"调 lambda 再和 lambda 自己拼的串比"——那种断言对漏译和语序错都是自证通过的。
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
     * 一次是积分徽章的单位（PointsBadge:127），一次是分钟步进器的标签（HourMinuteStepper:161）。
     * 去重把它们合成一条，英文却必须分写（`points` vs `min`），所以拆成 pointsUnit / minuteLabel。
     * 教训：清单的"去重条数"是**下限**，不能当成字段数直接验收。
     *
     * 另：assertDiffers 只比较**同一字段的 ZH vs EN**，绝不跨字段比——
     * `pointsUnit` 与 `minuteLabel` 的中文都是"分"，若把 ZH(分) vs EN(min) 写成跨字段配对，
     * 两条断言会互相掩盖。函数型字段（decreaseLabel / increaseLabel）不在此清单内，
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

    @Test
    fun stringsForPicksInstance() {
        // assertSame 而非 assertEquals：数据类的结构相等会让"新建一个内容相同实例"也通过，
        // 这里要钉死的是"命中单例表"这一事实。
        assertSame(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertSame(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}

package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

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
 * 所以带插值的文案（Task 7 起出现）无法靠整表扫描覆盖，必须继续为本页字段补显式断言。
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

    @Test
    fun stringsForPicksInstance() {
        // assertSame 而非 assertEquals：数据类的结构相等会让"新建一个内容相同实例"也通过，
        // 这里要钉死的是"命中单例表"这一事实。
        assertSame(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertSame(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}

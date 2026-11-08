package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageResolveTest {
    @Test
    fun explicitChoiceWins() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.ZH.resolveWith("en"))
        assertEquals(DisplayLanguage.EN, AppLanguage.EN.resolveWith("zh"))
    }

    @Test
    fun systemChineseSelectsZh() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-CN"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-Hant-TW"))
    }

    @Test
    fun systemOtherSelectsEn() {
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("en"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("fr"))
    }

    @Test
    fun emptySystemTagFallsBackToZh() {
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith(""))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("   "))
    }

    /**
     * systemLanguageTag() 的契约形状与 resolveWith 对这些形状的判定。
     * 要说清两件事，别把"系统 locale 的形状"当成"桥接返回值的形状"：
     * - **平台桥接实际只返回裸子标签**：androidMain 是 `Locale.getDefault().language`（`en`、`zh`），
     *   iosMain 把 `NSLocale…localeIdentifier` 先按 `-` 再按 `_` 截断后小写（也是 `en`、`zh`）。
     *   所以 `en-US` / `en_US` / `zh-Hans_CN` 是**操作系统层面的 locale 写法**，
     *   不是当前两个 actual 实现会交出来的值；
     * - 这一条测试因此是**防御性契约**：把 resolveWith 在这些形状上的判定固定下来，
     *   万一将来有人把桥接改成直接透传 localeIdentifier（或给解析加分隔符逻辑），
     *   退化会立刻红，而不是悄悄改变语言选择结果。
     */
    @Test
    fun documentedTagShapesResolveAsContracted() {
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("en"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("en_US"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("en-US"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("de"))
        assertEquals(DisplayLanguage.EN, AppLanguage.SYSTEM.resolveWith("de_DE"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-Hans_CN"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("zh-Hant-TW"))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith(""))
        assertEquals(DisplayLanguage.ZH, AppLanguage.SYSTEM.resolveWith("   "))
    }

    @Test
    fun storageRoundTrip() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage(null))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage("system"))
        assertEquals(AppLanguage.EN, appLanguageFromStorage(AppLanguage.EN.toStorageValue()))
        assertEquals("zh", AppLanguage.ZH.toStorageValue())
    }
}

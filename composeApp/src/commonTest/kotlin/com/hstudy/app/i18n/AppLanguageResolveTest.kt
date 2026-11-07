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
     * systemLanguageTag() 的契约形状：只保证裸语言子标签，`_` 与 `-` 两种分隔符都可能出现
     * （Apple 常见 en_US / zh-Hans_CN，Android 常见 en-US）。这里固定 resolveWith 在这些
     * 形状上的判定，防止后续把解析改成依赖分隔符时悄悄退化。
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

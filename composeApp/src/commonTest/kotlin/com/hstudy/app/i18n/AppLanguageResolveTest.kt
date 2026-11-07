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

    @Test
    fun storageRoundTrip() {
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage(null))
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage("system"))
        assertEquals(AppLanguage.EN, appLanguageFromStorage(AppLanguage.EN.toStorageValue()))
        assertEquals("zh", AppLanguage.ZH.toStorageValue())
    }
}

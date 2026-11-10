package com.hstudy.app.i18n

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * currentDisplayLanguage 的接线证明：读存储 → appLanguageFromStorage → resolveWith(systemTag)。
 *
 * 与 LanguagePreferenceTest 共用 build/test-i18n-prefs 目录是刻意的：DataStore 每文件单实例
 * 是进程内的（UserPreferences.kt 顶层 by lazy），两个测试类无论谁先跑，单例都锚在同一路径；
 * 每条测试先写后读，顺序无关。systemTag 用参数注入而非真机 locale——GitHub runner 是 en-US，
 * 依赖真实 locale 的断言在 zh 开发机上会翻车；解析规则本身已由 AppLanguageResolveTest 逐形状钉死，
 * 这里只证接线（显式选择压过系统标签、SYSTEM 走注入标签）。
 * null（未设置）→ SYSTEM 一环由 AppLanguageResolveTest.storageRoundTrip 钉住，不在此重复。
 */
class CurrentDisplayLanguageTest {
    @AfterTest
    fun cleanup() {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun explicitZhWinsOverEnglishSystem() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.ZH)
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "en"))
    }

    @Test
    fun explicitEnWinsOverChineseSystem() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.EN)
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "zh"))
    }

    @Test
    fun systemPreferenceResolvesChineseTags() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "zh"))
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = "zh-CN"))
        assertEquals(DisplayLanguage.ZH, currentDisplayLanguage(prefs, systemTag = ""))
    }

    @Test
    fun systemPreferenceResolvesOtherTags() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "en"))
        assertEquals(DisplayLanguage.EN, currentDisplayLanguage(prefs, systemTag = "fr"))
    }
}
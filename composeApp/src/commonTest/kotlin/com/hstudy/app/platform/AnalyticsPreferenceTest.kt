package com.hstudy.app.platform

import com.hstudy.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsPreferenceTest {
    // 目录与 LanguagePreferenceTest 相同：DataStore 是进程内单例（顶层 by lazy），
    // 换目录名不会换实例，反而让"产物落在哪"的注释失真；共用同一测试目录。
    @AfterTest
    fun cleanup() {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun defaultsToEnabledAndPersistsToggle() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        assertTrue(prefs.analyticsEnabled.first())
        prefs.setAnalyticsEnabled(false)
        assertEquals(false, prefs.analyticsEnabled.first())
        prefs.setAnalyticsEnabled(true)
        assertEquals(true, prefs.analyticsEnabled.first())
    }
}
package com.hstudy.app.i18n

import com.hstudy.app.data.preferences.UserPreferences
import com.hstudy.app.platform.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 语言偏好的读写往返（设计文档 §8.5）。
 *
 * 为什么落在 commonTest（Ruling 7）：UserPreferences 走 okio 文件版 DataStore
 * （`Platform.dataDir` + "datastore/hstudy_prefs.preferences_pb"），而 `Platform.dataDir` 是 lateinit，
 * 未初始化就直接 `UninitializedPropertyAccessException`，所以本测试必须先 `Platform.init(...)`
 * 把目录指到 `build/` 下（实测工作目录是 composeApp/，产物落在 composeApp/build/test-i18n-prefs/，
 * 被 .gitignore 的 build/ 覆盖，不会污染真实数据目录，也不需要 Android/iOS 设备）。
 *
 * 证明范围（诚实说明）：DataStore 每文件单实例是**进程内**的（UserPreferences.kt:19 的顶层 by lazy），
 * 所以这里能证明"写挂起等待落盘 → Flow 立刻反映新值"，证明不了"杀进程重启后仍在"；
 * 后者需要新 JVM/新进程，本仓库没有相应测试装置。旁证：跑完后
 * composeApp/build/test-i18n-prefs/datastore/hstudy_prefs.preferences_pb 里能看到 `app_language` 与 `en` 两段字节。
 */
class LanguagePreferenceTest {
    @AfterTest
    fun cleanup() {
        // 偏好文件是进程内单例，测试产物留在 build 目录下，避免污染真实数据目录
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
    }

    @Test
    fun languagePersistsAcrossReads() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()
        prefs.setAppLanguage(AppLanguage.EN)
        assertEquals("en", prefs.appLanguage.first())
        assertEquals(AppLanguage.EN, appLanguageFromStorage(prefs.appLanguage.first()))
    }

    /**
     * 上一条改成"每次都返回同一个字符串"也照样能过，所以补这一条：同一份 Flow 上连续改写
     * en → zh → system，每次读都必须拿到刚写进去的那个值，且顺带钉死持久化线格式
     * （"en"/"zh"/"system" 字面量——Task 1 遗留的 minor：toStorageValue() 把线格式绑在枚举名上，
     * 此前没有任何断言锁住这三个字符串，重命名枚举会静默重置所有用户的选择）。
     */
    @Test
    fun languageRewriteReplacesPreviousChoiceAndLocksWireFormat() = runTest {
        Platform.init(filesDir = "build/test-i18n-prefs", cacheDir = "build/test-i18n-prefs")
        val prefs = UserPreferences()

        prefs.setAppLanguage(AppLanguage.EN)
        assertEquals("en", prefs.appLanguage.first())

        prefs.setAppLanguage(AppLanguage.ZH)
        assertEquals("zh", prefs.appLanguage.first())
        assertEquals(AppLanguage.ZH, appLanguageFromStorage(prefs.appLanguage.first()))

        prefs.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals("system", prefs.appLanguage.first())
        assertEquals(AppLanguage.SYSTEM, appLanguageFromStorage(prefs.appLanguage.first()))
    }
}

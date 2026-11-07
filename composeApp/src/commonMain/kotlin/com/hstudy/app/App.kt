package com.hstudy.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hstudy.app.i18n.LocalStrings
import com.hstudy.app.i18n.appLanguageFromStorage
import com.hstudy.app.i18n.stringsFor
import com.hstudy.app.i18n.systemLanguageTag
import com.hstudy.app.ui.navigation.AppNavGraph
import com.hstudy.app.ui.navigation.LocalNavigator
import com.hstudy.app.ui.navigation.Screen
import com.hstudy.app.ui.navigation.rememberAppNavigator
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.ProvideWindowSizeClass
import kotlinx.coroutines.flow.catch

/**
 * 应用根 Composable（跨平台入口）。
 *
 * 对应原 Android 版 MainActivity.setContent 的内容：
 *   HStudyTheme { Surface(Modifier.fillMaxSize()) { AppNavGraph(windowSizeClass) } }
 * 差异：
 * - androidx.navigation 的 NavHost 已由 [AppNavGraph] + [AppNavigator] 替代（route 字符串语义不变）；
 * - Android-only 的 material3-window-size-class 由 [ProvideWindowSizeClass]（BoxWithConstraints 断点推导）替代；
 * - 边到边/状态栏仍由平台侧负责（Android 在 MainActivity.enableEdgeToEdge，iOS 由 Scene 决定）；
 * - 文案表由 [LocalStrings] 在此注入：生效语言 = 用户显式选择优先，SYSTEM 下按 [systemLanguageTag] 推导。
 *   偏好读写走 AppGraph.userPreferences（全工程唯一 DataStore 实例），入口平台侧已保证
 *   AppGraph.bootstrap() 早于本 Composable 组合（Android HStudyApplication.onCreate / iOS initIosPlatform）；
 *   同一次 bootstrap 还会预读偏好写入 AppGraph.startupLanguage，用作首帧 initial，减少一帧错语言闪现。
 * - 生效文案用 remember(storedLanguage) 缓存，避免每次重组重复调用平台桥接与解析。
 */
@Composable
fun App() {
    // 首帧语言：initial 用 AppGraph.bootstrap() 预读的偏好快照，而不是 null。
    // 旧写法 initial = null 让第 1 帧按 SYSTEM（=设备语言）解析，在"存储选择与设备语言相反"的
    // 设备上会先渲染错语言、等 DataStore 首次 emission 到达才翻转（几十到几百毫秒，正好落在
    // RoleSelect 入场动画里）。快照把窗口收窄到"预读尚未完成"这一小段：此时仍退化为 SYSTEM 推导，
    // 但两种写法都不会插入空白帧，只是首帧文案是否已正确之别。
    val storedLanguage by AppGraph.userPreferences.appLanguage
        // 降级不崩根，但代价要在调用点可见：catch 之后上游以一次 null 完成，订阅就此终止，
        // 本次会话语言被钉死在系统语言上，不再跟随设置页的切换（Task 5 的开关看起来失灵）。
        // 全工程其他 collectAsState 消费者都是裸调用（错误直接上抛），这里例外是因为根组合崩溃
        // 等于整 app 白屏；不额外加重试机制。
        .catch { emit(null) }
        .collectAsState(initial = AppGraph.startupLanguage)
    // storedLanguage 不变时不重新解析：systemLanguageTag() 要走平台桥接，stringsFor 也要建引用，
    // 放在 remember 外会让每次重组都重算。设备语言在运行中被更换时组合本就会被销毁重建
    // （Android 配置变更重建 Activity，iOS 改语言需重启 App），缓存随之失效，不必再加 key。
    val strings = remember(storedLanguage) {
        stringsFor(appLanguageFromStorage(storedLanguage).resolveWith(systemLanguageTag()))
    }

    HStudyTheme {
        ProvideWindowSizeClass {
            val navigator = rememberAppNavigator(Screen.RoleSelect.route)
            Surface(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalStrings provides strings,
                ) {
                    AppNavGraph(navigator)
                }
            }
        }
    }
}

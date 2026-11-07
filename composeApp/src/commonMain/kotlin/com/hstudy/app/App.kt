package com.hstudy.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.hstudy.app.ui.navigation.AppNavGraph
import com.hstudy.app.ui.navigation.LocalNavigator
import com.hstudy.app.ui.navigation.Screen
import com.hstudy.app.ui.navigation.rememberAppNavigator
import com.hstudy.app.ui.theme.HStudyTheme
import com.hstudy.app.ui.theme.ProvideWindowSizeClass

/**
 * 应用根 Composable（跨平台入口）。
 *
 * 对应原 Android 版 MainActivity.setContent 的内容：
 *   HStudyTheme { Surface(Modifier.fillMaxSize()) { AppNavGraph(windowSizeClass) } }
 * 差异：
 * - androidx.navigation 的 NavHost 已由 [AppNavGraph] + [AppNavigator] 替代（route 字符串语义不变）；
 * - Android-only 的 material3-window-size-class 由 [ProvideWindowSizeClass]（BoxWithConstraints 断点推导）替代；
 * - 边到边/状态栏仍由平台侧负责（Android 在 MainActivity.enableEdgeToEdge，iOS 由 Scene 决定）。
 */
@Composable
fun App() {
    HStudyTheme {
        ProvideWindowSizeClass {
            val navigator = rememberAppNavigator(Screen.RoleSelect.route)
            Surface(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    AppNavGraph(navigator)
                }
            }
        }
    }
}

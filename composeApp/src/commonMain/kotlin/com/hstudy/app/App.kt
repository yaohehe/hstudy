package com.hstudy.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hstudy.app.i18n.LocalDisplayLanguage
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
 *   同一次解析算出的生效语言另由 [LocalDisplayLanguage] 注入（页面据此做"仅英文态"的文案分支，
 *   不在自己内部重复解析），两者必须来自同一个 remember 结果，不允许各算各的。
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
    // Flow 实例本身也要 remember（Task 5 修复，Ruling 11）：不 remember 时每次重组都会新建一条
    // 管道，collectAsState 认出"上游换了"就重建订阅容器，state 被重置回 initial（= 启动快照）。
    // 启动期看不出来——快照与存储值相同；但本页开始支持运行中切换语言，于是切完之后任意一次重组
    // （动画、滚动、输入框）都会把界面闪回启动时的那个语言，看起来像"开关失灵"。
    val appLanguageFlow = remember {
        AppGraph.userPreferences.appLanguage
            // 降级不崩根，但代价要在调用点可见：catch 之后上游以一次 null 完成，订阅就此终止，
            // 本次会话语言被钉死在系统语言上，不再跟随设置页的切换（Task 5 的开关看起来失灵）。
            // 全工程其他 collectAsState 消费者都是裸调用（错误直接上抛），这里例外是因为根组合崩溃
            // 等于整 app 白屏；不额外加重试机制。
            // 注：flow 被 remember 之后这句"订阅就此终止"才真正成立——旧写法里每次重组都会重建
            // 订阅、顺带自愈一次；现在要恢复只能等平台侧重建组合（Android 配置变更 / iOS 重启）。
            .catch { emit(null) }
    }
    val storedLanguage by appLanguageFlow.collectAsState(initial = AppGraph.startupLanguage)
    // storedLanguage 不变时不重新解析：systemLanguageTag() 要走平台桥接，stringsFor 也要建引用，
    // 放在 remember 外会让每次重组都重算。设备语言在运行中被更换时组合本就会被销毁重建
    // （Android 配置变更重建 Activity，iOS 改语言需重启 App），缓存随之失效，不必再加 key。
    // 一次解析同时产出"生效语言"和"该语言的文案表"两个结果：它们是同一次计算的产物，
    // 分开算就会出现两份解析逻辑（Task 5 修复评审后定的形状）。生效语言随表一起下发，
    // 页面据此判断"某条文案只在英文态出现"，自己不再重复解析。
    val (displayLanguage, strings) = remember(storedLanguage) {
        val language = appLanguageFromStorage(storedLanguage).resolveWith(systemLanguageTag())
        language to stringsFor(language)
    }

    HStudyTheme {
        ProvideWindowSizeClass {
            val navigator = rememberAppNavigator(Screen.RoleSelect.route)
            Surface(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalStrings provides strings,
                    LocalDisplayLanguage provides displayLanguage,
                ) {
                    AppNavGraph(navigator)
                }
            }
        }
    }
}

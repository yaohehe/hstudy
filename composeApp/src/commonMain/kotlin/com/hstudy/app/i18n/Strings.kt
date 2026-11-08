package com.hstudy.app.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** 跨页面文案聚合根：每页一个子表，后续任务按页扩展。 */
data class AppStrings(
    val role: RoleStrings,
    val settings: SettingsStrings,
    val common: CommonStrings,
    val taskList: TaskListStrings,
    val pomodoro: PomodoroStrings,
    val focus: FocusStrings,
    val childHome: ChildHomeStrings,
    val parentHome: ParentHomeStrings,
    val parentDashboard: ParentDashboardStrings,
    val parentGate: ParentGateStrings,
)

val AppStringsZh = AppStrings(
    role = RoleStringsZh,
    settings = SettingsStringsZh,
    common = CommonStringsZh,
    taskList = TaskListStringsZh,
    pomodoro = PomodoroStringsZh,
    focus = FocusStringsZh,
    childHome = ChildHomeStringsZh,
    parentHome = ParentHomeStringsZh,
    parentDashboard = ParentDashboardStringsZh,
    parentGate = ParentGateStringsZh,
)
val AppStringsEn = AppStrings(
    role = RoleStringsEn,
    settings = SettingsStringsEn,
    common = CommonStringsEn,
    taskList = TaskListStringsEn,
    pomodoro = PomodoroStringsEn,
    focus = FocusStringsEn,
    childHome = ChildHomeStringsEn,
    parentHome = ParentHomeStringsEn,
    parentDashboard = ParentDashboardStringsEn,
    parentGate = ParentGateStringsEn,
)

fun stringsFor(language: DisplayLanguage): AppStrings = when (language) {
    DisplayLanguage.ZH -> AppStringsZh
    DisplayLanguage.EN -> AppStringsEn
}

val LocalStrings: ProvidableCompositionLocal<AppStrings> =
    staticCompositionLocalOf { error("LocalStrings 未提供：请在 App 根注入") }

/**
 * 生效语言（不含 SYSTEM），供页面做"仅某种语言态才出现"的文案分支。
 *
 * 为什么页面需要它：解析规则（appLanguageFromStorage(...).resolveWith(systemLanguageTag())）
 * 只在 App 根算一次。页面若自己再算一遍，就是第二份解析实现，将来改动解析时两边会漂移，
 * 而且每次重组都要重走一次平台桥接。所以根把算好的结果一并 provide 出来，页面只读不算。
 *
 * 这里刻意不给默认值：与 [LocalStrings] 一致地 fail-fast（漏注入 = 立刻报错，而不是静默按中文渲染）。
 * 全工程只有 App 根 provide 这两个 Local（另一处 CompositionLocalProvider 在
 * ui/theme/WindowSize.kt，与文案无关），所以这条错误只会在漏接线时出现。
 * 首个使用者是家长设置页的 partialTranslationNote（spec §7，仅英文态显示）。
 */
val LocalDisplayLanguage: ProvidableCompositionLocal<DisplayLanguage> =
    staticCompositionLocalOf { error("LocalDisplayLanguage 未提供：请在 App 根注入") }

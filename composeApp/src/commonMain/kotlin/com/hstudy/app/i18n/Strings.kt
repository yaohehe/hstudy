package com.hstudy.app.i18n

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** 跨页面文案聚合根：每页一个子表，后续任务按页扩展。 */
data class AppStrings(
    val role: RoleStrings,
)

val AppStringsZh = AppStrings(role = RoleStringsZh)
val AppStringsEn = AppStrings(role = RoleStringsEn)

fun stringsFor(language: DisplayLanguage): AppStrings = when (language) {
    DisplayLanguage.ZH -> AppStringsZh
    DisplayLanguage.EN -> AppStringsEn
}

val LocalStrings: ProvidableCompositionLocal<AppStrings> =
    staticCompositionLocalOf { error("LocalStrings 未提供：请在 App 根注入") }

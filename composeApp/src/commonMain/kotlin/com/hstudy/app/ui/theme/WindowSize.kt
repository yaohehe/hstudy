package com.hstudy.app.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 跨平台窗口尺寸档位：替代 Android-only 的 androidx.compose.material3.windowsizeclass。
 * 保持与 material3 同名的 API（WindowSizeClass.widthSizeClass / WindowWidthSizeClass.Compact 等），
 * 使各屏签名与判断逻辑无需改动；由 [ProvideWindowSizeClass] 用 BoxWithConstraints 按 Material 断点推导。
 */
object WindowWidthSizeClass {
    const val Compact = 0
    const val Medium = 1
    const val Expanded = 2
}

data class WindowSizeClass(val widthSizeClass: Int = WindowWidthSizeClass.Compact)

val LocalWindowSizeClass = compositionLocalOf { WindowSizeClass() }

/** Material 3 官方断点：Compact < 600dp，Medium < 840dp，Expanded >= 840dp */
fun windowWidthSizeClassOf(width: Dp): Int = when {
    width >= 840.dp -> WindowWidthSizeClass.Expanded
    width >= 600.dp -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Compact
}

@Composable
fun ProvideWindowSizeClass(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val sizeClass = WindowSizeClass(windowWidthSizeClassOf(maxWidth))
        CompositionLocalProvider(LocalWindowSizeClass provides sizeClass, content = content)
    }
}

package com.hstudy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 儿童友好圆角配置
val HStudyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Orange90,
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Green40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Green90,
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Blue40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Blue90,
    onTertiaryContainer = Color(0xFF0D47A1),
    error = Red40,
    errorContainer = Red80,
    background = WarmBg,
    onBackground = WarmOnSurface,
    surface = WarmSurface,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    outline = WarmOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Orange40,
    onPrimaryContainer = Orange90,
    secondary = Green80,
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = Green40,
    onSecondaryContainer = Green90,
    tertiary = Blue80,
    onTertiary = Color(0xFF0D47A1),
    tertiaryContainer = Blue40,
    onTertiaryContainer = Blue90,
    error = Red80,
    errorContainer = Red40,
    background = DarkBg,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
)

/**
 * 跨平台主题：去掉了 Android 的状态栏着色（Activity.window / WindowCompat），
 * 边到边与状态栏样式由平台侧（androidMain 的 Activity、iOS 的 Scene）负责。
 */
@Composable
fun HStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = HStudyShapes,
        content = content
    )
}

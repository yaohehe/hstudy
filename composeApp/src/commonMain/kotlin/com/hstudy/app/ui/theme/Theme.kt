package com.hstudy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * M3 形状等级（指令 §3.2.2）：业务页面一律经 `MaterialTheme.shapes.*` 取形，
 * 禁止裸数值 RoundedCornerShape(X.dp)。
 * extraSmall 4dp（标签/小徽章）、small 8dp（内嵌组件/进度条轨道）、
 * medium 12dp（对话框/小卡片）、large 16dp（标准业务卡片）、extraLarge 24dp（Hero 卡/大按钮）。
 */
val HStudyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * 亮色方案：语义色板 §3.2.1 全量接线，含 surfaceContainerLowest/Low/…/Highest 层级、
 * tertiary 角色族、outline/outlineVariant 与 inverse 角色（暖中性，取代 M3 默认冷紫）。
 * surfaceVariant 按 M3 规范对齐 containerHighest 层（页面旧引用不改语义继续工作）。
 */
private val LightColorScheme = lightColorScheme(
    primary = M3Primary,
    onPrimary = M3OnPrimary,
    primaryContainer = M3PrimaryContainer,
    onPrimaryContainer = M3OnPrimaryContainer,
    secondary = M3Secondary,
    onSecondary = M3OnSecondary,
    secondaryContainer = M3SecondaryContainer,
    onSecondaryContainer = M3OnSecondaryContainer,
    tertiary = M3Tertiary,
    onTertiary = M3OnTertiary,
    tertiaryContainer = M3TertiaryContainer,
    onTertiaryContainer = M3OnTertiaryContainer,
    error = M3Error,
    onError = M3OnError,
    errorContainer = M3ErrorContainer,
    onErrorContainer = M3OnErrorContainer,
    background = M3Surface,
    onBackground = M3OnSurface,
    surface = M3Surface,
    onSurface = M3OnSurface,
    surfaceVariant = M3SurfaceContainerHighest,
    onSurfaceVariant = M3OnSurfaceVariant,
    surfaceContainerLowest = M3SurfaceContainerLowest,
    surfaceContainerLow = M3SurfaceContainerLow,
    surfaceContainer = M3SurfaceContainer,
    surfaceContainerHigh = M3SurfaceContainerHigh,
    surfaceContainerHighest = M3SurfaceContainerHighest,
    outline = M3Outline,
    outlineVariant = M3OutlineVariant,
    inverseSurface = M3InverseSurface,
    inverseOnSurface = M3InverseOnSurface,
    inversePrimary = M3InversePrimary,
)

/**
 * 暗色方案：沿用既有暖黑底，按 §3.2.1 的角色补 tertiary/surfaceContainer 层级/outlineVariant，
 * 并接线 inverse 角色族（暗色审计 D1-dark/D2-dark：此前未接线回落 M3 默认冷紫，
 * 与暖橙品牌冲突且 snackbar 动作色实测 1.33:1）。实算：inverseOnSurface/inverseSurface 11.77:1、
 * inversePrimary/inverseSurface 4.87:1，均 ≥4.5。
 */
private val DarkColorScheme = darkColorScheme(
    primary = M3DarkPrimary,
    onPrimary = M3DarkOnPrimary,
    primaryContainer = M3DarkPrimaryContainer,
    onPrimaryContainer = M3DarkOnPrimaryContainer,
    secondary = M3DarkSecondary,
    onSecondary = M3DarkOnSecondary,
    secondaryContainer = M3DarkSecondaryContainer,
    onSecondaryContainer = M3DarkOnSecondaryContainer,
    tertiary = M3DarkTertiary,
    onTertiary = M3DarkOnTertiary,
    tertiaryContainer = M3DarkTertiaryContainer,
    onTertiaryContainer = M3DarkOnTertiaryContainer,
    error = M3DarkError,
    onError = M3DarkOnError,
    errorContainer = M3DarkErrorContainer,
    onErrorContainer = M3DarkOnErrorContainer,
    background = M3DarkBackground,
    onBackground = M3DarkOnBackground,
    surface = M3DarkSurface,
    onSurface = M3DarkOnSurface,
    surfaceVariant = M3DarkSurfaceContainerHigh,
    onSurfaceVariant = M3DarkOnSurfaceVariant,
    surfaceContainerLowest = M3DarkSurfaceContainerLowest,
    surfaceContainerLow = M3DarkSurfaceContainerLow,
    surfaceContainer = M3DarkSurfaceContainer,
    surfaceContainerHigh = M3DarkSurfaceContainerHigh,
    surfaceContainerHighest = M3DarkSurfaceContainerHighest,
    outline = M3DarkOutline,
    outlineVariant = M3DarkOutlineVariant,
    inverseSurface = M3DarkInverseSurface,
    inverseOnSurface = M3DarkInverseOnSurface,
    inversePrimary = M3DarkInversePrimary,
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

package com.hstudy.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Material 3 语义色板（Antigravity 指令 §3.2.1，逐值落实）
// 旧 token（Orange40/WarmBg/Gray*/PointsGold…）除 Theme.kt 外零引用，已退役；
// 业务页面一律经 MaterialTheme.colorScheme.* 取语义色，不再引用裸色值。
// ============================================================================

// ===== Primary（活力暖橙 · 孩子端主色）=====
// QA 审计 D1/D2：指令值 0xFFEF6C00 为 M3 tone-50，白字对比仅 3.08:1、作文字/线条
// 对 Surface 仅 2.93:1，双项不达 WCAG AA。落地值降为 tone-40 级同色相暖橙（Google M3
// 工具链本身即生成 tone-40 primary）：白字 5.52:1≥4.5；对 Surface 5.26:1≥4.5；
// 对 surfaceContainerLow/…/Highest 4.10–4.88:1，非文字 3:1 全过。
val M3Primary = Color(0xFFA65100)
val M3OnPrimary = Color(0xFFFFFFFF)
val M3PrimaryContainer = Color(0xFFFFDCC2)
val M3OnPrimaryContainer = Color(0xFF2E1500)

// ===== Secondary（清风竹绿 · 习惯与完成态）=====
val M3Secondary = Color(0xFF2E6C38)
val M3OnSecondary = Color(0xFFFFFFFF)
val M3SecondaryContainer = Color(0xFFB1F5B3)
val M3OnSecondaryContainer = Color(0xFF002107)

// ===== Tertiary（星空青蓝 · 专注与探索）=====
// 指令只给三值；OnTertiary 按容器对比度取白（与 Primary/Secondary 同形）。
val M3Tertiary = Color(0xFF00658F)
val M3OnTertiary = Color(0xFFFFFFFF)
val M3TertiaryContainer = Color(0xFFC7E7FF)
val M3OnTertiaryContainer = Color(0xFF001E2E)

// ===== Error（QA 审计 D3/D4：原 Red40 0xFFE53935 白字 4.23:1、正文色 3.82–4.03:1
// 均不达 WCAG AA，降为 Material Red tone-40 #C62828：白字 5.62:1；对 Surface 及
// container 层级作正文 4.98–5.35:1 全过；errorContainer/onErrorContainer 保持 13.26:1 不动）=====
val M3Error = Color(0xFFC62828)
val M3OnError = Color(0xFFFFFFFF)
val M3ErrorContainer = Color(0xFFFFDAD6)
val M3OnErrorContainer = Color(0xFF410002)

// ===== Neutral Surface Containers（温暖米白底色，消除纯灰冷感）=====
val M3Surface = Color(0xFFFFF8F5)
val M3OnSurface = Color(0xFF1D1B1A)
val M3SurfaceContainerLowest = Color(0xFFFFFFFF)
val M3SurfaceContainerLow = Color(0xFFF9F2EF)
val M3SurfaceContainer = Color(0xFFF3ECE9)
val M3SurfaceContainerHigh = Color(0xFFEDE6E3)
val M3SurfaceContainerHighest = Color(0xFFE7E1DD)
val M3OnSurfaceVariant = Color(0xFF52443C)
val M3Outline = Color(0xFF85736B)
val M3OutlineVariant = Color(0xFFD7C2B9)

// ===== Inverse 角色（Snackbar/反色容器；暖中性，取代 lightColorScheme 默认冷紫）=====
// 暗色审计 D1/D2 收口：三对组合均经 WCAG 实算——
// inverseOnSurface/inverseSurface 11.77:1；inversePrimary/inverseSurface 7.85:1（≥4.5）。
// 旧 snackbar action 直接用 tertiary（00658F）对 inverseSurface 仅 2.07:1，已改走 inversePrimary。
val M3InverseSurface = Color(0xFF342E2A)
val M3InverseOnSurface = Color(0xFFF7EFEA)
val M3InversePrimary = Color(0xFFFFB59A)

// ===== Dark theme（既有暗色继续工作：沿用暖黑底，补 surfaceContainer 层级与 outline）=====
val M3DarkPrimary = Color(0xFFFFB59A)
val M3DarkOnPrimary = Color(0xFF572B00)
val M3DarkPrimaryContainer = Color(0xFF7C3F00)
val M3DarkOnPrimaryContainer = Color(0xFFFFDCC2)
val M3DarkSecondary = Color(0xFF9BD7A1)
val M3DarkOnSecondary = Color(0xFF003910)
val M3DarkSecondaryContainer = Color(0xFF155124)
val M3DarkOnSecondaryContainer = Color(0xFFB1F5B3)
val M3DarkTertiary = Color(0xFF8ACDFF)
val M3DarkOnTertiary = Color(0xFF00344C)
val M3DarkTertiaryContainer = Color(0xFF004B6B)
val M3DarkOnTertiaryContainer = Color(0xFFC7E7FF)
val M3DarkError = Color(0xFFEF9A9A)
val M3DarkOnError = Color(0xFF601410)
val M3DarkErrorContainer = Color(0xFF8C1D18)
val M3DarkOnErrorContainer = Color(0xFFFFDAD6)
val M3DarkBackground = Color(0xFF1A1512)
val M3DarkOnBackground = Color(0xFFF3EAE0)
val M3DarkSurface = Color(0xFF221C18)
val M3DarkOnSurface = Color(0xFFF3EAE0)
val M3DarkSurfaceContainerLowest = Color(0xFF120E0B)
val M3DarkSurfaceContainerLow = Color(0xFF1D1814)
val M3DarkSurfaceContainer = Color(0xFF221C18)
val M3DarkSurfaceContainerHigh = Color(0xFF2D2620)
val M3DarkSurfaceContainerHighest = Color(0xFF38312A)
val M3DarkOnSurfaceVariant = Color(0xFFC4B5A6)
val M3DarkOutline = Color(0xFFA09087)
val M3DarkOutlineVariant = Color(0xFF53443C)

// ===== Dark Inverse 角色（暗色审计 D1-dark/D2-dark：此前未接线，回落 M3 默认冷紫）=====
// 亮色反色容器（暖米白）+ 深色文字 + tone-40 暖橙反色主色，实算：
// inverseOnSurface/inverseSurface 11.77:1；inversePrimary(A65100)/inverseSurface 4.87:1（≥4.5）。
// 更暗的候选容器（如 0xFFEFE3D8）会把 inversePrimary 压到 4.38:1，故取 0xFFF7EFEA。
val M3DarkInverseSurface = Color(0xFFF7EFEA)
val M3DarkInverseOnSurface = Color(0xFF342E2A)
val M3DarkInversePrimary = Color(0xFFA65100)

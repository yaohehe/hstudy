package com.hstudy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * M3 排版梯（儿童端紧凑节奏版）。
 *
 * 既有槽位（displayLarge 32 … labelMedium 12）保持逐值不变——各屏已按这套节奏排版，
 * 改动会造成全局布局漂移（"extend, don't break"）；本次补齐缺失的四个槽
 * （displaySmall / headlineSmall / titleSmall / labelSmall），使整条 scale 落在同一节奏上，
 * 页面不再回落到 Material3 默认（Roboto 冷节奏）。
 * Hero/积分数字按指令 §3.3.1 使用 [displayMedium] 加粗。
 */
val Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

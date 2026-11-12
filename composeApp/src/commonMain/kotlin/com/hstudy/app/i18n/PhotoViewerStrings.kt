package com.hstudy.app.i18n

/**
 * 照片查看页（ui/proof/PhotoViewerScreen.kt）文案表（阶段 2 W6）。
 *
 * 三条无障碍/提示文案；缩放系数区间（0.5f..5f）与手势处理留在页面（逻辑，不是文案）。
 * 行尾注释里的 `:行号` 是迁移前 PhotoViewerScreen.kt 的坐标。
 */
data class PhotoViewerStrings(
    val photoContentDescription: String,
    val closeContentDescription: String,
    val zoomHint: String,
)

val PhotoViewerStringsZh = PhotoViewerStrings(
    photoContentDescription = "查看照片",                                        // :41
    closeContentDescription = "关闭",                                           // :56
    zoomHint = "双指缩放查看",                                                   // :61
)

val PhotoViewerStringsEn = PhotoViewerStrings(
    photoContentDescription = "View photo",
    closeContentDescription = "Close",
    zoomHint = "Pinch to zoom",
)

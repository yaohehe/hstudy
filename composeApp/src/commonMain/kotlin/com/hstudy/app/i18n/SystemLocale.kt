package com.hstudy.app.i18n

/**
 * 当前系统语言标签（小写语言子标签，如 zh / en / fr）。
 *
 * 只取语言子标签、不取地区：文案仅中英双语，zh-Hans / zh-Hant 之类的区域变体不参与分支。
 * 这是双语第一阶段计划新增的唯一 expect/actual 点（仓库原有的 PlatformRuntime 等不受影响）。
 */
expect fun systemLanguageTag(): String

package com.hstudy.app.i18n

// currentLocale / localeIdentifier 在 platform.Foundation 里是扩展属性而非类成员，
// 因此必须单独 import，否则解析不到（缺 import 时 iOS 编译报 unresolved reference）。
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/**
 * 取 localeIdentifier 的语言子标签。Apple 的形状混用两种分隔符：语言与地区之间是 `_`
 * （en_US、ja_JP），书写系统之前是 `-`（zh-Hans_CN），两种都可能出现，
 * 故先按 `-` 再按 `_` 截断，结果只保留裸语言子标签（zh / en / ja，全小写）。
 * platform.Foundation 把 localeIdentifier 暴露为非空 String，故无需 try/catch；
 * 若个别系统给出空值，空串由 AppLanguage.resolveWith 按中文兜底。
 */
actual fun systemLanguageTag(): String =
    NSLocale.currentLocale.localeIdentifier
        .substringBefore("-")
        .substringBefore("_")
        .lowercase()

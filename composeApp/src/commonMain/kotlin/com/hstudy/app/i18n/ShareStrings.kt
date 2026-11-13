package com.hstudy.app.i18n

/**
 * 分享面板标题（spec §1/§3「分享标题例外」、L-08）。
 *
 * 为什么单独一个对象而不是 `AppStrings` 的一张表：系统分享面板的 chooser 标题是平台 API 的
 * 字符串参数（`Intent.createChooser(…, CharSequence)`），不在任何 Compose 组合里，拿不到
 * `LocalStrings`；而它只有一个调用点（androidMain 的 `AndroidFileSharer`）、不属于任何页面。
 * 做成整表只会让聚合根多一个恒不被页面使用的字段（等价性闸门还得替它登记一条"表侧单边"披露）。
 * iOS 侧没有分享面板标题槽位（`UIActivityViewController` 不暴露等价参数），本对象只服务 Android。
 *
 * 取词来源：调用方用 [currentDisplayLanguageSnapshot]（同步、不阻塞主线程）——
 * `AndroidFileSharer.shareTextFile` 是非 suspend 的平台接口，没有 suspend 上下文可用。
 * 中文值与迁移前逐字节一致（`AndroidFileSharer.kt` 旧字面量"分享文件"）。
 */
object ShareStrings {
    fun shareFileTitle(lang: DisplayLanguage): String = when (lang) {
        DisplayLanguage.ZH -> "分享文件"
        DisplayLanguage.EN -> "Share file"
    }
}

package com.hstudy.app.i18n

import java.util.Locale

/** JVM 的 language 子标签本身已是小写；lowercase() 只用于防御个别厂商 locale 数据。 */
actual fun systemLanguageTag(): String = Locale.getDefault().language.lowercase()

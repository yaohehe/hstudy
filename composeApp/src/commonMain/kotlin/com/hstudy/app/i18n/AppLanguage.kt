package com.hstudy.app.i18n

/** 用户可选的语言设置。 */
enum class AppLanguage {
    SYSTEM, ZH, EN;

    /** 解析为实际生效语言：显式选择优先；SYSTEM 下 zh* 走中文，空标签回退中文，其余走英文。 */
    fun resolveWith(systemTag: String): DisplayLanguage {
        val tag = systemTag.trim().lowercase()
        return when (this) {
            ZH -> DisplayLanguage.ZH
            EN -> DisplayLanguage.EN
            SYSTEM -> if (tag.isEmpty() || tag.startsWith("zh")) DisplayLanguage.ZH else DisplayLanguage.EN
        }
    }

    fun toStorageValue(): String = name.lowercase()
}

/** 生效语言，不含 SYSTEM 语义。 */
enum class DisplayLanguage { ZH, EN }

fun appLanguageFromStorage(raw: String?): AppLanguage = when (raw?.lowercase()) {
    "zh" -> AppLanguage.ZH
    "en" -> AppLanguage.EN
    else -> AppLanguage.SYSTEM
}

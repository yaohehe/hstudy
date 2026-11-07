package com.hstudy.app.i18n

import kotlin.test.assertNotEquals

/** 英文字段若与中文原样相同，视为漏译。 */
fun assertDiffers(vararg pairs: Pair<String, String>) {
    pairs.forEach { (zh, en) -> assertNotEquals(zh, en, "疑似漏译：$zh") }
}

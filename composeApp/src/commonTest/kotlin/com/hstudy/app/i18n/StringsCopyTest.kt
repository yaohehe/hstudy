package com.hstudy.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsCopyTest {
    @Test
    fun roleCopyDiffersBetweenLanguages() {
        val zh = AppStringsZh.role
        val en = AppStringsEn.role
        assertDiffers(
            zh.brandTitle to en.brandTitle,
            zh.tagline to en.tagline,
            zh.parentEntry to en.parentEntry,
            zh.parentDesc to en.parentDesc,
            zh.childEntry to en.childEntry,
            zh.childDesc to en.childDesc,
        )
    }

    @Test
    fun stringsForPicksInstance() {
        assertEquals(AppStringsZh, stringsFor(DisplayLanguage.ZH))
        assertEquals(AppStringsEn, stringsFor(DisplayLanguage.EN))
    }
}

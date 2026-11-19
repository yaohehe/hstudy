package com.hstudy.app.platform

import com.hstudy.app.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsMappingTest {
    @Test
    fun screenViewUsesScreenNameParam() {
        assertEquals(
            "screen_view" to mapOf("screen_name" to "child_home"),
            AnalyticsMapping.wire(AnalyticsEvent.ScreenView("child_home")),
        )
    }

    @Test
    fun everyFeatureMapsToSnakeCase() {
        AnalyticsEvent.Feature.entries.forEach { feature ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.FeatureUsed(feature))
            assertEquals("feature_used", name)
            assertEquals(feature.name.lowercase(), params["feature"])
            assertEquals(1, params.size)
        }
    }

    @Test
    fun everyReminderKindMaps() {
        ReminderKind.entries.forEach { kind ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.ReminderScheduled(kind))
            assertEquals("reminder_scheduled", name)
            assertEquals(kind.name.lowercase(), params["kind"])
        }
    }

    @Test
    fun languageChangedUsesStorageWireFormat() {
        AppLanguage.entries.forEach { language ->
            val (name, params) = AnalyticsMapping.wire(AnalyticsEvent.LanguageChanged(language))
            assertEquals("language_changed", name)
            assertEquals(language.toStorageValue(), params["to"])
        }
    }

    @Test
    fun zeroParamEvents() {
        assertEquals("backup_exported" to emptyMap(), AnalyticsMapping.wire(AnalyticsEvent.BackupExported))
        assertEquals("pairing_completed" to emptyMap(), AnalyticsMapping.wire(AnalyticsEvent.PairingCompleted))
    }

    @Test
    fun overlongParamDiscardedNotTruncated() {
        val (_, atLimit) = AnalyticsMapping.wire(AnalyticsEvent.ScreenView("x".repeat(100)))
        assertEquals("x".repeat(100), atLimit["screen_name"])
        val (_, over) = AnalyticsMapping.wire(AnalyticsEvent.ScreenView("x".repeat(101)))
        assertTrue(over.isEmpty())
    }

    @Test
    fun userPropertyNamesAreSnakeCase() {
        assertEquals(
            listOf("app_language", "user_role", "app_channel"),
            UserProperty.entries.map { AnalyticsMapping.propertyName(it) },
        )
    }
}

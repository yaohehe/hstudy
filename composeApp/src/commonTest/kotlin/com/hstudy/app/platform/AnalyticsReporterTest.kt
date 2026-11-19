package com.hstudy.app.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val properties = mutableListOf<Pair<UserProperty, String?>>()
    val enabledCalls = mutableListOf<Boolean>()
    override fun logEvent(event: AnalyticsEvent) { events.add(event) }
    override fun setUserProperty(property: UserProperty, value: String?) { properties.add(property to value) }
    override fun setEnabled(enabled: Boolean) { enabledCalls.add(enabled) }
    fun clear() { events.clear(); properties.clear(); enabledCalls.clear() }
}

class AnalyticsReporterTest {
    private val fake = FakeAnalytics()

    @BeforeTest
    fun setup() {
        Platform.analytics = fake
        AnalyticsReporter.setChannel("release")
        AnalyticsReporter.setLanguage("zh")
        AnalyticsReporter.setRole("parent")
        AnalyticsReporter.setEnabled(true)
        fake.clear()
    }

    @AfterTest
    fun teardown() {
        Platform.analytics = NoopAnalytics
    }

    @Test
    fun disabledReporterNeverTouchesPlatformAnalytics() {
        AnalyticsReporter.setEnabled(false)
        val offCalls = fake.enabledCalls.toList()
        fake.clear()
        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
        AnalyticsReporter.setLanguage("en")
        AnalyticsReporter.setRole("child")
        assertTrue(fake.events.isEmpty())
        assertTrue(fake.properties.isEmpty())
        assertTrue(fake.enabledCalls.isEmpty())
        assertEquals(listOf(false), offCalls)
    }

    @Test
    fun enabledReporterForwardsEvents() {
        AnalyticsReporter.log(AnalyticsEvent.ScreenView("child_home"))
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView("child_home")), fake.events)
    }

    @Test
    fun reenableResendsCurrentPropertiesButNoRetroactiveEvents() {
        AnalyticsReporter.setEnabled(false)
        AnalyticsReporter.log(AnalyticsEvent.FeatureUsed(AnalyticsEvent.Feature.CHECKIN))
        AnalyticsReporter.setLanguage("en")
        fake.clear()
        AnalyticsReporter.setEnabled(true)
        assertTrue(fake.events.isEmpty())
        assertEquals(
            listOf<Pair<UserProperty, String?>>(UserProperty.APP_CHANNEL to "release", UserProperty.APP_LANGUAGE to "en", UserProperty.USER_ROLE to "parent"),
            fake.properties,
        )
        assertEquals(listOf(true), fake.enabledCalls)
    }

    @Test
    fun languageAndRoleUpdatesForwardWhileEnabled() {
        AnalyticsReporter.setLanguage("en")
        AnalyticsReporter.setRole("child")
        assertEquals(
            listOf<Pair<UserProperty, String?>>(UserProperty.APP_LANGUAGE to "en", UserProperty.USER_ROLE to "child"),
            fake.properties,
        )
    }
}
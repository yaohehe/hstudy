package com.hstudy.app.platform

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AndroidFirebaseAnalytics(context: Context) : Analytics {
    private val firebase: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(event: AnalyticsEvent) {
        val (name, params) = AnalyticsMapping.wire(event)
        firebase.logEvent(name, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }

    override fun setUserProperty(property: UserProperty, value: String?) {
        firebase.setUserProperty(AnalyticsMapping.propertyName(property), value)
    }

    override fun setEnabled(enabled: Boolean) {
        firebase.setAnalyticsCollectionEnabled(enabled)
    }
}

package com.hstudy.app.platform

import com.hstudy.app.i18n.AppLanguage

enum class UserProperty { APP_LANGUAGE, USER_ROLE, APP_CHANNEL }

interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun setUserProperty(property: UserProperty, value: String?)
    fun setEnabled(enabled: Boolean)
}

object NoopAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) {}
    override fun setUserProperty(property: UserProperty, value: String?) {}
    override fun setEnabled(enabled: Boolean) {}
}

sealed class AnalyticsEvent {
    data class ScreenView(val route: String) : AnalyticsEvent()
    data class FeatureUsed(val feature: Feature) : AnalyticsEvent()
    data class ReminderScheduled(val kind: ReminderKind) : AnalyticsEvent()
    data class LanguageChanged(val to: AppLanguage) : AnalyticsEvent()
    object BackupExported : AnalyticsEvent()
    object PairingCompleted : AnalyticsEvent()

    enum class Feature {
        POMODORO_START, POMODORO_COMPLETE, CHECKIN, LUCKY_DRAW,
        PET_CARE, WISH_SUBMIT, APPROVAL_ACTION, BACKUP_EXPORT, PAIRING
    }
}

/** 唯一的事件→SDK wire 映射点：Android 与 Swift bridge 都消费它，避免两份映射漂移。 */
object AnalyticsMapping {
    fun wire(event: AnalyticsEvent): Pair<String, Map<String, String>> {
        val (name, params) = when (event) {
            is AnalyticsEvent.ScreenView -> "screen_view" to mapOf("screen_name" to event.route)
            is AnalyticsEvent.FeatureUsed -> "feature_used" to mapOf("feature" to event.feature.name.lowercase())
            is AnalyticsEvent.ReminderScheduled -> "reminder_scheduled" to mapOf("kind" to event.kind.name.lowercase())
            is AnalyticsEvent.LanguageChanged -> "language_changed" to mapOf("to" to event.to.toStorageValue())
            AnalyticsEvent.BackupExported -> "backup_exported" to emptyMap()
            AnalyticsEvent.PairingCompleted -> "pairing_completed" to emptyMap()
        }
        // spec §6：超长丢弃而非截断，避免把内容裁成可猜的片段
        return name to params.filterValues { it.length <= 100 }
    }

    fun propertyName(property: UserProperty): String = property.name.lowercase()
}
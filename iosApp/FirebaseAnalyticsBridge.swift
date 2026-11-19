import Foundation
import ComposeApp
import FirebaseCore
import FirebaseAnalytics

final class FirebaseAnalyticsBridge: NSObject, ComposeApp.Analytics {
    func logEvent(event: AnalyticsEvent) {
        guard FirebaseApp.app() != nil else { return }
        let wire = AnalyticsMapping.shared.wire(event: event)
        let name = wire.first as? String ?? ""
        let params = wire.second as? [String: String] ?? [:]
        FirebaseAnalytics.Analytics.logEvent(name, parameters: params)
    }

    func setUserProperty(property: UserProperty, value: String?) {
        guard FirebaseApp.app() != nil else { return }
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: AnalyticsMapping.shared.propertyName(property: property))
    }

    func setEnabled(enabled: Bool) {
        guard FirebaseApp.app() != nil else { return }
        FirebaseAnalytics.Analytics.setAnalyticsCollectionEnabled(enabled)
    }
}

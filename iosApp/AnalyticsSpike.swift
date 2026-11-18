import Foundation
import FirebaseAnalytics

enum AnalyticsSpike {
    static func fire() {
        guard FirebaseApp.app() != nil else {
            print("SPIKE: FirebaseApp not configured")
            return
        }
        Analytics.logEvent("spike_probe", parameters: ["probe": "v320"])
        print("SPIKE: logEvent issued")
    }
}
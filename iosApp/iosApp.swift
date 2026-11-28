import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseAnalytics
import UserNotifications

private let iosNotificationDelegate = IOSNotificationDelegate()

final class IOSNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let route = response.notification.request.content.userInfo["extra_route"] as? String {
            IosPlatformKt.handleIosNotificationRoute(route: route)
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    init() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        if FirebaseApp.app() == nil { print("analytics: FirebaseApp configure failed; noop") }
        let channel: String
        #if DEBUG
        channel = "debug"
        #else
        channel = "release"
        #endif
        // Set the notification delegate before SwiftUI creates the root controller so a cold-start
        // notification response is retained in Platform.pendingRoute and passes through the normal gate.
        UNUserNotificationCenter.current().delegate = iosNotificationDelegate
        IosPlatformKt.doInitIosPlatform(analytics: FirebaseAnalyticsBridge(), appChannel: channel)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosPlatformKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
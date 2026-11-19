import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseAnalytics

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
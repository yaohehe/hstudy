import SwiftUI
import ComposeApp
import FirebaseAnalytics

@main
struct iOSApp: App {
    init() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        AnalyticsSpike.fire()
        // Kotlin fun initIosPlatform() 导出到 Swift 时带 do 前缀
        IosPlatformKt.doInitIosPlatform()
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
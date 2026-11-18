import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
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
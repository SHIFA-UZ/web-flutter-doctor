import UIKit
import SwiftUI
import ComposeApp

/**
 * UIViewControllerRepresentable wrapper for the Compose UI.
 *
 * This struct bridges the Compose Multiplatform UI with SwiftUI,
 * allowing the Kotlin-based UI to be displayed in an iOS app.
 */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/**
 * Main content view for the iOS app.
 *
 * This view wraps the Compose UI and displays it full-screen,
 * ignoring safe areas for edge-to-edge display.
 */
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

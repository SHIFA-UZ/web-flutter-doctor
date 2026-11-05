import ComposeApp
import FirebaseCore
import FirebaseMessaging
import UIKit
import UserNotifications

/// Firebase is configured at launch; the FCM token and push taps reach the shared code with PUSH-01.
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        // VIDEO-01: the shared video visit makes its Daily calls through this.
        VideoCalls.shared.factory = { DailyVideoCall() }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }
}

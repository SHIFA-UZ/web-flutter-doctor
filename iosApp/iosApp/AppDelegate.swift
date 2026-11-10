import ComposeApp
import FirebaseCore
import FirebaseMessaging
import PostHog
import UIKit
import UserNotifications

/// Firebase at launch, and push (PUSH-01…03): the FCM token, pushes that arrive while the app is open and taps reach the
/// shared code through `Push`. The shell asks for permission once the doctor is signed in (`AskPushPermission`).
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        setUpTracking()
        // VIDEO-01: the shared video visit makes its Daily calls through this.
        VideoCalls.shared.factory = { DailyVideoCall() }
        // Set before launch ends, so a tap that started the app is delivered too.
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        // The APNs token (no prompt): Firebase needs it for the FCM token.
        application.registerForRemoteNotifications()
        return true
    }

    /// Product analytics (docs/posthog). Only release builds carry the key (Config.xcconfig, from the gitignored
    /// Secrets.xcconfig), so Debug builds and a fresh clone never start the SDK.
    private func setUpTracking() {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "PostHogApiKey") as? String, !key.isEmpty else { return }
        let config = PostHogConfig(projectToken: key, host: TrackingSinkKt.POSTHOG_HOST)
        config.captureApplicationLifecycleEvents = true
        config.captureScreenViews = false // UIKit only sees the one Compose controller; screens come from Kotlin
        config.captureElementInteractions = false
        config.capturePushNotificationSubscriptions = false
        config.capturePushNotificationOpened = false // the push payload names patients
        config.sessionReplay = false
        config.surveys = false
        config.personProfiles = .identifiedOnly
        config.errorTrackingConfig.autoCapture = true
        config.preloadFeatureFlags = false // the app reads no flags
        // ponytail: posthog-ios can't drop its queue on reset, so send each event at once; only events held while
        // offline outlive a reset. Batch again if the SDK gains a public queue clear.
        config.flushAt = 1
        // Setup itself sends `Application Installed` / `Updated` before anything can be registered (00-plan §4 rule 1).
        let app = TrackingSinkKt.TRACKING_APP
        config.setBeforeSend([{ event in
            event.properties["app"] = app
            return event
        }])
        config.optOut = TrackingBridgeKt.trackingStartsOff()
        PostHogSDK.shared.setup(config)
        Tracking.shared.start(sink: PostHogAnalytics(), webHost: nil)
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // PUSH-01: a new or refreshed FCM token (also at every launch).
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Push.shared.onToken(token: fcmToken)
    }

    // PUSH-02: a push while the app is open: shown as a banner (the backend's text is in the doctor's language).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        Push.shared.onReceived(data: Self.data(notification.request.content.userInfo))
        completionHandler([.banner, .list, .sound, .badge])
    }

    // PUSH-03: a tapped push, whether the app was open, in the background or not running.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        Push.shared.onTap(data: Self.data(response.notification.request.content.userInfo))
        completionHandler()
    }

    // The push's `data` keys as strings; APNs' own (`aps`) and Firebase's (`gcm.`, `google.`) are dropped.
    private static func data(_ userInfo: [AnyHashable: Any]) -> [String: String] {
        var out: [String: String] = [:]
        for (key, value) in userInfo {
            guard let key = key as? String, key != "aps", !key.hasPrefix("gcm."), !key.hasPrefix("google.") else { continue }
            if let value = value as? String { out[key] = value } else if let value = value as? NSNumber { out[key] = value.stringValue }
        }
        return out
    }
}

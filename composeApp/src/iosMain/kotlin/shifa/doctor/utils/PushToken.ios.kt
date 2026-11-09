package shifa.doctor.utils

// The APNs → FCM token arrives in Swift (AppDelegate), which hands it to `Push.onToken`.
actual suspend fun currentPushToken(): String? = null

actual val pushPlatform: String = "ios"

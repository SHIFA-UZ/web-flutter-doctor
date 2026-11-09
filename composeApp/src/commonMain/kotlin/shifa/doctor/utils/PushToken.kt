package shifa.doctor.utils

/**
 * This device's FCM token, or null when there is none yet. Android asks Firebase; iOS's token arrives in Swift (the
 * `FirebaseMessaging` delegate, which hands it to `Push`), so the call returns null there; the web has no push.
 */
expect suspend fun currentPushToken(): String?

/** `android` or `ios`, as `POST /doctors/me/fcm-tokens` names the platform (`web` never registers). */
expect val pushPlatform: String

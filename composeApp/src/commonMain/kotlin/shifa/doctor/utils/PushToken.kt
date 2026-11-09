package shifa.doctor.utils

/**
 * This device's FCM token, or null when there is none yet. Android asks Firebase; iOS's token arrives in Swift (the
 * `FirebaseMessaging` delegate) and the web's from index.html's `shifaPush`; both hand it to `Push`, so the call returns
 * null there.
 */
expect suspend fun currentPushToken(): String?

/** `android`, `ios` or `web`, as `POST /doctors/me/fcm-tokens` names the platform. */
expect val pushPlatform: String

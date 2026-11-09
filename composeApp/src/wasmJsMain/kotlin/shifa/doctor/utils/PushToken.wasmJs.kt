package shifa.doctor.utils

// The web's token comes from index.html's `shifaPush` (AskPushPermission), straight to `Push`.
actual suspend fun currentPushToken(): String? = null

actual val pushPlatform: String = "web"

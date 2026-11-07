package shifa.doctor.utils

// ponytail: the APNs → FCM token arrives in Swift (AppDelegate); PUSH-01 hands it to Kotlin.
actual suspend fun currentPushToken(): String? = null

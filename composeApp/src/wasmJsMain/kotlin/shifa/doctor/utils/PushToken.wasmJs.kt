package shifa.doctor.utils

// No push on the web (PUSH-01…03 cover the phones).
actual suspend fun currentPushToken(): String? = null

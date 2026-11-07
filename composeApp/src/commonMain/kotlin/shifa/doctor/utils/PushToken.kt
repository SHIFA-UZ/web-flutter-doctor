package shifa.doctor.utils

/**
 * This device's FCM token, or null when there is none yet (CORE-07 spike; PUSH-01 sends it with
 * `PUT /doctors/me/fcm-token`). iOS gets it through APNs and the Swift `FirebaseMessaging` delegate, which needs a
 * real device, so the shared call returns null there until PUSH-01 bridges it.
 */
expect suspend fun currentPushToken(): String?

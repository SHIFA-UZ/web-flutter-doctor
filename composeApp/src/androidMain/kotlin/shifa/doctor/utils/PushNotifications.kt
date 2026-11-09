package shifa.doctor.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import shifa.doctor.MainActivity
import shifa.doctor.R
import shifa.doctor.data.push.Push

/**
 * PUSH-01/02 on Android. FCM hands this service a new token and every push that arrives while the app is open (in the
 * background the system shows the push itself, on [CHANNEL] through the manifest, and a tap starts [MainActivity] with
 * the push's data). An open app shows it here: the backend's title and body (already in the doctor's language), the
 * data riding along for the tap.
 */
class ShifaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.onToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Push.onReceived(data)
        PushNotifications.show(
            this,
            title = message.notification?.title ?: data["title"],
            body = message.notification?.body ?: data["message"],
            data = data,
        )
    }
}

object PushNotifications {
    /** The channel id Flutter used, so the doctor's sound and importance settings carry over (07 §2.5). */
    const val CHANNEL = "shifa_doctor_channel"

    // Marks the extras of a notification this app built; the system's own carry `google.message_id`.
    private const val MARK = "shifa_push"

    // The rows shown while the app was open, so a push delivered twice rings once.
    private val shown = ArrayDeque<String>()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        title: String?,
        body: String?,
        data: Map<String, String>,
    ) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val id = data["notificationId"] ?: data["id"]
        synchronized(shown) {
            if (id != null && id in shown) return
            id?.let { shown.addLast(it) }
            if (shown.size > 50) shown.removeFirst()
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val tap =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtras(Bundle().apply { data.forEach { (k, v) -> putString(k, v) } })
                .putExtra(MARK, true)
        val code = id?.toIntOrNull() ?: data.hashCode()
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.splash_logo)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        code,
                        tap,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        try {
            manager.notify(code, notification)
        } catch (_: SecurityException) {
            // Notifications turned off between the check and here.
        }
    }

    /** A tapped push's data from the intent that started or reached [MainActivity], or null for any other intent. */
    fun tapped(intent: Intent?): Map<String, String>? {
        val extras = intent?.extras ?: return null
        if (!extras.containsKey(MARK) && !extras.containsKey("google.message_id")) return null
        return extras
            .keySet()
            .filter {
                it != MARK &&
                    !it.startsWith(
                        "google.",
                    ) && !it.startsWith("gcm.") && it != "from" && it != "collapse_key"
            }
            .mapNotNull { key -> extras.getString(key)?.let { key to it } }
            .toMap()
    }
}

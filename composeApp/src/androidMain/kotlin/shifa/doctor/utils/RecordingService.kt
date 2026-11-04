package shifa.doctor.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import shifa.doctor.R
import shifa.doctor.data.api.httpLogger

/**
 * Keeps the app in the foreground while the microphone records (SCRIBE-01: a consultation recorded with the screen
 * off or another app open). Since Android 9 a background app gets silence from the microphone; a foreground service
 * of type microphone keeps it, with a notification for as long as it runs. The recorder starts and stops it.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording_now))
                .setSmallIcon(R.drawable.splash_logo)
                .setOngoing(true)
                .setContentIntent(
                    packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                        android.app.PendingIntent.getActivity(this, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE)
                    },
                )
                .build()
        runCatching {
            val microphone = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            val type = if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure {
            // Not allowed now (the app wasn't in the foreground): the recording goes on while the app is in view.
            httpLogger.log("FAILED to keep recording in the background: ${it::class.simpleName}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)) }
                .onFailure { httpLogger.log("FAILED to start the recording service: ${it::class.simpleName}") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}

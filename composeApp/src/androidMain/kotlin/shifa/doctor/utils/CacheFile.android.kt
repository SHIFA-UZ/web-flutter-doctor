package shifa.doctor.utils

import android.content.Context
import java.io.File

/** Set by `ShifaDoctorApp.onCreate`, before any screen. */
internal lateinit var appContext: Context

actual fun writeCacheFile(
    name: String,
    bytes: ByteArray,
): String = File(appContext.cacheDir, name).apply { writeBytes(bytes) }.absolutePath

package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** The web has no share sheet for files a site makes: the browser downloads it. */
@Composable
actual fun rememberFileShare(): (fileName: String, mimeType: String, bytes: ByteArray) -> Boolean =
    { fileName, mimeType, bytes ->
        downloadBytes(safeFileName(fileName), mimeType, bytes)
        true
    }

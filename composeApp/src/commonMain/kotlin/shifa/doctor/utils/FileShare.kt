package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * The system share sheet with one file (ANALYTICS-02's CSV): written to the app's cache first, never to a public
 * folder, so nothing is left behind in Downloads. False when it couldn't be written or shown.
 */
@Composable
expect fun rememberFileShare(): (fileName: String, mimeType: String, bytes: ByteArray) -> Boolean

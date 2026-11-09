package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * PUSH-01: asks to show notifications once the doctor is signed in (Android 13+ while not granted: the system stops
 * asking after two refusals; iOS once, then registers with APNs). Nothing on the web or on older Androids.
 */
@Composable
expect fun AskPushPermission()

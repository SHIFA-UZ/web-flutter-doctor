package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * PUSH-01: asks to show notifications once the doctor is signed in (Android 13+ while not granted: the system stops
 * asking after two refusals; iOS once, then registers with APNs; the web on the first click or key, as browsers want).
 * Nothing on older Androids.
 */
@Composable
expect fun AskPushPermission()

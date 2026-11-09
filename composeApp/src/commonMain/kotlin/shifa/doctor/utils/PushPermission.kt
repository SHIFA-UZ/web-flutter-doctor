package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * PUSH-01: asks to show notifications once the doctor is signed in (Android 13+ while not granted: the system stops
 * asking after two refusals; iOS once, then registers with APNs; the web on the first click or key, as browsers want).
 * Nothing on older Androids.
 */
@Composable
expect fun AskPushPermission()

/**
 * The web's notification permission for this site: "granted", "default" (not asked yet), "denied" or "unsupported" (no
 * notifications in this browser, e.g. Safari on an iPhone outside a home-screen app). Null on the phones: the OS
 * settings decide there.
 */
expect fun browserNotificationPermission(): String?

/** Web: asks the browser now (needs a tap) and hands back the answer; the push bridge picks up a grant itself. */
expect fun askBrowserNotifications(done: (String) -> Unit)

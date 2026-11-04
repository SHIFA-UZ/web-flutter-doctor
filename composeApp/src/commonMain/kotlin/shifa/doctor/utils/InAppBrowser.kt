package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** Opens a web page in an in-app browser: a Custom Tab on Android, `SFSafariViewController` on iOS. */
@Composable
expect fun rememberInAppBrowser(): (url: String) -> Unit

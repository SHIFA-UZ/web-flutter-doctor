package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * Opens a web page in an in-app browser: a Custom Tab on Android, `SFSafariViewController` on iOS. False when it
 * couldn't (no browser, not an http(s) URL), so the caller can say so (CHAT-05: "Cannot open document URL").
 */
@Composable
expect fun rememberInAppBrowser(): (url: String) -> Boolean

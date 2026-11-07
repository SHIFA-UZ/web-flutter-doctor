package shifa.doctor.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberInAppBrowser(): (url: String) -> Boolean =
    { url -> (url.startsWith("https://") || url.startsWith("http://")) && openTab(url) }

package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
actual fun ProvideAppLocale(
    code: String,
    content: @Composable () -> Unit,
) {
    // Compose's Locale.current reads NSLocale.preferredLanguages, which follows AppleLanguages.
    remember(code) { NSUserDefaults.standardUserDefaults.setObject(listOf(code), forKey = "AppleLanguages") }
    CompositionLocalProvider(LocalAppLocale provides code, content = content)
}

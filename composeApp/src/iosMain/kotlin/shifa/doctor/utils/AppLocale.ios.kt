package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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

@Composable
internal actual fun stringOf(resource: StringResource): String = stringResource(resource)

package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
actual fun ProvideAppLocale(
    code: String,
    content: @Composable () -> Unit,
) {
    // Compose's Locale.current reads navigator.languages on the web: point it at the app's language.
    remember(code) { overrideBrowserLanguage(code) }
    CompositionLocalProvider(LocalAppLocale provides code, content = content)
}

private fun overrideBrowserLanguage(code: String) {
    js(
        "Object.defineProperty(navigator, 'languages', { get: function () { return [code] }, configurable: true });" +
            "Object.defineProperty(navigator, 'language', { get: function () { return code }, configurable: true })",
    )
}

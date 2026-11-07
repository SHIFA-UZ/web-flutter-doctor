package shifa.doctor.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
actual fun ProvideAppLocale(
    code: String,
    content: @Composable () -> Unit,
) {
    // Android resets the default locale on a configuration change, so it's re-applied per configuration.
    val system = LocalConfiguration.current
    val configuration =
        remember(code, system) {
            val locale = Locale.forLanguageTag(code)
            Locale.setDefault(locale)
            Configuration(system).apply { setLocale(locale) } // the Material date picker reads this one
        }
    CompositionLocalProvider(LocalConfiguration provides configuration, LocalAppLocale provides code, content = content)
}

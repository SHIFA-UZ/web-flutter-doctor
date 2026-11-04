package shifa.doctor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.ui.theme.ShifaTheme
import shifa.doctor.utils.LocalUzbekCyrillic
import shifa.doctor.utils.ProvideAppLocale

/** Root of the app: theme and language. Placeholder content until AUTH-01 adds splash and screen switching. */
@Composable
fun App() {
    val language by languageStorage.language.collectAsState()
    ProvideAppLocale(language.resourceCode) {
        CompositionLocalProvider(LocalUzbekCyrillic provides language.cyrillic) {
            ShifaTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Shifa Doctor", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

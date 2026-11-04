package shifa.doctor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.settings.languageRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.theme.ShifaTheme
import shifa.doctor.utils.LocalUzbekCyrillic
import shifa.doctor.utils.ProvideAppLocale
import shifa.doctor.utils.isDebugBuild

/** Root of the app: theme and language. Placeholder content until AUTH-01 adds splash and screen switching. */
@Composable
fun App() {
    val language by languageStorage.language.collectAsState()
    val session by sessionRepository.session.collectAsState()
    // 01 §8: the backend gets the UI language on sign-in and on every change (deduped in the repository).
    LaunchedEffect(session != null, language) {
        if (session != null) languageRepository.sync(language.backendCode)
    }
    ProvideAppLocale(language.resourceCode) {
        CompositionLocalProvider(LocalUzbekCyrillic provides language.cyrillic) {
            ShifaTheme {
                // Until AUTH-01: debug builds open the debug menu (CORE-08), release builds a placeholder.
                if (isDebugBuild) {
                    DebugMenuScreen()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Shifa Doctor", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

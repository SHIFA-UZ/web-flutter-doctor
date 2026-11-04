package shifa.doctor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.settings.languageRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.splash.SplashScreen
import shifa.doctor.ui.theme.ShifaTheme
import shifa.doctor.utils.LocalUzbekCyrillic
import shifa.doctor.utils.ProvideAppLocale
import shifa.doctor.utils.isDebugBuild

/** Root of the app: language, theme, and the top-level screens (no navigation library: CLAUDE.md). */
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
                // Debug menu → Demo data or Development backend restarts everything from the splash.
                val demo by DemoMode.enabled.collectAsState()
                val backend by BackendChoice.current.collectAsState()
                key(demo, backend) { ScopedViewModels { AppScreens() } }
            }
        }
    }
}

private enum class Screen { Splash, SignIn, Shell }

@Composable
private fun AppScreens() {
    // Not saveable: a recreated process starts at the splash again, which restores the session (01 §2.9).
    var screen by remember { mutableStateOf(Screen.Splash) }
    val session by sessionRepository.session.collectAsState()
    // One navigation for every way a session ends (sign-out, 401, expiry, disabled account: 17 §1, AUTH-09).
    LaunchedEffect(session) { if (session == null && screen == Screen.Shell) screen = Screen.SignIn }
    Crossfade(screen) {
        when (it) {
            Screen.Splash -> SplashScreen(onFinished = { s -> screen = if (s != null) Screen.Shell else Screen.SignIn })
            // AUTH-02 and SHELL-01 replace these. Debug builds reach the debug menu meanwhile.
            Screen.SignIn, Screen.Shell -> if (isDebugBuild) DebugMenuScreen() else Placeholder()
        }
    }
}

@Composable
private fun Placeholder() =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Shifa Doctor", style = MaterialTheme.typography.headlineSmall)
    }

/** A ViewModel store for what's inside, cleared when it leaves (a backend switch drops every ViewModel). */
@Composable
internal fun ScopedViewModels(content: @Composable () -> Unit) {
    val owner =
        remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

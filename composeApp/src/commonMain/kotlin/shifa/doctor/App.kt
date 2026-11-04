package shifa.doctor

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.settings.languageRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.signOut
import shifa.doctor.ui.auth.LanguageSwitch
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.auth.SignOutSheet
import shifa.doctor.ui.auth.SignOutViewModel
import shifa.doctor.ui.debug.DebugMenuScreen
import shifa.doctor.ui.debug.spikeAutoRun
import shifa.doctor.ui.emailcode.CodeScreen
import shifa.doctor.ui.emailcode.EmailCodeMode
import shifa.doctor.ui.emailcode.EmailEntryScreen
import shifa.doctor.ui.emailcode.ResetPasswordScreen
import shifa.doctor.ui.login.LoginScreen
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.navigation.ShellNoticeBanner
import shifa.doctor.ui.register.RegisterScreen
import shifa.doctor.ui.register.VerifyKeyScreen
import shifa.doctor.ui.splash.SplashScreen
import shifa.doctor.ui.theme.ShifaTheme
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.utils.LocalUzbekCyrillic
import shifa.doctor.utils.ProvideAppLocale
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.tr

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
        CompositionLocalProvider(
            LocalUzbekCyrillic provides language.cyrillic,
            LocalLanguageSwitch provides LanguageSwitch(language, languageStorage::set),
        ) {
            ShifaTheme {
                // Debug menu → Demo data or Development backend restarts everything from the splash.
                val demo by DemoMode.enabled.collectAsState()
                val backend by BackendChoice.current.collectAsState()
                key(demo, backend) { ScopedViewModels { AppScreens() } }
            }
        }
    }
}

// The signed-out screens and the shell; data classes carry what the next screen needs (no navigation library).
private sealed interface Screen {
    data object Splash : Screen

    data object SignIn : Screen

    data class EmailEntry(
        val mode: EmailCodeMode,
        val email: String = "",
    ) : Screen

    data class EmailCode(
        val email: String,
        val mode: EmailCodeMode,
    ) : Screen

    data class ResetPassword(
        val email: String,
        val code: String,
    ) : Screen

    data object VerifyKey : Screen

    /** AUTH-06 (doctor) or AUTH-08 (receptionist), by the invitation's purpose. */
    data class Register(
        val invite: Invite,
    ) : Screen

    data object Debug : Screen

    data object Shell : Screen
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppScreens() {
    // Not saveable: a recreated process starts at the splash again, which restores the session (01 §2.9). Each
    // screen has its own ViewModel store, cleared when it leaves (Login must not keep a finished sign-in).
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    val session by sessionRepository.session.collectAsState()

    // A screen that is leaving (Crossfade keeps it a moment) must not navigate when its request finishes late.
    fun go(
        from: Screen,
        to: Screen,
    ) {
        if (screen == from) screen = to
    }
    // One navigation for every way a session ends (sign-out, 401, expiry, disabled account: 17 §1, AUTH-09).
    // Keyed on the screen too: a screen that finishes late (registration) must not open the shell after the session
    // ended. A note left for the shell belongs to the session that left it.
    LaunchedEffect(session, screen) { if (session == null && screen == Screen.Shell) screen = Screen.SignIn }
    LaunchedEffect(session) { if (session == null) ShellNotice.pending.value = null }
    Crossfade(screen) {
        when (it) {
            Screen.Splash ->
                ScopedViewModels {
                    SplashScreen(onFinished = { s ->
                        screen =
                            when {
                                spikeAutoRun -> Screen.Debug // the iOS spike check (CORE-07)
                                s != null -> Screen.Shell
                                else -> Screen.SignIn
                            }
                    })
                }
            Screen.SignIn ->
                ScopedViewModels {
                    LoginScreen(
                        onSignedIn = { go(Screen.SignIn, Screen.Shell) },
                        onEmailSignIn = { screen = Screen.EmailEntry(EmailCodeMode.SignIn) },
                        onForgotPassword = { screen = Screen.EmailEntry(EmailCodeMode.Reset) },
                        onCreateAccount = { screen = Screen.VerifyKey },
                        debugMenu = if (isDebugBuild) debugLink { screen = Screen.Debug } else null,
                    )
                }
            is Screen.EmailEntry -> {
                val back = { screen = Screen.SignIn }
                BackHandler(onBack = back)
                ScopedViewModels {
                    EmailEntryScreen(it.mode, back, onSent = {
                            email ->
                        screen = Screen.EmailCode(email, it.mode)
                    })
                }
            }
            is Screen.EmailCode -> {
                val back = { screen = Screen.EmailEntry(it.mode, it.email) }
                BackHandler(onBack = back)
                ScopedViewModels {
                    CodeScreen(
                        it.email,
                        it.mode,
                        back,
                        onSignedIn = { _ -> go(it, Screen.Shell) },
                        onResetCode = { email, code -> go(it, Screen.ResetPassword(email, code)) },
                    )
                }
            }
            is Screen.ResetPassword -> {
                val back = { screen = Screen.EmailCode(it.email, EmailCodeMode.Reset) }
                BackHandler(onBack = back)
                ScopedViewModels {
                    ResetPasswordScreen(it.email, it.code, back, onSignedIn = {
                            _ ->
                        go(it, Screen.Shell)
                    })
                }
            }
            Screen.VerifyKey -> {
                val back = { screen = Screen.SignIn }
                BackHandler(onBack = back)
                ScopedViewModels {
                    VerifyKeyScreen(back, onVerified = {
                            invite ->
                        go(Screen.VerifyKey, Screen.Register(invite))
                    })
                }
            }
            // Back from registration returns to Sign In (flow 01/06 §5). The invitation picks the doctor's or the
            // receptionist's form.
            is Screen.Register ->
                ScopedViewModels {
                    RegisterScreen(it.invite, onExit = { go(it, Screen.SignIn) }, onSignedIn = { _ ->
                        go(it, Screen.Shell)
                    })
                }
            Screen.Debug -> {
                BackHandler { screen = Screen.SignIn }
                DebugMenuScreen(onBack = { screen = Screen.SignIn })
            }
            // SHELL-01 replaces this. Debug builds reach the debug menu meanwhile.
            // SHELL-01 replaces this; until then it offers the sign-out question (AUTH-09).
            Screen.Shell ->
                ScopedViewModels {
                    val signOut = viewModel { SignOutViewModel() }
                    val question by signOut.state.collectAsState()
                    Column(Modifier.fillMaxSize()) {
                        ShellNoticeBanner()
                        Box(Modifier.weight(1f)) {
                            if (isDebugBuild) DebugMenuScreen(onSignOut = signOut::ask) else Placeholder(signOut::ask)
                        }
                    }
                    SignOutSheet(question, signOut::confirm, signOut::cancel)
                }
        }
    }
}

private fun debugLink(open: () -> Unit): @Composable () -> Unit = { ShifaLink("Debug menu", open) }

@Composable
private fun Placeholder(onSignOut: () -> Unit) =
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Shifa Doctor", style = MaterialTheme.typography.headlineSmall)
        ShifaLink(tr(Res.string.signOut), onSignOut)
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

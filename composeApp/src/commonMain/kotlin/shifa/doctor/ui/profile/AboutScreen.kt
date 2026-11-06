package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.aboutCopyDiagnostics
import shifa.doctor.generated.resources.aboutCopyDiagnosticsHint
import shifa.doctor.generated.resources.aboutDiagnosticsCopied
import shifa.doctor.generated.resources.aboutLegal
import shifa.doctor.generated.resources.aboutOpenFailed
import shifa.doctor.generated.resources.aboutOpensSite
import shifa.doctor.generated.resources.aboutPrivacyPolicy
import shifa.doctor.generated.resources.aboutVersion
import shifa.doctor.generated.resources.appName
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.appBuild
import shifa.doctor.utils.appVersion
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.platformName
import shifa.doctor.utils.rememberInAppBrowser
import shifa.doctor.utils.tr

/** The public privacy notice (the one legal page Shifa publishes today; the patient app links the same). */
const val PRIVACY_URL = "https://shifa.uz/#privacy"

/**
 * About (LEGAL-01, flow 08/13, board `DaProfile` legalAbout): the app and its version (seven taps, or the row, copy
 * diagnostics for support: version, platform, language, practice zone; no names, no patient data), Legal with the
 * privacy policy. Decided by the LEGAL-01 spike: the backend has no doctor-side contract or legal-documents endpoint
 * (BE-16), so the contract card (LEGAL-02), terms, licences and support contacts are left out instead of invented.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val browser = rememberInAppBrowser()
    val clipboard = LocalClipboardManager.current
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val backend by BackendChoice.current.collectAsStateWithLifecycle()
    val demo by DemoMode.enabled.collectAsStateWithLifecycle()
    // The app's own language (uz and uz-Cyrl differ here, not in the platform locale).
    val language = LocalLanguageSwitch.current.current.tag
    val copy = {
        val environment = if (demo) "demo" else backend.name.lowercase()
        val line =
            diagnostics(
                "$appVersion ($appBuild)",
                "$platformName ${if (isDebugBuild) "debug" else "release"}",
                environment,
                language,
                me?.profile?.timeZone,
            )
        clipboard.setText(AnnotatedString(line))
        ShellNotice.pending.value = Notice(Res.string.aboutDiagnosticsCopied, success = true)
    }
    var taps by remember { mutableIntStateOf(0) }
    AboutContent(
        onBack = onBack,
        openPrivacy = { if (!browser(PRIVACY_URL)) ShellNotice.pending.value = Notice(Res.string.aboutOpenFailed) },
        copyDiagnostics = copy,
        // Seven taps on the version (the board's hidden gesture); the row is the accessible way.
        versionTap = {
            taps = (taps + 1) % DIAGNOSTIC_TAPS
            if (taps == 0) copy()
        },
    )
}

/** What support gets: the build and the environment, never names or patient data (flow 08/13 §4). */
internal fun diagnostics(
    version: String,
    platform: String,
    environment: String,
    language: String,
    zone: String?,
) = "Shifa Doctor $version · $platform · $environment · $language · ${zone ?: "-"}"

@Composable
fun AboutContent(
    onBack: () -> Unit,
    openPrivacy: () -> Unit,
    copyDiagnostics: () -> Unit,
    versionTap: () -> Unit,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(tr(Res.string.about), Modifier.statusBarsPadding(), onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
    ) {
        // The app and its version (taps counted by the screen: seven copy diagnostics).
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = versionTap)
                .padding(vertical = ShifaSize.S12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            Box(
                Modifier.size(ShifaSize.S56).background(ShifaColors.Interactive.Primary, ShifaRadius.ControlLg),
                Alignment.Center,
            ) {
                Text("S", style = MaterialTheme.typography.headlineSmall, color = ShifaColors.Text.Inverse)
            }
            Text(tr(Res.string.appName), style = MaterialTheme.typography.titleMedium, color = ShifaColors.Text.Primary)
            Text(
                tr(Res.string.aboutVersion, appVersion),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        ShifaSection(tr(Res.string.aboutLegal)) {
            ShifaSettingsItem(
                tr(Res.string.aboutPrivacyPolicy),
                openPrivacy,
                subtitle = tr(Res.string.aboutOpensSite),
                divider = false,
            )
        }
        ShifaSection(null) {
            ShifaSettingsItem(
                tr(Res.string.aboutCopyDiagnostics),
                copyDiagnostics,
                subtitle = tr(Res.string.aboutCopyDiagnosticsHint),
                divider = false,
            )
        }
    }
}

private const val DIAGNOSTIC_TAPS = 7

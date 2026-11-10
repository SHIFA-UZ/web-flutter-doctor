package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.debug.DemoMode
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ProfileEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.aboutContractFrom
import shifa.doctor.generated.resources.aboutCopyDiagnostics
import shifa.doctor.generated.resources.aboutCopyDiagnosticsHint
import shifa.doctor.generated.resources.aboutDiagnosticsCopied
import shifa.doctor.generated.resources.aboutLegal
import shifa.doctor.generated.resources.aboutLicences
import shifa.doctor.generated.resources.aboutMyContract
import shifa.doctor.generated.resources.aboutOpenFailed
import shifa.doctor.generated.resources.aboutOpensSite
import shifa.doctor.generated.resources.aboutPrivacyPolicy
import shifa.doctor.generated.resources.aboutTermsOfService
import shifa.doctor.generated.resources.aboutVersion
import shifa.doctor.generated.resources.appName
import shifa.doctor.generated.resources.contractLoadFailed
import shifa.doctor.generated.resources.retry
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAppIcon
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.PdfView
import shifa.doctor.utils.appBuild
import shifa.doctor.utils.appVersion
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.isDebugBuild
import shifa.doctor.utils.platformName
import shifa.doctor.utils.rememberInAppBrowser
import shifa.doctor.utils.tr

/** The public privacy notice (the one legal page Shifa publishes today; the patient app links the same). */
const val PRIVACY_URL = "https://shifa.uz/#privacy"

/**
 * About (LEGAL-01/02, flow 08/13, board `DaProfile` legalAbout): the app and its version (seven taps, or the row, copy
 * diagnostics for support: version, platform, language, practice zone; no names, no patient data), Legal (terms,
 * privacy and licences as the backend lists them, BE-16; the public privacy page when it doesn't) and "My partnership
 * contract" when the doctor has one, its PDF opened here. Support contacts are still left out (none published).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    staff: Boolean = false,
    viewModel: AboutViewModel = viewModel { AboutViewModel(doctor = !staff) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        Tracking.track(ProfileEvents.DiagnosticsCopied)
        ShellNotice.pending.value = Notice(Res.string.aboutDiagnosticsCopied, success = true)
    }
    var taps by remember { mutableIntStateOf(0) }
    val open = { url: String ->
        if (!browser(url)) {
            ShellNotice.pending.value = Notice(Res.string.aboutOpenFailed)
        } else {
            val links = state.links
            // The public privacy page stands in when the backend lists none.
            val link =
                when (url) {
                    links?.terms -> "terms"
                    links?.licences -> "licences"
                    else -> "privacy"
                }
            Tracking.track(ProfileEvents.LegalLinkOpened(link))
        }
    }
    if (state.viewing) {
        // System Back closes the contract, not About.
        BackHandler(onBack = viewModel::closeContract)
        ContractViewer(state, viewModel::closeContract, viewModel::loadPdf)
        return
    }
    AboutContent(
        onBack = onBack,
        state = state,
        openUrl = open,
        openContract = viewModel::openContract,
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
    state: AboutUiState,
    openUrl: (String) -> Unit,
    openContract: () -> Unit,
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
            ShifaAppIcon(ShifaSize.S56)
            Text(tr(Res.string.appName), style = MaterialTheme.typography.titleMedium, color = ShifaColors.Text.Primary)
            Text(
                tr(Res.string.aboutVersion, appVersion),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        // Board: Terms, Privacy and Licences, each with its version (the backend's list, BE-16).
        val links = state.links
        val version = links?.version?.let { tr(Res.string.aboutVersion, it) } ?: tr(Res.string.aboutOpensSite)
        val legal =
            listOfNotNull(
                links?.terms?.let { Res.string.aboutTermsOfService to it },
                Res.string.aboutPrivacyPolicy to (links?.privacy ?: PRIVACY_URL),
                links?.licences?.let { Res.string.aboutLicences to it },
            )
        ShifaSection(tr(Res.string.aboutLegal)) {
            Column {
                legal.forEachIndexed { i, (title, url) ->
                    ShifaSettingsItem(tr(title), { openUrl(url) }, subtitle = version, divider = i < legal.lastIndex)
                }
            }
        }
        state.contract?.let { c ->
            ShifaSection(tr(Res.string.aboutMyContract)) {
                ShifaSettingsItem(
                    c.contractNumber,
                    openContract,
                    subtitle = c.from?.let { tr(Res.string.aboutContractFrom, displayDate(it)) },
                    divider = false,
                )
            }
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

/** LEGAL-02: the contract the backend renders, full screen: a page skeleton while it loads, why not with Retry. */
@Composable
private fun ContractViewer(
    state: AboutUiState,
    onBack: () -> Unit,
    retry: () -> Unit,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(
        state.contract?.contractNumber ?: tr(Res.string.aboutMyContract),
        Modifier.statusBarsPadding(),
        onBack = onBack,
    )
    val error = state.pdfError
    when {
        error != null ->
            Column(Modifier.padding(ShifaSpacing.Inset.Md), verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
                codeBanner(error)?.invoke()
                ShifaLink(tr(Res.string.retry), retry)
            }
        state.pdf == null -> ShifaSkeleton(Modifier.fillMaxSize().padding(ShifaSpacing.Inset.Md))
        else ->
            PdfView(state.pdf, Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(ShifaSpacing.Inset.Md),
                ) { codeBanner(CodeBanner(Res.string.contractLoadFailed))?.invoke() }
            }
    }
}

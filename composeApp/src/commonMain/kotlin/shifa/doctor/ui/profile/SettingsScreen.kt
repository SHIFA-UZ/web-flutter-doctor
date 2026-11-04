package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.applock.appLockRepository
import shifa.doctor.data.model.DoctorSettings
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.LanguageStorage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.appLockOff
import shifa.doctor.generated.resources.appLockOn
import shifa.doctor.generated.resources.biometricUnlock
import shifa.doctor.generated.resources.country
import shifa.doctor.generated.resources.encryptedDocuments
import shifa.doctor.generated.resources.germany
import shifa.doctor.generated.resources.ic_globe_22
import shifa.doctor.generated.resources.ic_info_22
import shifa.doctor.generated.resources.ic_shield_22
import shifa.doctor.generated.resources.language
import shifa.doctor.generated.resources.legalAndSupport
import shifa.doctor.generated.resources.notifications
import shifa.doctor.generated.resources.notificationsSystemSettings
import shifa.doctor.generated.resources.otherCountry
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.security
import shifa.doctor.generated.resources.settings
import shifa.doctor.generated.resources.settingsGeneral
import shifa.doctor.generated.resources.startTabHelper
import shifa.doctor.generated.resources.startingScreen
import shifa.doctor.generated.resources.twoFactorAuthentication
import shifa.doctor.generated.resources.usa
import shifa.doctor.generated.resources.uzbekistan
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.ShellTab
import shifa.doctor.ui.navigation.label
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.openAppSettings
import shifa.doctor.utils.tr

/** The country values the backend stores (English words, 08 §1.8) with their labels. */
enum class SettingsCountry(
    val wire: String,
    val label: StringResource,
) {
    Uzbekistan("Uzbekistan", Res.string.uzbekistan),
    Germany("Germany", Res.string.germany),
    Usa("USA", Res.string.usa),
    Other("Other", Res.string.otherCountry),
}

data class SettingsUiState(
    val staff: Boolean = false,
    val settings: DoctorSettings? = null,
    val tasksAllowed: Boolean = false,
    val picking: SettingsPick? = null,
    val banner: CodeBanner? = null,
    // A settings PATCH is on its way (flow 08/10 §3: a ring until it lands).
    val saving: Boolean = false,
    // The Security row's preview (SET-03): app lock on, and with biometrics.
    val appLock: Boolean = false,
    val biometrics: Boolean = false,
) {
    val startTabs: List<ShellTab>
        get() = ShellTab.entries.filter { it != ShellTab.Tasks || tasksAllowed }
}

enum class SettingsPick { Country, StartTab }

/**
 * SET-01 (flow 08/10, DaProfile `settings`): country, language, starting screen, two-factor and encrypted-documents
 * flags. Every change shows at once and goes out as the whole settings block (`PATCH /doctors/me/settings`), built from
 * the latest state when its turn comes (one queue), so two quick toggles can't overwrite each other; a failure goes
 * back to what the server last took. Clinic staff have no doctor settings: they see Language, Security and the rest.
 */
class SettingsViewModel(
    private val staff: Boolean,
    private val profile: ProfileRepository = profileRepository,
    private val language: LanguageStorage = languageStorage,
    private val lock: AppLockRepository = appLockRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(staff = staff))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val writes = Mutex()
    private var confirmed: DoctorSettings? = null
    private var pending = 0

    init {
        // The plan can change while this screen is open (resume refresh): the Tasks choice follows it.
        if (!staff) {
            viewModelScope.launch {
                profile.me.collect { me ->
                    val allowed = FeatureGate.of(me?.subscription).allows(DoctorFeature.RemoteCareTasks)
                    _state.update { it.copy(tasksAllowed = allowed) }
                }
            }
        }
    }

    fun open() {
        // Read on every open: the lock is set on the Security screen and Back lands here.
        _state.update { it.copy(appLock = lock.enabled, biometrics = lock.biometricEnabled) }
        if (staff || writes.isLocked) return
        val me = profile.me.value ?: return
        confirmed = me.settings
        _state.update { it.copy(settings = me.settings) }
    }

    fun ui(change: (SettingsUiState) -> SettingsUiState) = _state.update(change)

    fun change(edit: (DoctorSettings) -> DoctorSettings) {
        val current = _state.value.settings ?: return
        pending++
        _state.update { it.copy(settings = edit(current), picking = null, banner = null, saving = true) }
        viewModelScope.launch {
            writes.withLock {
                val s = _state.value
                val shown = s.settings ?: return@withLock
                // The language goes as the backend code (Cyrillic Uzbek is "uz"); a start tab the plan no longer allows
                // is repaired to Home (flow 08/10 §8).
                val body =
                    shown.copy(
                        language = language.language.value.backendCode,
                        defaultStartTab =
                            shown.defaultStartTab.takeIf {
                                    tab ->
                                s.startTabs.any { it.key == tab }
                            } ?: ShellTab.Home.key,
                    )
                if (body == confirmed) return@withLock
                profile.updateSettings(body).fold(
                    onSuccess = { confirmed = it },
                    onFailure = { e ->
                        // Only what this request carried goes back; a change made since stays and goes out next.
                        _state.update { st ->
                            st.copy(
                                settings = st.settings?.let { now -> revert(now, body, confirmed ?: now) },
                                banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                            )
                        }
                    },
                )
            }
            pending--
            _state.update { it.copy(saving = pending > 0) }
        }
    }

    private fun revert(
        now: DoctorSettings,
        sent: DoctorSettings,
        server: DoctorSettings,
    ) = DoctorSettings(
        country = if (now.country == sent.country) server.country else now.country,
        language = now.language,
        twoFA = if (now.twoFA == sent.twoFA) server.twoFA else now.twoFA,
        encryptedDocs = if (now.encryptedDocs == sent.encryptedDocs) server.encryptedDocs else now.encryptedDocs,
        defaultStartTab =
            if (now.defaultStartTab == sent.defaultStartTab) server.defaultStartTab else now.defaultStartTab,
    )
}

class SettingsActions(
    val back: () -> Unit = {},
    val language: () -> Unit = {},
    val security: () -> Unit = {},
    val about: () -> Unit = {},
    val debug: (() -> Unit)? = null,
    val change: ((DoctorSettings) -> DoctorSettings) -> Unit = {},
    val ui: ((SettingsUiState) -> SettingsUiState) -> Unit = {},
)

@Composable
fun SettingsScreen(
    staff: Boolean,
    actions: SettingsActions,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(staff) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open() }
    SettingsContent(
        state,
        SettingsActions(
            actions.back,
            actions.language,
            actions.security,
            actions.about,
            actions.debug,
            viewModel::change,
            viewModel::ui,
        ),
        languageStorage.language.collectAsStateWithLifecycle().value.displayName,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    languageName: String,
) {
    val s = state.settings
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.settings), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
            if (state.saving) ShifaSpinner(ShifaColors.Interactive.Primary, Modifier.align(Alignment.End))
            ShifaSection(tr(Res.string.settingsGeneral)) {
                Column {
                    if (s != null) {
                        val country =
                            SettingsCountry.entries.firstOrNull {
                                it.wire.equals(
                                    s.country,
                                    true,
                                )
                            } ?: SettingsCountry.Uzbekistan
                        ShifaSettingsItem(tr(Res.string.country), {
                            actions.ui { it.copy(picking = SettingsPick.Country) }
                        }, subtitle = tr(country.label))
                    }
                    ShifaSettingsItem(
                        tr(Res.string.language),
                        actions.language,
                        icon = Res.drawable.ic_globe_22,
                        subtitle = languageName,
                    )
                    if (s != null) {
                        val tab = state.startTabs.firstOrNull { it.key == s.defaultStartTab } ?: ShellTab.Home
                        ShifaSettingsItem(
                            tr(Res.string.startingScreen),
                            { actions.ui { it.copy(picking = SettingsPick.StartTab) } },
                            subtitle = tr(tab.label),
                            divider = false,
                        )
                        Text(
                            tr(Res.string.startTabHelper),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
            }
            ShifaSection(tr(Res.string.security)) {
                Column {
                    ShifaSettingsItem(
                        tr(Res.string.security),
                        actions.security,
                        icon = Res.drawable.ic_shield_22,
                        subtitle =
                            if (state.appLock) {
                                listOfNotNull(
                                    tr(Res.string.appLockOn),
                                    tr(Res.string.biometricUnlock).takeIf { state.biometrics },
                                )
                                    .joinToString(" · ")
                            } else {
                                tr(Res.string.appLockOff)
                            },
                        divider = s != null,
                    )
                    if (s != null) {
                        SwitchRow(
                            tr(Res.string.twoFactorAuthentication),
                            s.twoFA,
                        ) { v -> actions.change { it.copy(twoFA = v) } }
                        SwitchRow(
                            tr(Res.string.encryptedDocuments),
                            s.encryptedDocs,
                        ) { v -> actions.change { it.copy(encryptedDocs = v) } }
                    }
                }
            }
            ShifaSection(null) {
                Column {
                    // No in-app notification preferences: the OS decides (flow 08/10 §3.4).
                    ShifaSettingsItem(
                        tr(Res.string.notifications),
                        ::openAppSettings,
                        subtitle = tr(Res.string.notificationsSystemSettings),
                    )
                    ShifaSettingsItem(
                        tr(Res.string.about),
                        actions.about,
                        icon = Res.drawable.ic_info_22,
                        subtitle = tr(Res.string.legalAndSupport),
                        divider = actions.debug != null,
                    )
                    actions.debug?.let { ShifaSettingsItem("Debug menu", it, divider = false) }
                }
            }
        }
    }
    val pick = state.picking
    if (pick != null && s != null) {
        ProfileSheet({ actions.ui { it.copy(picking = null) } }) {
            when (pick) {
                SettingsPick.Country ->
                    ShifaTray(tr(Res.string.country), { actions.ui { it.copy(picking = null) } }) {
                        ShifaOptionList(
                            SettingsCountry.entries.map {
                                OptionItem(
                                    tr(it.label),
                                    selected = it.wire.equals(s.country, true),
                                )
                            },
                            { i -> actions.change { it.copy(country = SettingsCountry.entries[i].wire) } },
                            role = Role.RadioButton,
                        )
                    }
                SettingsPick.StartTab ->
                    ShifaTray(tr(Res.string.startingScreen), { actions.ui { it.copy(picking = null) } }) {
                        ShifaOptionList(
                            state.startTabs.map { OptionItem(tr(it.label), selected = it.key == s.defaultStartTab) },
                            { i -> actions.change { it.copy(defaultStartTab = state.startTabs[i].key) } },
                            role = Role.RadioButton,
                        )
                    }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = Row(
    Modifier.fillMaxWidth().padding(vertical = ShifaSpacing.Gap.Md),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
    ShifaSwitch(checked, onChange)
}

package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.MeProfile
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.about
import shifa.doctor.generated.resources.addClinicAddress
import shifa.doctor.generated.resources.addName
import shifa.doctor.generated.resources.addPhone
import shifa.doctor.generated.resources.addPhoto
import shifa.doctor.generated.resources.addPracticeTimezone
import shifa.doctor.generated.resources.addProfession
import shifa.doctor.generated.resources.addServices
import shifa.doctor.generated.resources.appVersionLabel
import shifa.doctor.generated.resources.clinicAnalyticsAndExports
import shifa.doctor.generated.resources.contactAndClinicAddress
import shifa.doctor.generated.resources.drMedPrefix
import shifa.doctor.generated.resources.errorRequestId
import shifa.doctor.generated.resources.extendedProfile
import shifa.doctor.generated.resources.extendedProfileSubtitle
import shifa.doctor.generated.resources.ic_award_22
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_card_22
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_globe_22
import shifa.doctor.generated.resources.ic_info_22
import shifa.doctor.generated.resources.ic_logout_22
import shifa.doctor.generated.resources.ic_pin_22
import shifa.doctor.generated.resources.ic_reports_22
import shifa.doctor.generated.resources.ic_sliders_22
import shifa.doctor.generated.resources.ic_user_22
import shifa.doctor.generated.resources.ic_wallet_22
import shifa.doctor.generated.resources.languageAndSecurity
import shifa.doctor.generated.resources.languageSecurityNotifications
import shifa.doctor.generated.resources.legalAndSupport
import shifa.doctor.generated.resources.navReports
import shifa.doctor.generated.resources.notSet
import shifa.doctor.generated.resources.personalInformation
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.profile
import shifa.doctor.generated.resources.profileCompletePct
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.schedule
import shifa.doctor.generated.resources.servicesPricing
import shifa.doctor.generated.resources.servicesPricingSubtitle
import shifa.doctor.generated.resources.settings
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.generated.resources.staffProfileManagedByClinic
import shifa.doctor.generated.resources.subscriptionAndBilling
import shifa.doctor.generated.resources.workingHoursAndLocations
import shifa.doctor.generated.resources.yourName
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.register.displayDate
import shifa.doctor.ui.register.label
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.appVersion
import shifa.doctor.utils.doctorDisplayName
import shifa.doctor.utils.tr

/** The profile's child screens (each its own ticket: PROF-03/04/07/08, SVC, SUB-02, SET, LEGAL). */
enum class ProfileSection { PersonalInfo, Contact, Timezone, Extended, Services, Subscription, Settings, Security, About }

data class ProfileUiState(
    val staff: Boolean = false,
    val me: DoctorMe? = null,
    val staffMe: MeProfile? = null,
    // A failed first read: the mapped message and the request id (17 §1), shown with Retry.
    val error: StringResource? = null,
    val requestId: String? = null,
    // The staff "Personal information" row explains instead of opening (DaProfile).
    val staffNote: Boolean = false,
    // English catalogue label → Uzbek label (the bundled catalogue), for the profession line in uz.
    val uzbekLabels: Map<String, String> = emptyMap(),
    // Null until known; the completeness card counts them (flow §4).
    val services: Int? = null,
    val locations: Int? = null,
) {
    val failed: Boolean get() = error != null
    val loading: Boolean get() = !failed && (if (staff) staffMe == null else me == null)
}

/**
 * PROF-02 (flow 08/01, DaProfile root): reads the shared profile (refreshing it when the shell hasn't yet); staff read
 * `/me/profile` instead, since `/doctors/me` answers 403 for them.
 */
class ProfileViewModel(
    private val staff: Boolean = false,
    private val profile: ProfileRepository = profileRepository,
    private val catalog: CatalogRepository = CatalogRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState(staff = staff))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        if (!staff) {
            // A profile arriving from anywhere (a resume refresh after a failure) also clears the error.
            viewModelScope.launch {
                profile.me.collect { me ->
                    _state.update {
                        if (me != null) {
                            it.copy(
                                me = me,
                                error = null,
                                requestId = null,
                            )
                        } else {
                            it.copy(me = null)
                        }
                    }
                }
            }
            viewModelScope.launch {
                val labels = catalog.bundledProfessions().mapNotNull { p -> p.uzbek?.let { p.english to it } }.toMap()
                _state.update { it.copy(uzbekLabels = labels) }
            }
            viewModelScope.launch {
                val services = profile.servicesCount().getOrNull()
                val locations = profile.locationsCount().getOrNull()
                _state.update { it.copy(services = services, locations = locations) }
            }
        }
        load()
    }

    fun retry() = load()

    fun showStaffNote(show: Boolean) = _state.update { it.copy(staffNote = show) }

    private fun load() {
        _state.update { it.copy(error = null, requestId = null) }
        viewModelScope.launch {
            val failure =
                if (staff) {
                    profile.meProfile().onSuccess { me -> _state.update { it.copy(staffMe = me) } }.exceptionOrNull()
                } else if (profile.me.value == null) {
                    profile.refresh().exceptionOrNull()
                } else {
                    null
                }
            failure?.let { e ->
                _state.update {
                    it.copy(
                        error = e.toUserMessage(Res.string.somethingWentWrong),
                        requestId = e.requestId,
                    )
                }
            }
        }
    }
}

class ProfileActions(
    val open: (ProfileSection) -> Unit = {},
    val schedule: () -> Unit = {},
    val reports: () -> Unit = {},
    val signOut: () -> Unit = {},
    val retry: () -> Unit = {},
    val staffNote: (Boolean) -> Unit = {},
)

@Composable
fun ProfileScreen(
    staff: Boolean,
    actions: ProfileActions,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel(staff) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreenContent(
        state,
        ProfileActions(
            actions.open,
            actions.schedule,
            actions.reports,
            actions.signOut,
            retry = viewModel::retry,
            staffNote = viewModel::showStaffNote,
        ),
    )
}

/** The Profile root: title, identity card, completeness, the section list ending in Sign Out, the version. */
@Composable
fun ProfileScreenContent(
    state: ProfileUiState,
    actions: ProfileActions,
) = Column(
    Modifier
        .fillMaxSize()
        .background(ShifaColors.Bg.Canvas)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(
            start = ShifaSpacing.Inset.Md,
            end = ShifaSpacing.Inset.Md,
            top = ShifaSpacing.Inset.Md,
            bottom = ShifaSize.S120,
        ),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
) {
    AuthTitle(tr(Res.string.profile), align = TextAlign.Start) // DaProfile's bold page title
    when {
        state.loading -> ShifaListSkeleton(count = 4)
        state.failed ->
            // Sign Out stays reachable with a broken profile (flow §6).
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Sm)) {
                EmptyState(
                    tr(state.error ?: Res.string.somethingWentWrong),
                    Res.drawable.ic_info_22,
                    body = state.requestId?.let { tr(Res.string.errorRequestId, it) },
                    action = {
                        ShifaActionButton(
                            actions.retry,
                            text = tr(Res.string.retry),
                            variant = ShifaActionVariant.Secondary,
                        )
                    },
                )
                Rows(listOf(signOutRow(actions)))
            }
        state.staff -> StaffProfile(state, actions)
        else -> DoctorProfile(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorProfile(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val me = state.me!!
    val p = me.profile
    val name = doctorDisplayName(p.firstName, p.lastName, tr(Res.string.drMedPrefix))
    val professions = p.profession?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val professionLabel = professions.map { Profession(it, state.uzbekLabels[it]).label() }.joinToString(", ")
    val gate = FeatureGate.of(me.subscription)
    IdentityCard(
        p.photoUrl,
        name.ifEmpty {
            tr(Res.string.yourName)
        },
        professionLabel,
        p.timeZone,
        gate.tier?.name?.uppercase(),
    ) {
        actions.open(ProfileSection.PersonalInfo)
    }
    // Flow §4's eight checks, 12.5 % each: names, profession, timezone, phone, photo, a location, a service. Counts
    // not known yet don't count as missing.
    val address =
        listOfNotNull(p.locationStreetAddress, p.locationCity).filter { it.isNotBlank() }.joinToString(", ")
            .ifEmpty { p.address.orEmpty() }
    val checks =
        listOf(
            Triple(!p.firstName.isNullOrBlank(), Res.string.addName, ProfileSection.PersonalInfo),
            Triple(!p.lastName.isNullOrBlank(), Res.string.addName, ProfileSection.PersonalInfo),
            Triple(professions.isNotEmpty(), Res.string.addProfession, ProfileSection.PersonalInfo),
            Triple(!p.timeZone.isNullOrBlank(), Res.string.addPracticeTimezone, ProfileSection.Timezone),
            Triple(!me.contact.phone.isNullOrBlank(), Res.string.addPhone, ProfileSection.Contact),
            Triple(!p.photoUrl.isNullOrBlank(), Res.string.addPhoto, ProfileSection.PersonalInfo),
            Triple(
                address.isNotBlank() || (state.locations ?: 1) > 0,
                Res.string.addClinicAddress,
                ProfileSection.Contact,
            ),
            Triple((state.services ?: 1) > 0, Res.string.addServices, ProfileSection.Services),
        )
    val done = checks.count { it.first }
    if (done < checks.size) {
        Completeness(
            done * 100 / checks.size,
            checks.filter { !it.first }.map { it.second to it.third }.distinct(),
            actions,
        )
    }
    val notSet = tr(Res.string.notSet)
    val dob = p.dob?.let { runCatching { displayDate(LocalDate.parse(it)) }.getOrNull() }
    val personal = listOfNotNull(name.ifBlank { null }, dob, professionLabel.ifBlank { null }).joinToString(" · ")
    val contact =
        listOfNotNull(me.contact.phone, me.contact.email, address.ifBlank { null }).filter { it.isNotBlank() }
            .joinToString(" · ")
    val zone = p.timeZone?.let { z -> TimeZones.utcLabel(z)?.let { "$z ($it)" } ?: z }
    Rows(
        listOf(
            Row(
                Res.string.personalInformation,
                personal.ifEmpty {
                    notSet
                },
                Res.drawable.ic_user_22,
            ) { actions.open(ProfileSection.PersonalInfo) },
            Row(
                Res.string.contactAndClinicAddress,
                contact.ifEmpty {
                    notSet
                },
                Res.drawable.ic_pin_22,
            ) { actions.open(ProfileSection.Contact) },
            Row(Res.string.practiceTimezone, zone ?: notSet, Res.drawable.ic_globe_22, warn = zone == null) {
                actions.open(ProfileSection.Timezone)
            },
            Row(Res.string.extendedProfile, tr(Res.string.extendedProfileSubtitle), Res.drawable.ic_award_22) {
                actions.open(ProfileSection.Extended)
            },
            Row(Res.string.servicesPricing, tr(Res.string.servicesPricingSubtitle), Res.drawable.ic_wallet_22) {
                actions.open(ProfileSection.Services)
            },
            Row(
                Res.string.schedule,
                tr(Res.string.workingHoursAndLocations),
                Res.drawable.ic_calendar_22,
                onClick = actions.schedule,
            ),
            Row(Res.string.subscriptionAndBilling, gate.tier?.name?.uppercase() ?: notSet, Res.drawable.ic_card_22) {
                actions.open(ProfileSection.Subscription)
            },
            Row(
                Res.string.navReports,
                tr(Res.string.clinicAnalyticsAndExports),
                Res.drawable.ic_reports_22,
                onClick = actions.reports,
            ),
            Row(Res.string.settings, tr(Res.string.languageSecurityNotifications), Res.drawable.ic_sliders_22) {
                actions.open(ProfileSection.Settings)
            },
            Row(
                Res.string.about,
                tr(Res.string.legalAndSupport),
                Res.drawable.ic_info_22,
            ) { actions.open(ProfileSection.About) },
            signOutRow(actions),
        ),
    )
    Version { actions.open(ProfileSection.About) }
}

// Flow 08/01 §5 staff branch: read-only identity, Settings, About, Sign Out; personal data is the clinic owner's.
@Composable
private fun StaffProfile(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val me = state.staffMe!!
    val name = doctorDisplayName(me.firstName, me.lastName, null).ifEmpty { tr(Res.string.yourName) }
    IdentityCard(
        me.photoUrl,
        name,
        listOfNotNull(me.email, me.phone).joinToString(" · "),
        me.timeZone,
        null,
        onClick = null,
    )
    if (state.staffNote) {
        ShifaBanner(
            tr(Res.string.staffProfileManagedByClinic),
            Res.drawable.ic_info_22,
            ShifaBannerType.Warning,
            onClose = { actions.staffNote(false) },
        )
    }
    Rows(
        listOf(
            Row(Res.string.personalInformation, name, Res.drawable.ic_user_22) { actions.staffNote(true) },
            Row(Res.string.settings, tr(Res.string.languageAndSecurity), Res.drawable.ic_sliders_22) {
                actions.open(ProfileSection.Settings)
            },
            Row(
                Res.string.about,
                tr(Res.string.legalAndSupport),
                Res.drawable.ic_info_22,
            ) { actions.open(ProfileSection.About) },
            signOutRow(actions),
        ),
    )
    Version { actions.open(ProfileSection.About) }
}

@Composable
private fun IdentityCard(
    photo: String?,
    name: String,
    sub: String,
    zone: String?,
    tier: String?,
    onClick: (() -> Unit)?,
) = Row(
    Modifier
        .fillMaxWidth()
        .clip(ShifaRadius.ControlLg)
        .background(ShifaColors.Bg.Surface)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(ShifaSize.S16),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S14),
    verticalAlignment = Alignment.CenterVertically,
) {
    ShifaAvatar(photo, ShifaSize.S72)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = ShifaColors.Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub.isNotBlank()) Text(sub, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            zone?.let { ProfileChip(it, ShifaColors.Bg.Canvas, ShifaColors.Text.Primary) }
            // The plan badge only once known (D-03).
            tier?.let { ProfileChip(it, ShifaColors.Text.Primary, ShifaColors.Text.Inverse) }
        }
    }
}

@Composable
internal fun ProfileChip(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color,
) = Text(
    text,
    Modifier.background(background, ShifaRadius.Full).padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
    style = ShifaTextStyles.metaSmall(),
    color = color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Completeness(
    percent: Int,
    missing: List<Pair<StringResource, ProfileSection>>,
    actions: ProfileActions,
) = Column(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .border(ShifaSize.S1, ShifaColors.Alpha.Teal25, ShifaRadius.ControlLg)
        .padding(ShifaSize.S14),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Text(
        tr(Res.string.profileCompletePct, percent),
        style = ShifaTextStyles.panelTitle(),
        color = ShifaColors.Text.Primary,
    )
    Box(Modifier.fillMaxWidth().height(ShifaSize.S6).background(ShifaColors.Bg.Disabled, ShifaRadius.Full)) {
        Box(
            Modifier.fillMaxWidth(
                percent / 100f,
            ).height(ShifaSize.S6).background(ShifaColors.Interactive.Primary, ShifaRadius.Full),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        missing.take(3).forEach { (label, section) ->
            ShifaActionButton({ actions.open(section) }, text = tr(label), variant = ShifaActionVariant.Secondary)
        }
    }
}

private class Row(
    val title: StringResource,
    val preview: String?,
    val icon: DrawableResource,
    val warn: Boolean = false,
    val danger: Boolean = false,
    val chevron: Boolean = true,
    val onClick: () -> Unit,
)

private fun signOutRow(actions: ProfileActions) =
    Row(Res.string.signOut, null, Res.drawable.ic_logout_22, danger = true, chevron = false, onClick = actions.signOut)

@Composable
private fun Rows(rows: List<Row>) =
    Column(Modifier.fillMaxWidth().clip(ShifaRadius.ControlLg).background(ShifaColors.Bg.Surface)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) {
                HorizontalDivider(
                    Modifier.padding(start = ShifaSize.S60),
                    ShifaSize.S1,
                    ShifaColors.Border.Default,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = r.onClick)
                    .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ShifaSize.S36)
                        .background(
                            if (r.danger) ShifaColors.Bg.ErrorSubtle else ShifaColors.Bg.Canvas,
                            ShifaRadius.ControlMd,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(r.icon),
                        null,
                        tint = if (r.danger) ShifaColors.Icon.Error else ShifaColors.Icon.Primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        tr(r.title),
                        style = ShifaTextStyles.panelTitle(),
                        color = if (r.danger) ShifaColors.Text.Error else ShifaColors.Text.Primary,
                    )
                    r.preview?.let {
                        Text(
                            it,
                            style = ShifaTextStyles.metaSmall(),
                            color = if (r.warn) ShifaColors.Text.Warning else ShifaColors.Text.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (r.chevron) Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Secondary)
            }
        }
    }

@Composable
private fun Version(onClick: () -> Unit) =
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ShifaLink(tr(Res.string.appVersionLabel, appVersion), onClick, style = ShifaTextStyles.metaSmall())
    }

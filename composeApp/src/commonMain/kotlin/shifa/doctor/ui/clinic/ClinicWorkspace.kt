package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.ClinicsState
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.ClinicOverviewStats
import shifa.doctor.data.model.MembershipRole
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.role
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.address
import shifa.doctor.generated.resources.adminClinicNameLabel
import shifa.doctor.generated.resources.adminClinicTimezoneLabel
import shifa.doctor.generated.resources.clinic
import shifa.doctor.generated.resources.clinicDoctorRevenueShareNotSet
import shifa.doctor.generated.resources.clinicDoctorRevenueSharePreview
import shifa.doctor.generated.resources.clinicFinanceDefaultRevenueShare
import shifa.doctor.generated.resources.clinicGroupPeople
import shifa.doctor.generated.resources.clinicLoadFailed
import shifa.doctor.generated.resources.clinicMembershipRoleClinicAdmin
import shifa.doctor.generated.resources.clinicMembershipRoleDoctor
import shifa.doctor.generated.resources.clinicMembershipRoleNurse
import shifa.doctor.generated.resources.clinicMembershipRoleOwner
import shifa.doctor.generated.resources.clinicMembershipRoleReceptionist
import shifa.doctor.generated.resources.clinicMetricActiveDoctors
import shifa.doctor.generated.resources.clinicMetricAppointmentsToday
import shifa.doctor.generated.resources.clinicMetricPatientsThisMonth
import shifa.doctor.generated.resources.clinicOpenCalendarTab
import shifa.doctor.generated.resources.clinicSettingsReadOnly
import shifa.doctor.generated.resources.clinicSettingsTitle
import shifa.doctor.generated.resources.clinicStatsFailed
import shifa.doctor.generated.resources.clinicTreatmentPlansNew
import shifa.doctor.generated.resources.clinicWorkspaceCalendar
import shifa.doctor.generated.resources.clinicWorkspaceDoctors
import shifa.doctor.generated.resources.clinicWorkspaceFinance
import shifa.doctor.generated.resources.clinicWorkspaceInvitations
import shifa.doctor.generated.resources.clinicWorkspaceNoClinics
import shifa.doctor.generated.resources.clinicWorkspaceOverview
import shifa.doctor.generated.resources.clinicWorkspacePatients
import shifa.doctor.generated.resources.clinicWorkspacePrimaryPractice
import shifa.doctor.generated.resources.clinicWorkspaceQuickActions
import shifa.doctor.generated.resources.clinicWorkspaceServices
import shifa.doctor.generated.resources.clinicWorkspaceSettings
import shifa.doctor.generated.resources.clinicWorkspaceTreatmentPlans
import shifa.doctor.generated.resources.clinicWorkspaceYourRole
import shifa.doctor.generated.resources.clinicYourClinics
import shifa.doctor.generated.resources.contactSection
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.ic_calendar_16
import shifa.doctor.generated.resources.ic_calendar_20
import shifa.doctor.generated.resources.ic_clinic_22
import shifa.doctor.generated.resources.ic_gear_20
import shifa.doctor.generated.resources.ic_layers_16
import shifa.doctor.generated.resources.ic_mail_16
import shifa.doctor.generated.resources.ic_phone_16
import shifa.doctor.generated.resources.ic_pin_16
import shifa.doctor.generated.resources.ic_users_16
import shifa.doctor.generated.resources.ic_users_20
import shifa.doctor.generated.resources.phone
import shifa.doctor.generated.resources.plans
import shifa.doctor.generated.resources.retry
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ContextSwitcher
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.KpiCard
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.ui.widgets.UnderlineTabs
import shifa.doctor.utils.tr

/** The workspace's sections, as the DaClinic board groups them (09 §2; Flutter had nine flat tabs). */
enum class ClinicSection { Overview, Calendar, Doctors, Invitations, Patients, Plans, Finance, Services, Settings }

/** The board's underline groups and their pill sub-tabs; Settings sits behind the gear. Staff don't invite. */
internal fun clinicGroups(staff: Boolean): List<Pair<StringResource, List<ClinicSection>>> =
    listOf(
        Res.string.clinicWorkspaceOverview to listOf(ClinicSection.Overview, ClinicSection.Calendar),
        Res.string.clinicGroupPeople to
            if (staff) listOf(ClinicSection.Doctors) else listOf(ClinicSection.Doctors, ClinicSection.Invitations),
        Res.string.clinicWorkspacePatients to listOf(ClinicSection.Patients),
        Res.string.plans to listOf(ClinicSection.Plans),
        Res.string.clinicWorkspaceFinance to listOf(ClinicSection.Finance, ClinicSection.Services),
    )

data class ClinicUiState(
    val clinics: ClinicsState = ClinicsState(),
    val loading: Boolean = false,
    val error: CodeBanner? = null,
    val section: ClinicSection = ClinicSection.Overview,
    val stats: ClinicOverviewStats? = null,
    val statsError: CodeBanner? = null,
    val switcher: Boolean = false,
) {
    val clinic: MyClinic? get() = clinics.selected
}

/**
 * The clinic workspace (CLINIC-02/04, 09 §1–2, flows 09/01 and 09/20, board `DaClinic`): the user's clinics from
 * [ClinicsRepository] (the switcher remembers the choice), the section shown, and the Overview's numbers. Every
 * section reads its own data for the selected clinic.
 */
class ClinicWorkspaceViewModel(
    private val repo: ClinicsRepository = clinicsRepository,
) : ViewModel() {
    private val _state =
        MutableStateFlow(ClinicUiState(clinics = if (repo.isCurrent) repo.state.value else ClinicsState()))
    val state: StateFlow<ClinicUiState> = _state.asStateFlow()

    private var statsJob: Job? = null

    // The clinic [ClinicUiState.stats] belong to: another clinic's numbers never show under this one's name.
    private var statsClinic: Long? = null

    init {
        viewModelScope.launch {
            repo.state.collect { clinics ->
                if (!repo.isCurrent) return@collect
                val changed = clinics.selected?.clinicId != _state.value.clinic?.clinicId
                _state.update { it.copy(clinics = clinics) }
                if (changed) readStats()
            }
        }
    }

    /** The clinic list (on open and on coming back, 09 §1.6); the Overview's numbers with it. */
    fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = repo.mine()
            _state.update {
                it.copy(
                    loading = false,
                    // A failure over a list already shown keeps it (sticky); with nothing shown it's the error state.
                    error =
                        result.exceptionOrNull()?.takeIf { _ -> !it.clinics.known }?.let { e ->
                            CodeBanner(e.toUserMessage(Res.string.clinicLoadFailed), requestId = e.requestId)
                        },
                )
            }
            readStats()
        }
    }

    fun section(section: ClinicSection) = _state.update { it.copy(section = section) }

    fun switcher(open: Boolean) = _state.update { it.copy(switcher = open) }

    /** The clinic switcher: every section reloads for the chosen clinic (it's remembered for this user). */
    fun select(clinicId: Long) {
        repo.select(clinicId)
        _state.update { it.copy(switcher = false) }
    }

    fun readStats() {
        val clinic = _state.value.clinic ?: return
        statsJob?.cancel()
        if (statsClinic != clinic.clinicId) _state.update { it.copy(stats = null) }
        statsClinic = clinic.clinicId
        _state.update { it.copy(statsError = null) }
        statsJob =
            viewModelScope.launch {
                repo.overviewStats(clinic.clinicId).fold(
                    { stats -> _state.update { it.copy(stats = stats) } },
                    { e ->
                        _state.update {
                            it.copy(
                                stats = null,
                                statsError = CodeBanner(Res.string.clinicStatsFailed, requestId = e.requestId),
                            )
                        }
                    },
                )
            }
    }
}

/** What the workspace can do outside itself. */
class ClinicActions(
    // Overview's "My calendar" (doctors only): the shell's Calendar tab, the doctor's own calendar.
    val openMyCalendar: () -> Unit = {},
    // Overview's "New plan" (board `DaClinic`, TP-04): the plan wizard for this clinic.
    val newPlan: ((Long) -> Unit)? = null,
)

@Composable
fun ClinicWorkspaceScreen(
    staff: Boolean,
    actions: ClinicActions = ClinicActions(),
    viewModel: ClinicWorkspaceViewModel = viewModel { ClinicWorkspaceViewModel() },
    // The sections built by the other M11 tickets (doctors, patients, plans, finance…), by section.
    section: @Composable ColumnScope.(ClinicSection, MyClinic) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    ClinicWorkspaceContent(
        state,
        staff,
        viewModel::section,
        viewModel::switcher,
        viewModel::select,
        viewModel::load,
        viewModel::readStats,
        actions,
        section,
    )
}

@Composable
internal fun ClinicWorkspaceContent(
    state: ClinicUiState,
    staff: Boolean,
    onSection: (ClinicSection) -> Unit,
    onSwitcher: (Boolean) -> Unit,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onRetryStats: () -> Unit,
    actions: ClinicActions,
    section: @Composable ColumnScope.(ClinicSection, MyClinic) -> Unit,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding()) {
    val clinic = state.clinic
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S10),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(tr(Res.string.clinic), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        when {
            clinic != null -> Header(clinic, state, staff, onSection, onSwitcher)
            state.error != null ->
                codeBanner(state.error)?.let {
                    it()
                    ShifaLink(tr(Res.string.retry), onRetry)
                }
            !state.clinics.known -> ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S30))
        }
    }
    when {
        clinic != null ->
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ShifaSpacing.Inset.Md)
                    .padding(top = ShifaSize.S4, bottom = ShifaSpacing.Inset.Md + ShifaDimens.FabDockedClear),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            ) {
                when (state.section) {
                    ClinicSection.Overview -> Overview(clinic, state, staff, onSection, onRetryStats, actions)
                    ClinicSection.Settings -> Settings(clinic)
                    else -> section(state.section, clinic)
                }
            }
        state.clinics.known ->
            EmptyState(tr(Res.string.clinicWorkspaceNoClinics), Res.drawable.ic_clinic_22, Modifier.weight(1f))
    }
    if (state.switcher) {
        ProfileSheet({ onSwitcher(false) }) {
            ShifaTray(tr(Res.string.clinicYourClinics), { onSwitcher(false) }) {
                val clinics = state.clinics.clinics
                // Two clinics of one name are told apart by their address (flow 09/01 §8).
                ShifaOptionList(
                    clinics.map {
                        OptionItem(
                            listOfNotNull(it.name, it.address).joinToString(" · "),
                            selected = it.clinicId == clinic?.clinicId,
                        )
                    },
                    { i -> onSelect(clinics[i].clinicId) },
                    role = Role.RadioButton,
                )
            }
        }
    }
}

/**
 * The board's header: the clinic (`.dr-context`: name, "time zone · address", the role) — a switcher with 2+ clinics —
 * and the gear for Settings, then the underline groups and, for a group of two, its pill sub-tabs.
 */
@Composable
private fun Header(
    clinic: MyClinic,
    state: ClinicUiState,
    staff: Boolean,
    onSection: (ClinicSection) -> Unit,
    onSwitcher: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        val primary =
            if (clinic.isPracticeClinic && !staff) {
                " (${tr(
                    Res.string.clinicWorkspacePrimaryPractice,
                )})"
            } else {
                ""
            }
        ContextSwitcher(
            clinic.name,
            listOfNotNull(clinic.timeZone ?: UTC, clinic.address?.takeIf { it.isNotBlank() }).joinToString(" · "),
            Modifier.weight(1f),
            role = "${tr(Res.string.clinicWorkspaceYourRole)}: ${roleLabel(clinic.role)}$primary",
            onClick = if (state.clinics.clinics.size > 1) ({ onSwitcher(true) }) else null,
        )
        ShifaIconButton(
            Res.drawable.ic_gear_20,
            tr(Res.string.clinicSettingsTitle),
            {
                onSection(
                    if (state.section == ClinicSection.Settings) ClinicSection.Overview else ClinicSection.Settings,
                )
            },
            variant = ShifaIconButtonVariant.Surface,
            selected = state.section == ClinicSection.Settings,
        )
    }
    val groups = clinicGroups(staff)
    val group = groups.indexOfFirst { state.section in it.second }
    UnderlineTabs(groups.map { TabItem(tr(it.first)) }, group, { onSection(groups[it].second.first()) })
    groups.getOrNull(group)?.second?.takeIf { it.size > 1 }?.let { subs ->
        SegmentedTabs(subs.map { TabItem(tr(sectionLabel(it))) }, subs.indexOf(state.section), { onSection(subs[it]) })
    }
}

/**
 * Overview (flow 09/01 §3, board `DaClinic`): the clinic's numbers (today's appointments, active doctors, this month's
 * patients; a tap opens the matching section), quick actions, and the contact rows that dial, mail or open maps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Overview(
    clinic: MyClinic,
    state: ClinicUiState,
    staff: Boolean,
    onSection: (ClinicSection) -> Unit,
    onRetryStats: () -> Unit,
    actions: ClinicActions,
) {
    val stats = state.stats
    when {
        stats != null ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                maxItemsInEachRow = 2,
            ) {
                val cell = Modifier.weight(1f)
                KpiCard(
                    tr(Res.string.clinicMetricAppointmentsToday),
                    stats.appointmentsToday.toString(),
                    Res.drawable.ic_calendar_20,
                    cell,
                    onClick = { onSection(ClinicSection.Calendar) },
                )
                KpiCard(
                    tr(Res.string.clinicMetricPatientsThisMonth),
                    stats.patientsThisMonth.toString(),
                    Res.drawable.ic_users_20,
                    cell,
                    onClick = { onSection(ClinicSection.Patients) },
                )
                KpiCard(
                    tr(Res.string.clinicMetricActiveDoctors),
                    stats.activeDoctors.toString(),
                    Res.drawable.ic_users_20,
                    cell,
                    onClick = { onSection(ClinicSection.Doctors) },
                )
            }
        state.statsError != null ->
            // The rest of the Overview stands (flow 09/01 §6 "partial data").
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                Text(
                    tr(state.statsError.text),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Error,
                )
                ShifaLink(tr(Res.string.retry), onRetryStats)
            }
        else ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                repeat(2) { ShifaSkeleton(Modifier.weight(1f).padding(vertical = ShifaSize.S36)) }
            }
    }
    Card(tr(Res.string.clinicWorkspaceQuickActions)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            // "My calendar" leaves for the doctor's own calendar; staff have none (flow 09/01 §6, a dead button before).
            if (!staff) {
                ShifaSecondaryButton(
                    tr(Res.string.clinicOpenCalendarTab),
                    actions.openMyCalendar,
                    icon = Res.drawable.ic_calendar_16,
                )
            }
            ShifaSecondaryButton(tr(Res.string.clinicWorkspaceCalendar), {
                onSection(ClinicSection.Calendar)
            }, icon = Res.drawable.ic_calendar_16)
            ShifaSecondaryButton(tr(Res.string.clinicWorkspacePatients), {
                onSection(ClinicSection.Patients)
            }, icon = Res.drawable.ic_users_16)
            actions.newPlan?.let { go ->
                ShifaSecondaryButton(
                    tr(Res.string.clinicTreatmentPlansNew),
                    { go(clinic.clinicId) },
                    icon = Res.drawable.ic_layers_16,
                )
            }
        }
    }
    val uri = LocalUriHandler.current
    val contact =
        listOfNotNull(
            clinic.phone?.takeIf { it.isNotBlank() }?.let {
                Triple(
                    Res.drawable.ic_phone_16,
                    it,
                    "tel:${it.filter {
                            c ->
                        c.isDigit() || c == '+'
                    }}",
                )
            },
            clinic.email?.takeIf { it.isNotBlank() }?.let { Triple(Res.drawable.ic_mail_16, it, "mailto:$it") },
            clinic.address?.takeIf { it.isNotBlank() }?.let { Triple(Res.drawable.ic_pin_16, it, null) },
        )
    if (contact.isNotEmpty()) {
        Card(tr(Res.string.contactSection)) {
            contact.forEach {
                    (icon, text, link) ->
                ContactRow(icon, text) { link?.let { runCatching { uri.openUri(it) } } }
            }
        }
    }
}

/**
 * Settings (flow 09/20, board `DaClinic`): read-only — the clinic's details and its default doctor revenue share
 * (edited on the Doctors tab). The board's Contact admin, Primary practice switch and Leave clinic have no backend
 * (BE-24) and aren't shown.
 */
@Composable
private fun ColumnScope.Settings(clinic: MyClinic) {
    Box(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.InfoSubtle, ShifaRadius.ControlMd)
            .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
    ) {
        Text(
            tr(Res.string.clinicSettingsReadOnly),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
    }
    Card(null) {
        listOf(
            tr(Res.string.adminClinicNameLabel) to clinic.name,
            tr(Res.string.adminClinicTimezoneLabel) to (clinic.timeZone ?: UTC),
            tr(Res.string.phone) to clinic.phone,
            tr(Res.string.email) to clinic.email,
            tr(Res.string.address) to clinic.address,
            tr(Res.string.clinicFinanceDefaultRevenueShare) to
                (
                    clinic.defaultDoctorRevenueSharePercent?.let {
                        tr(Res.string.clinicDoctorRevenueSharePreview, it.toString(), (100 - it).toString())
                    } ?: tr(Res.string.clinicDoctorRevenueShareNotSet)
                ),
        ).filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                Text(label, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                Text(value.orEmpty(), style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
            }
        }
    }
}

/** A white section of the workspace with an optional heading (the board's `section` blocks). */
@Composable
internal fun Card(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    title?.let {
        Text(
            it,
            Modifier.semantics { heading() },
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
        )
    }
    content()
}

@Composable
private fun ContactRow(
    icon: DrawableResource,
    text: String,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().clickable(
        role = Role.Button,
        onClick = onClick,
    ).padding(vertical = ShifaSize.S8, horizontal = ShifaSize.S6),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Icon(painterResource(icon), null, tint = ShifaColors.Icon.Secondary)
    Text(text, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Primary)
}

@Composable
internal fun roleLabel(role: MembershipRole): String =
    when (role) {
        MembershipRole.Owner -> tr(Res.string.clinicMembershipRoleOwner)
        MembershipRole.ClinicAdmin -> tr(Res.string.clinicMembershipRoleClinicAdmin)
        MembershipRole.Receptionist -> tr(Res.string.clinicMembershipRoleReceptionist)
        MembershipRole.Doctor -> tr(Res.string.clinicMembershipRoleDoctor)
        MembershipRole.Nurse -> tr(Res.string.clinicMembershipRoleNurse)
        MembershipRole.Unknown -> "—"
    }

internal fun sectionLabel(section: ClinicSection): StringResource =
    when (section) {
        ClinicSection.Overview -> Res.string.clinicWorkspaceOverview
        ClinicSection.Calendar -> Res.string.clinicWorkspaceCalendar
        ClinicSection.Doctors -> Res.string.clinicWorkspaceDoctors
        ClinicSection.Invitations -> Res.string.clinicWorkspaceInvitations
        ClinicSection.Patients -> Res.string.clinicWorkspacePatients
        ClinicSection.Plans -> Res.string.clinicWorkspaceTreatmentPlans
        ClinicSection.Finance -> Res.string.clinicWorkspaceFinance
        ClinicSection.Services -> Res.string.clinicWorkspaceServices
        ClinicSection.Settings -> Res.string.clinicWorkspaceSettings
    }

// The backend's default when a clinic has no time zone (flow 09/01 §4).
private const val UTC = "UTC"

package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientStatus
import shifa.doctor.data.patients.FavoritesRepository
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.favoritesRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allPatients
import shifa.doctor.generated.resources.clearFilters
import shifa.doctor.generated.resources.failedToLoadPatients
import shifa.doctor.generated.resources.favorites
import shifa.doctor.generated.resources.female
import shifa.doctor.generated.resources.filterAllStatuses
import shifa.doctor.generated.resources.followUps
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_sliders_22
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.male
import shifa.doctor.generated.resources.newPatient
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.noPatientsFound
import shifa.doctor.generated.resources.noPatientsYet
import shifa.doctor.generated.resources.noPatientsYetBody
import shifa.doctor.generated.resources.other
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientStatusActive
import shifa.doctor.generated.resources.patientStatusAtRisk
import shifa.doctor.generated.resources.patientStatusFollowUp
import shifa.doctor.generated.resources.patients
import shifa.doctor.generated.resources.patientsCount
import shifa.doctor.generated.resources.patientsFilterTitle
import shifa.doctor.generated.resources.patientsPagination
import shifa.doctor.generated.resources.previous
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchPatientsDirectoryHint
import shifa.doctor.generated.resources.sortNameAsc
import shifa.doctor.generated.resources.sortNameDesc
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural
import kotlin.time.Duration.Companion.milliseconds

/** The directory's segments (flow 06/01 §7); Flutter's "Recent" (last 20 by name, not recency) is dropped (PAT-03). */
enum class PatientTab { All, Favorites, FollowUps }

data class PatientsUiState(
    val patients: List<Patient>? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    // What is typed, and what the list filters by (300 ms after the last keystroke).
    val typed: String = "",
    val query: String = "",
    val tab: PatientTab = PatientTab.All,
    val status: PatientStatus? = null,
    val descending: Boolean = false,
    val page: Int = 0,
    val favorites: Set<Long> = emptySet(),
    val choosingStatus: Boolean = false,
    // The practice date, for ages (flow 06/01 §8: not the phone's date).
    val today: LocalDate,
) {
    val filtered: Boolean get() = query.isNotBlank() || tab != PatientTab.All || status != null

    /** Search (name, id, display id, phone digits), segment, status, then name order. */
    val visible: List<Patient>
        get() =
            patients.orEmpty().asSequence()
                .filter { it.matches(query) }
                .filter {
                    when (tab) {
                        PatientTab.All -> true
                        PatientTab.Favorites -> it.id in favorites
                        PatientTab.FollowUps -> it.status == PatientStatus.FollowUp
                    }
                }
                // An unknown status counts as Active (flow 06/01 §4).
                .filter { status == null || shown(it.status) == status }
                .sortedWith(compareBy<Patient> { it.name.lowercase() }.let { if (descending) it.reversed() else it })
                .toList()

    val pages: Int get() = maxOf(1, (visible.size + PAGE - 1) / PAGE)

    /** The page shown, kept in range when the list shrinks (a refresh, a removed favourite). */
    val currentPage: Int get() = page.coerceIn(0, pages - 1)

    val shown: List<Patient> get() = visible.drop(currentPage * PAGE).take(PAGE)
}

private fun shown(s: PatientStatus) = if (s == PatientStatus.Unknown) PatientStatus.Active else s

private const val PAGE = 10

/**
 * PAT-03 (flow 06/01, spec 06 §1.2–1.5): the doctor's patients from the shared directory, read when stale (60 s) rather
 * than on every visit, pulled to refresh; search over name, id, display id and phone digits (300 ms after typing),
 * All / Favorites / Follow-ups, a status filter, name order either way, ten per page. Favourites are kept per doctor on
 * the phone. Ages use the practice date.
 */
class PatientsViewModel(
    private val repo: PatientsRepository = patientsRepository,
    private val favorites: FavoritesRepository = favoritesRepository,
    // The signed-in doctor (id, practice zone), possibly arriving after the screen opened: their favourites and the
    // practice date for ages follow when it does.
    doctors: Flow<Pair<Long?, String?>> = profileRepository.me.map { it?.profile?.id to it?.profile?.timeZone },
    private val clockOf: (String?) -> PracticeClock = { PracticeClock(it) },
) : ViewModel() {
    private val _state =
        MutableStateFlow(PatientsUiState(today = clockOf(profileRepository.me.value?.profile?.timeZone).today()))
    val state: StateFlow<PatientsUiState> = _state.asStateFlow()
    private var debounce: Job? = null

    init {
        viewModelScope.launch { repo.patients.collect { list -> _state.update { it.copy(patients = list) } } }
        viewModelScope.launch {
            doctors.distinctUntilChanged().collect { (id, zone) ->
                favorites.load(id)
                _state.update { it.copy(today = clockOf(zone).today()) }
            }
        }
        viewModelScope.launch { favorites.ids.collect { ids -> _state.update { it.copy(favorites = ids) } } }
    }

    /** On open (stale only) and on pull / Retry ([force]). */
    fun load(force: Boolean = false) {
        _state.update {
            it.copy(
                loading = it.patients == null,
                refreshing = force && it.patients != null,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = if (force) repo.refresh() else repo.refreshIfStale()
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error =
                        result.exceptionOrNull()?.let {
                                e ->
                            CodeBanner(e.toUserMessage(Res.string.failedToLoadPatients), requestId = e.requestId)
                        },
                )
            }
        }
    }

    fun type(text: String) {
        _state.update { it.copy(typed = text) }
        debounce?.cancel()
        debounce =
            viewModelScope.launch {
                delay(DEBOUNCE)
                _state.update { it.copy(query = text, page = 0) }
            }
    }

    fun ui(change: (PatientsUiState) -> PatientsUiState) = _state.update(change)

    /** A change of what is shown (segment, status, order) starts again on page 1 (Flutter kept the page on sort). */
    fun filter(change: (PatientsUiState) -> PatientsUiState) = _state.update { change(it).copy(page = 0) }

    fun page(to: Int) = _state.update { it.copy(page = to.coerceIn(0, it.pages - 1)) }

    fun clearFilters() =
        _state.update {
            it.copy(typed = "", query = "", tab = PatientTab.All, status = null, page = 0)
        }.also { debounce?.cancel() }

    /** Star or unstar a patient (the detail does the same through the shared [FavoritesRepository]). */
    fun toggleFavorite(id: Long) = favorites.toggle(id)

    private companion object {
        val DEBOUNCE = 300.milliseconds
    }
}

class PatientsActions(
    val load: (Boolean) -> Unit = {},
    val type: (String) -> Unit = {},
    val ui: ((PatientsUiState) -> PatientsUiState) -> Unit = {},
    val filter: ((PatientsUiState) -> PatientsUiState) -> Unit = {},
    val page: (Int) -> Unit = {},
    val clearFilters: () -> Unit = {},
    val open: (Patient) -> Unit = {},
    // Null until the create sheet exists (PAT-04): no dead button.
    val create: (() -> Unit)? = null,
)

@Composable
fun PatientsScreen(
    onOpen: (Patient) -> Unit,
    viewModel: PatientsViewModel = viewModel { PatientsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The create sheet's open stamp (a fresh form each time), null when closed (PAT-04).
    var creating by rememberSaveable { mutableStateOf<Long?>(null) }
    creating?.let { opened ->
        CreatePatientSheet(
            opened,
            onCreated = { patient ->
                creating = null
                onOpen(patient) // the new patient opens, as the flow asks
            },
            onClose = { creating = null },
        )
    }
    LaunchedEffect(Unit) { viewModel.load() }
    PatientsContent(
        state,
        PatientsActions(
            viewModel::load,
            viewModel::type,
            viewModel::ui,
            viewModel::filter,
            viewModel::page,
            viewModel::clearFilters,
            onOpen,
            { creating = currentInstant().toEpochMilliseconds() },
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientsContent(
    state: PatientsUiState,
    actions: PatientsActions,
) {
    // A new page starts at the top (flow 06/01 §7; Flutter kept the offset).
    val scroll = rememberScrollState()
    LaunchedEffect(state.currentPage) { scroll.scrollTo(0) }
    PullToRefreshBox(
        state.refreshing,
        { actions.load(true) },
        Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll)
                // Clear of the floating tab bar, like the other tab roots.
                .padding(
                    start = ShifaSpacing.Inset.Md,
                    end = ShifaSpacing.Inset.Md,
                    top = ShifaSpacing.Inset.Md,
                    bottom = ShifaSize.S120,
                ),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
        ) {
            Header(state, actions)
            codeBanner(state.error.takeIf { state.patients != null }) { actions.ui { it.copy(error = null) } }?.invoke()
            Body(state, actions)
        }
    }
    if (state.choosingStatus) StatusSheet(state, actions)
}

@Composable
private fun Header(
    state: PatientsUiState,
    actions: PatientsActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    Text(tr(Res.string.patients), style = ShifaTextStyles.trayTitle(), color = ShifaColors.Text.Primary)
    state.patients?.let {
        ProfileChip(
            trPlural(Res.plurals.patientsCount, it.size, it.size),
            ShifaColors.Bg.Surface,
            ShifaColors.Text.Secondary,
        )
    }
    ShifaSearchField(state.typed, actions.type, tr(Res.string.searchPatientsDirectoryHint), Modifier.fillMaxWidth())
    Row(
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaIconButton(
            Res.drawable.ic_sliders_22,
            tr(Res.string.patientsFilterTitle),
            { actions.ui { it.copy(choosingStatus = true) } },
            variant = if (state.status != null) ShifaIconButtonVariant.Primary else ShifaIconButtonVariant.Secondary,
        )
        actions.create?.let { ShifaPrimaryButton(tr(Res.string.newPatient), it, Modifier.weight(1f)) }
    }
    // Wraps on a narrow phone, in Russian or with large text.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        listOf(
            PatientTab.All to Res.string.allPatients,
            PatientTab.Favorites to Res.string.favorites,
            PatientTab.FollowUps to Res.string.followUps,
        )
            .forEach {
                    (tab, label) ->
                ShifaChip(tr(label), state.tab == tab, { actions.filter { it.copy(tab = tab) } })
            }
    }
    // Name order, either way (Flutter's "Recent activity" sorted by document dates only; dropped).
    ShifaChip(
        tr(if (state.descending) Res.string.sortNameDesc else Res.string.sortNameAsc),
        false,
        { actions.filter { it.copy(descending = !it.descending) } },
    )
}

@Composable
private fun Body(
    state: PatientsUiState,
    actions: PatientsActions,
) {
    val list = state.patients
    val error = state.error
    when {
        list == null && error != null ->
            EmptyState(tr(error.text), Res.drawable.ic_users_22, body = error.requestId) {
                ShifaSecondaryButton(tr(Res.string.retry), { actions.load(true) })
            }
        list == null -> ShifaListSkeleton(count = 6)
        list.isEmpty() ->
            EmptyState(
                tr(Res.string.noPatientsYet),
                Res.drawable.ic_users_22,
                body = tr(Res.string.noPatientsYetBody),
            ) {
                actions.create?.let { ShifaPrimaryButton(tr(Res.string.newPatient), it) }
            }
        state.visible.isEmpty() ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr(Res.string.noPatientsFound),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
                if (state.filtered) ShifaLink(tr(Res.string.clearFilters), actions.clearFilters)
            }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                state.shown.forEach { PatientRow(it, state.today) { actions.open(it) } }
            }
            Pager(state, actions)
        }
    }
}

/** Avatar, name (or "Patient"), "34, Male · +998…" (parts left out when unknown), the status pill, chevron. */
@Composable
internal fun PatientRow(
    p: Patient,
    today: LocalDate,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(ShifaSpacing.Inset.Sm),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    ShifaAvatar(p.photoUrl, ShifaSize.S40)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            p.name.takeIf { it.isNotBlank() } ?: tr(Res.string.patient),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val who = listOfNotNull(p.age(today)?.toString(), genderLabel(p.gender)).joinToString(", ")
        val line = listOf(who, p.allPhones.firstOrNull().orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(
                line,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    StatusPill(p.status)
    androidx.compose.material3.Icon(
        org.jetbrains.compose.resources.painterResource(Res.drawable.ic_chev_r_16),
        null,
        tint = ShifaColors.Icon.Secondary,
    )
}

/** Teal / amber / violet-ish (the info tint) per status; an unknown status reads as Active (flow 06/01 §4). */
@Composable
internal fun StatusPill(status: PatientStatus) =
    when (status) {
        PatientStatus.AtRisk ->
            ProfileChip(
                tr(Res.string.patientStatusAtRisk),
                ShifaColors.Bg.WarningSubtle,
                ShifaColors.Text.Warning,
            )
        PatientStatus.FollowUp ->
            ProfileChip(
                tr(Res.string.patientStatusFollowUp),
                ShifaColors.Bg.InfoSubtle,
                ShifaColors.Text.Info,
            )
        else -> ProfileChip(tr(Res.string.patientStatusActive), ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Primary)
    }

/** "Male" / "Female" / "Other", the raw value when it is something else, nothing when empty. */
@Composable
internal fun genderLabel(raw: String?): String? =
    when (raw?.trim()?.lowercase()) {
        null, "" -> null
        "male", "m" -> tr(Res.string.male)
        "female", "f" -> tr(Res.string.female)
        "other" -> tr(Res.string.other)
        else -> raw
    }

/** "Showing 11 to 20 of 47 patients", ‹ ›, up to five page numbers around the current one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pager(
    state: PatientsUiState,
    actions: PatientsActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    val total = state.visible.size
    val page = state.currentPage
    val start = page * PAGE + 1
    val end = minOf(total, start + PAGE - 1)
    Text(
        trPlural(Res.plurals.patientsPagination, total, start, end, total),
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
    )
    if (state.pages > 1) {
        // Wraps on a narrow phone or large text; Previous / Next only where they lead somewhere (no dead controls).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            if (page > 0) ShifaChip(tr(Res.string.previous), false, { actions.page(page - 1) })
            val first = (page - 2).coerceIn(0, maxOf(0, state.pages - 5))
            (first until minOf(state.pages, first + 5)).forEach {
                    i ->
                ShifaChip("${i + 1}", i == page, { actions.page(i) })
            }
            if (page < state.pages - 1) ShifaChip(tr(Res.string.next), false, { actions.page(page + 1) })
        }
    }
}

@Composable
private fun StatusSheet(
    state: PatientsUiState,
    actions: PatientsActions,
) = ProfileSheet({ actions.ui { it.copy(choosingStatus = false) } }) {
    ShifaTray(tr(Res.string.patientsFilterTitle), { actions.ui { it.copy(choosingStatus = false) } }) {
        val options =
            listOf(
                null to Res.string.filterAllStatuses,
                PatientStatus.Active to Res.string.patientStatusActive,
                PatientStatus.AtRisk to Res.string.patientStatusAtRisk,
                PatientStatus.FollowUp to Res.string.patientStatusFollowUp,
            )
        ShifaOptionList(
            options.map { (status, label) -> OptionItem(tr(label), selected = state.status == status) },
            { i -> actions.filter { it.copy(status = options[i].first, choosingStatus = false) } },
            role = Role.RadioButton,
        )
    }
}

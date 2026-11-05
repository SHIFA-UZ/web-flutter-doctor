package shifa.doctor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.Patient
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addPatient
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.failedToLoadPatients
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_search_24
import shifa.doctor.generated.resources.noPatientsFound
import shifa.doctor.generated.resources.noPatientsFoundHint
import shifa.doctor.generated.resources.noPatientsYet
import shifa.doctor.generated.resources.patients
import shifa.doctor.generated.resources.patientsCount
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.searchPatientsByNameOrPhone
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.patients.PatientRow
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSearchField
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

// With nothing typed, the first patients by name (flow 02/07 §4: Flutter showed 8 in server order).
private const val FIRST = 8

data class PatientSearchUiState(
    val patients: List<Patient>? = null,
    val loading: Boolean = false,
    val error: CodeBanner? = null,
    val query: String = "",
    // The practice date, for ages.
    val today: LocalDate,
) {
    /** Every match by name (name, id, display id, phone digits: the Patients tab's search); the first 8 when blank. */
    val hits: List<Patient>
        get() {
            val sorted = patients.orEmpty().sortedBy { it.name.lowercase() }
            return if (query.isBlank()) sorted.take(FIRST) else sorted.filter { it.matches(query) }
        }
}

/**
 * HOME-05 (flow 02/07, spec 02 §1.9, board `DaHome` search page): the doctor's patients from the shared directory (read
 * when stale, as the Patients tab does), filtered as typed. No debounce: the list is local.
 */
class PatientSearchViewModel(
    private val repo: PatientsRepository = patientsRepository,
    today: LocalDate = PracticeClock(profileRepository.me.value?.profile?.timeZone).today(),
) : ViewModel() {
    private val _state = MutableStateFlow(PatientSearchUiState(today = today))
    val state: StateFlow<PatientSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.patients.collect { list -> _state.update { it.copy(patients = list) } } }
        load()
    }

    /** On open, and Retry. */
    fun load() {
        _state.update { it.copy(loading = it.patients == null, error = null) }
        viewModelScope.launch {
            val e = repo.refreshIfStale().exceptionOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    error =
                        e?.let {
                            CodeBanner(
                                e.toUserMessage(Res.string.failedToLoadPatients),
                                requestId = e.requestId,
                            )
                        },
                )
            }
        }
    }

    fun type(text: String) = _state.update { it.copy(query = text) }
}

@Composable
fun PatientSearchScreen(
    opened: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAddPatient: () -> Unit,
    // One per open: a new search starts empty and checks the directory again (flow 02/07 §2).
    viewModel: PatientSearchViewModel = viewModel(key = "patient-search-$opened") { PatientSearchViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PatientSearchContent(state, viewModel::type, viewModel::load, onBack, onOpen, onAddPatient, autofocus = true)
}

/** The search page: Back, the field (keyboard up on entry), then the list, its count, or why there is none. */
@Composable
internal fun PatientSearchContent(
    state: PatientSearchUiState,
    onType: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAddPatient: () -> Unit = {},
    autofocus: Boolean = false,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).statusBarsPadding().imePadding()) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autofocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hits = state.hits
    Row(
        Modifier.fillMaxWidth().background(
            ShifaColors.Bg.Surface,
        ).padding(horizontal = ShifaSize.S16, vertical = ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        ShifaIconButton(
            Res.drawable.ic_chev_l_16,
            tr(Res.string.back),
            onBack,
            variant = ShifaIconButtonVariant.Secondary,
        )
        ShifaSearchField(
            state.query,
            onType,
            tr(Res.string.searchPatientsByNameOrPhone),
            Modifier.weight(1f).focusRequester(focus),
            // The keyboard's Search opens the only match, else just puts the keyboard away (flow 02/07 §7).
            onSearch = { hits.singleOrNull()?.let { onOpen(it.id) } ?: keyboard?.hide() },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ShifaSize.S16),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        state.error?.let { e ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                    codeBanner(e) {}?.invoke()
                    ShifaLink(tr(Res.string.retry), onRetry)
                }
            }
        }
        when {
            state.loading -> item { ShifaListSkeleton() }
            state.patients == null -> Unit
            // No patients at all (flow 02/07 §6): say so, with Add patient (the Patients tab, where patients are made).
            state.patients.isEmpty() -> item { Empty(Res.string.noPatientsYet, null, onAddPatient) }
            hits.isEmpty() -> item { Empty(Res.string.noPatientsFound, Res.string.noPatientsFoundHint, null) }
            else -> {
                item {
                    // Read out as the count changes ("12 patients").
                    Text(
                        if (state.query.isBlank()) {
                            tr(
                                Res.string.patients,
                            )
                        } else {
                            trPlural(Res.plurals.patientsCount, hits.size, hits.size)
                        },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = ShifaTextStyles.legend(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
                items(hits, key = { it.id }) { p -> PatientRow(p, state.today) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun Empty(
    title: StringResource,
    hint: StringResource?,
    onAdd: (() -> Unit)?,
) = Column(
    Modifier.fillMaxWidth().padding(horizontal = ShifaSize.S16, vertical = ShifaSize.S40),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Box(Modifier.size(ShifaSize.S56).background(ShifaColors.Bg.Surface, ShifaRadius.Full), Alignment.Center) {
        Icon(painterResource(Res.drawable.ic_search_24), null, tint = ShifaColors.Text.Secondary)
    }
    Text(tr(title), style = ShifaTextStyles.contextName(), color = ShifaColors.Text.Primary)
    hint?.let {
        Text(
            tr(it),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
            textAlign = TextAlign.Center,
        )
    }
    onAdd?.let { ShifaPrimaryButton(tr(Res.string.addPatient), it) }
}

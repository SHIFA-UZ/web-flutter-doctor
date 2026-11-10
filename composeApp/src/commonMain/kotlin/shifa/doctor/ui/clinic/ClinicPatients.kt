package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.ClinicPatient
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.ClinicEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicFinanceLoadMore
import shifa.doctor.generated.resources.clinicPatientsEmpty
import shifa.doctor.generated.resources.clinicPatientsSearchHint
import shifa.doctor.generated.resources.clinicPatientsTotal
import shifa.doctor.generated.resources.failedToLoadPatients
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.patient
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.tr
import kotlin.time.Duration.Companion.milliseconds

data class ClinicPatientsUiState(
    val typed: String = "",
    // Null until the first page of the current search has come back.
    val rows: List<ClinicPatient>? = null,
    val total: Long = 0,
    val page: Int = 0,
    val last: Boolean = true,
    val loadingMore: Boolean = false,
    val error: CodeBanner? = null,
)

/**
 * The clinic's patients (CLINIC-06, 09 §2 row 3, board `DaClinic` Patients): pages of 50 searched on the server (`q`:
 * name, phone or email; Flutter filtered one page on the device), "Load more" for the next page. An answer for an
 * older search is dropped. [clinicId] is fixed: switching clinics makes a new ViewModel.
 */
class ClinicPatientsViewModel(
    private val clinicId: Long,
    private val repo: ClinicsRepository = clinicsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicPatientsUiState())
    val state: StateFlow<ClinicPatientsUiState> = _state.asStateFlow()

    // The search the rows belong to, and a counter so a late page of an older search (or a reload) is ignored.
    private var query = ""
    private var generation = 0
    private var debounce: Job? = null
    private var loading: Job? = null

    init {
        load()
    }

    /** The first page of the current search (also Retry). */
    fun load() {
        val gen = ++generation
        loading?.cancel()
        _state.update { it.copy(rows = null, error = null, loadingMore = false) }
        loading =
            viewModelScope.launch {
                repo.patients(clinicId, 0, query).fold(
                    { p ->
                        if (gen == generation) {
                            _state.update { it.copy(rows = p.rows, total = p.total, page = 0, last = p.last) }
                            // A search's answer (never its text): how much it found.
                            if (query.isNotEmpty()) {
                                tracker.track(ClinicEvents.PatientsSearched(countBucket(p.total.toInt())))
                            }
                        }
                    },
                    { e -> if (gen == generation) _state.update { it.copy(error = failure(e)) } },
                )
            }
    }

    /**
     * Back in front (a patient was added or edited meanwhile): the first page again, the rows kept until it answers.
     * Not while the first read is still out, and not once more pages were added below (they would be dropped).
     */
    fun refresh() {
        val s = _state.value
        if (s.rows == null || s.page != 0 || s.loadingMore) return
        val gen = ++generation
        loading?.cancel()
        loading =
            viewModelScope.launch {
                repo.patients(clinicId, 0, query).onSuccess { p ->
                    if (gen == generation) _state.update { it.copy(rows = p.rows, total = p.total, last = p.last) }
                }
            }
    }

    /** The search field: the server is asked once typing pauses, and only when the trimmed text changed. */
    fun type(text: String) {
        _state.update { it.copy(typed = text) }
        debounce?.cancel()
        debounce =
            viewModelScope.launch {
                delay(DEBOUNCE)
                if (text.trim() != query) {
                    query = text.trim()
                    load()
                }
            }
    }

    /** The next page, added below (a patient already shown, moved by a new sign-up, isn't listed twice). */
    fun loadMore() {
        val s = _state.value
        val rows = s.rows ?: return
        if (s.last || s.loadingMore) return
        val gen = generation
        // A resume's refresh still out would put page 0 back over the rows this adds to: it gives way.
        loading?.cancel()
        _state.update { it.copy(loadingMore = true, error = null) }
        loading =
            viewModelScope.launch {
                repo.patients(clinicId, s.page + 1, query).fold(
                    { p ->
                        if (gen == generation) {
                            _state.update {
                                it.copy(
                                    rows = (rows + p.rows).distinctBy { r -> r.patientId },
                                    total = p.total,
                                    page = s.page + 1,
                                    last = p.last,
                                    loadingMore = false,
                                )
                            }
                        }
                    },
                    { e ->
                        if (gen == generation) _state.update { it.copy(loadingMore = false, error = failure(e)) }
                    },
                )
            }
    }

    private fun failure(e: Throwable) =
        CodeBanner(e.toUserMessage(Res.string.failedToLoadPatients), requestId = e.requestId)

    companion object {
        val DEBOUNCE = 350.milliseconds
    }
}

/** What the patients list can do (the section wires them to [ClinicPatientsViewModel]; previews leave them empty). */
class ClinicPatientsActions(
    val type: (String) -> Unit = {},
    val load: () -> Unit = {},
    val loadMore: () -> Unit = {},
    val open: (ClinicPatient) -> Unit = {},
)

@Composable
internal fun ColumnScope.ClinicPatientsSection(
    clinic: MyClinic,
    onOpen: (ClinicPatient) -> Unit,
    viewModel: ClinicPatientsViewModel =
        viewModel(key = "clinic-patients-${clinic.clinicId}") { ClinicPatientsViewModel(clinic.clinicId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ClinicPatientsContent(state, ClinicPatientsActions(viewModel::type, viewModel::load, viewModel::loadMore, onOpen))
}

/** Search, "Total: N", one card per patient (avatar, name, "phone · email"), Load more (board `DaClinic` Patients). */
@Composable
internal fun ColumnScope.ClinicPatientsContent(
    state: ClinicPatientsUiState,
    actions: ClinicPatientsActions,
) {
    ShifaInputField(state.typed, actions.type, tr(Res.string.clinicPatientsSearchHint), Modifier.fillMaxWidth())
    val rows = state.rows
    when {
        rows == null && state.error != null ->
            ShifaLoadError(tr(state.error.text), actions.load, requestId = state.error.requestId)
        rows == null ->
            repeat(SKELETON_ROWS) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S24)) }
        // Empty only when no page follows: a page whose rows were all unreadable still offers the next.
        rows.isEmpty() && state.last -> EmptyState(tr(Res.string.clinicPatientsEmpty), Res.drawable.ic_users_22)
        else -> {
            Text(
                tr(Res.string.clinicPatientsTotal, state.total.toString()),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Secondary,
            )
            rows.forEach { p -> PatientCard(p) { actions.open(p) } }
            // A failed next page says so under the rows it kept; Load more tries again.
            state.error?.let { e -> codeBanner(e)?.let { it() } }
            if (!state.last) {
                ShifaSecondaryButton(
                    tr(Res.string.clinicFinanceLoadMore),
                    actions.loadMore,
                    Modifier.fillMaxWidth(),
                    loading = state.loadingMore,
                )
            }
        }
    }
}

@Composable
private fun PatientCard(
    p: ClinicPatient,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    ShifaAvatar(null, ShifaSize.S40)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            p.fullName?.takeIf { it.isNotBlank() } ?: tr(Res.string.patient),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // "+998… · mail@…": a dash when there's no phone, the email only when there is one (the board's line).
        val line =
            listOfNotNull(
                p.phone?.takeIf { it.isNotBlank() }?.let(PhoneE164::display),
                p.email?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "—" }
        Text(
            line,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Secondary)
}

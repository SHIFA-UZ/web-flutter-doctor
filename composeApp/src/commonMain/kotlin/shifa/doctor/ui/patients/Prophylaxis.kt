package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.ProphylaxisSetting
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.PatientsEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.prophylaxisEnabled
import shifa.doctor.generated.resources.prophylaxisIntervalMonths
import shifa.doctor.generated.resources.prophylaxisLastSent
import shifa.doctor.generated.resources.prophylaxisLoadFailed
import shifa.doctor.generated.resources.prophylaxisRemindersTitle
import shifa.doctor.generated.resources.prophylaxisSave
import shifa.doctor.generated.resources.prophylaxisSaveFailed
import shifa.doctor.generated.resources.prophylaxisSaved
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSwitchRow
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

data class ProphylaxisUiState(
    // False until the setting was read; a failed read keeps the card closed ([loadError]) so Save can't overwrite
    // the server's values with defaults (flow 06/05 §6; Flutter fell back to the defaults silently).
    val loaded: Boolean = false,
    val loadError: CodeBanner? = null,
    val interval: Int = DEFAULT_INTERVAL,
    val enabled: Boolean = true,
    val lastSentAt: String? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
) {
    companion object {
        const val DEFAULT_INTERVAL = 12
        val INTERVALS = 1..60
    }
}

/**
 * The clinic's prophylaxis reminder for a patient (CLINIC-06 with PAT-06; flow 06/05 §3.2, spec 06 §2.9):
 * `GET /prophylaxis/settings?patientId&clinicId` (an empty answer is "no setting": 12 months, on) and Save,
 * `PUT /prophylaxis/settings`. Only a patient opened from a clinic's workspace has one.
 */
class ProphylaxisViewModel(
    private val patientId: Long,
    private val clinicId: Long,
    private val repo: PatientsRepository = patientsRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(ProphylaxisUiState())
    val state: StateFlow<ProphylaxisUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            repo.prophylaxis(patientId, clinicId).fold(
                { s -> _state.update { it.from(s).copy(loaded = true) } },
                { e -> _state.update { it.copy(loadError = failure(e, Res.string.prophylaxisLoadFailed)) } },
            )
        }
    }

    fun interval(months: Int) =
        _state.update { if (months in ProphylaxisUiState.INTERVALS) it.copy(interval = months, banner = null) else it }

    fun enabled(on: Boolean) = _state.update { it.copy(enabled = on, banner = null) }

    fun save() {
        val s = _state.value
        if (!s.loaded || s.saving) return
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            repo.saveProphylaxis(ProphylaxisSetting(patientId, clinicId, s.interval, s.enabled)).fold(
                { saved ->
                    tracker.track(PatientsEvents.ProphylaxisSaved(s.enabled, s.interval))
                    _state.update {
                        // "Last sent" stays as it was when the answer leaves it out (flow 06/05 §7).
                        it.from(saved.copy(lastSentAt = saved.lastSentAt ?: it.lastSentAt))
                            .copy(saving = false, banner = CodeBanner(Res.string.prophylaxisSaved, error = false))
                    }
                },
                { e ->
                    tracker.track(PatientsEvents.ProphylaxisSaveFailed(e.toFailureReason()))
                    _state.update { it.copy(saving = false, banner = failure(e, Res.string.prophylaxisSaveFailed)) }
                },
            )
        }
    }

    fun dismiss() = _state.update { it.copy(banner = null) }

    private fun ProphylaxisUiState.from(s: ProphylaxisSetting?) =
        copy(
            interval =
                s?.intervalMonths?.takeIf { it in ProphylaxisUiState.INTERVALS } ?: ProphylaxisUiState.DEFAULT_INTERVAL,
            enabled = s?.enabled ?: true,
            lastSentAt = s?.lastSentAt,
        )

    private fun failure(
        e: Throwable,
        fallback: org.jetbrains.compose.resources.StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)
}

class ProphylaxisActions(
    val load: () -> Unit = {},
    val interval: (Int) -> Unit = {},
    val enabled: (Boolean) -> Unit = {},
    val save: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

@Composable
internal fun ProphylaxisCard(
    patientId: Long,
    clinicId: Long,
    viewModel: ProphylaxisViewModel =
        viewModel(key = "prophylaxis-$patientId-$clinicId") { ProphylaxisViewModel(patientId, clinicId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "Last sent" in the clinic's time zone (the doctor's own when the clinic isn't known).
    val zone = clinicsRepository.state.value.clinics.firstOrNull { it.clinicId == clinicId }?.timeZone
    ProphylaxisContent(
        state,
        ProphylaxisActions(
            viewModel::load,
            viewModel::interval,
            viewModel::enabled,
            viewModel::save,
            viewModel::dismiss,
        ),
        PracticeClock(zone),
    )
}

/** Not in the design (the board has no clinic-scope card): the Medical info card's style, flow 06/05 §3.2. */
@Composable
internal fun ProphylaxisContent(
    state: ProphylaxisUiState,
    actions: ProphylaxisActions,
    clock: PracticeClock,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    Text(
        tr(Res.string.prophylaxisRemindersTitle),
        style = ShifaTextStyles.panelTitle(),
        color = ShifaColors.Text.Primary,
    )
    codeBanner(state.banner, actions.dismiss)?.let { it() }
    when {
        !state.loaded && state.loadError != null ->
            ShifaLoadError(
                tr(state.loadError.text),
                actions.load,
                requestId = state.loadError.requestId,
                compact = true,
            )
        !state.loaded -> repeat(2) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S16)) }
        else -> {
            val enabled = !state.saving
            val months = ProphylaxisUiState.INTERVALS.toList()
            Choice(
                tr(Res.string.prophylaxisIntervalMonths),
                months.map { it.toString() },
                months.indexOf(state.interval),
                enabled,
            ) { i -> actions.interval(months[i]) }
            ShifaSwitchRow(tr(Res.string.prophylaxisEnabled), state.enabled, { if (enabled) actions.enabled(it) })
            state.lastSentAt?.takeIf { it.isNotBlank() }?.let { raw ->
                // A date in the clinic's zone; something unreadable as sent.
                val shown = PracticeClock.parseInstant(raw)?.let { displayDate(clock.local(it).date) } ?: raw
                Text(
                    tr(Res.string.prophylaxisLastSent, shown),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            ShifaPrimaryButton(
                tr(Res.string.prophylaxisSave),
                actions.save,
                enabled = enabled,
                loading = state.saving,
                size = ShifaButtonSize.Compact,
            )
        }
    }
}

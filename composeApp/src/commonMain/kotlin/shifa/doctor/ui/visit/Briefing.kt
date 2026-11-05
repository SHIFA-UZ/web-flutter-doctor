package shifa.doctor.ui.visit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.BRIEFING_SLOW_AFTER
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.model.AppointmentBriefing
import shifa.doctor.data.model.BriefingStatus
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attachedDocuments
import shifa.doctor.generated.resources.copied
import shifa.doctor.generated.resources.retryBriefing
import shifa.doctor.generated.resources.visitBriefingCopy
import shifa.doctor.generated.resources.visitBriefingDisclaimer
import shifa.doctor.generated.resources.visitBriefingEmpty
import shifa.doctor.generated.resources.visitBriefingFailed
import shifa.doctor.generated.resources.visitBriefingMeta
import shifa.doctor.generated.resources.visitBriefingPending
import shifa.doctor.generated.resources.visitBriefingStillGenerating
import shifa.doctor.generated.resources.visitBriefingSubtitle
import shifa.doctor.generated.resources.visitBriefingTitle
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.AiResponseText
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

data class BriefingUiState(
    val briefing: AppointmentBriefing? = null,
    // The briefing couldn't be read or made again (flow 05/02 §9).
    val failed: Boolean = false,
    val retrying: Boolean = false,
    // Being made for over a minute: "Still generating" (flow 05/02 §8).
    val slow: Boolean = false,
    // The documents the patient attached to this visit (their originals, one tap away).
    val documents: List<PatientDocument> = emptyList(),
    val copied: Boolean = false,
) {
    val status: BriefingStatus? get() = if (failed) BriefingStatus.Failed else briefing?.state
}

/**
 * APPT-06 (flow 05/02, spec 05 §7; board `DaVisit` "briefing"): the visit briefing, read every 3 s while it is being
 * made (10 s after a minute), and Retry. The reads stop when the sheet closes (the ViewModel goes with it).
 */
class BriefingViewModel(
    private val appointmentId: Long,
    private val patientId: Long?,
    private val appointments: AppointmentsRepository = appointmentsRepository,
    private val documents: DocumentsRepository = documentsRepository,
    private val now: () -> Instant = ::currentInstant,
) : ViewModel() {
    private val _state = MutableStateFlow(BriefingUiState())
    val state: StateFlow<BriefingUiState> = _state.asStateFlow()
    private var reading: Job? = null

    fun start() {
        follow()
        patientId ?: return
        viewModelScope.launch {
            documents.list(patientId).getOrNull()?.let { all ->
                _state.update { s -> s.copy(documents = all.filter { it.appointmentId == appointmentId }) }
            }
        }
    }

    private fun follow() {
        reading?.cancel()
        val started = now()
        reading =
            viewModelScope.launch {
                appointments.briefingUpdates(appointmentId).collect { r ->
                    _state.update {
                        it.copy(
                            briefing = r.getOrNull() ?: it.briefing,
                            failed = r.isFailure,
                            slow = now() - started >= BRIEFING_SLOW_AFTER,
                        )
                    }
                }
            }
    }

    /** The sheet closed: no more reads, and a Retry on its way won't start them again (the shell keeps this ViewModel). */
    fun stop() {
        reading?.cancel()
        retrying?.cancel()
        _state.update { it.copy(retrying = false) }
    }

    private var retrying: Job? = null

    /** One at a time; then the briefing is followed again. */
    fun retry() {
        if (_state.value.retrying) return
        _state.update { it.copy(retrying = true) }
        retrying =
            viewModelScope.launch {
                val result = appointments.retryBriefing(appointmentId)
                _state.update {
                    it.copy(
                        retrying = false,
                        failed = result.isFailure,
                        briefing = result.getOrNull() ?: it.briefing,
                        slow = false,
                    )
                }
                if (result.isSuccess) follow()
            }
    }

    fun copied() = _state.update { it.copy(copied = true) }
}

/** The sheet over the appointment details; [onOpenDocument] opens an attached original. */
@Composable
fun BriefingSheet(
    appointmentId: Long,
    patientId: Long?,
    completed: Boolean,
    clock: PracticeClock,
    onOpenDocument: (PatientDocument) -> Unit,
    onClose: () -> Unit,
    viewModel: BriefingViewModel =
        viewModel(key = "briefing-$appointmentId") { BriefingViewModel(appointmentId, patientId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    ProfileSheet(onClose) {
        BriefingTray(state, completed, clock, viewModel::retry, viewModel::copied, onOpenDocument, onClose)
    }
}

@Composable
internal fun BriefingTray(
    state: BriefingUiState,
    completed: Boolean,
    clock: PracticeClock,
    retry: () -> Unit,
    copied: () -> Unit,
    openDocument: (PatientDocument) -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.visitBriefingTitle), close) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            tr(Res.string.visitBriefingSubtitle),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
        val b = state.briefing
        when (state.status) {
            null -> repeat(3) { ShifaSkeleton(Modifier.fillMaxWidth().heightIn(min = ShifaSize.S16)) }
            BriefingStatus.Pending -> {
                Text(
                    tr(Res.string.visitBriefingPending),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
                if (state.slow) {
                    Text(
                        tr(Res.string.visitBriefingStillGenerating),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
            }
            BriefingStatus.Ready ->
                AiResponseText(b?.briefingText.orEmpty(), style = ShifaTextStyles.bodyText())
            BriefingStatus.Failed ->
                Text(
                    b?.error?.takeIf { !state.failed && it.isNotBlank() } ?: tr(Res.string.visitBriefingFailed),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Error,
                )
            BriefingStatus.None ->
                Text(
                    tr(Res.string.visitBriefingEmpty),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
        }
        if (b != null && state.status == BriefingStatus.Ready) {
            val time =
                b.generated?.let {
                    val local = clock.local(it)
                    "${displayDate(local.date)} ${displayTime(local.time)}"
                }
            if (time != null) {
                Text(
                    trPlural(Res.plurals.visitBriefingMeta, b.documentCount, b.documentCount, time),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        // The originals show whatever the briefing's state (a skipped briefing still lists them, flow 05/02 §6).
        if (state.documents.isNotEmpty()) {
            Text(
                tr(Res.string.attachedDocuments),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            state.documents.forEach { d ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    ShifaLink(d.title.ifBlank { "—" }, { openDocument(d) })
                    d.day?.let {
                        Text(
                            displayDate(it),
                            style = ShifaTextStyles.caption(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                }
            }
        }
        Text(
            tr(Res.string.visitBriefingDisclaimer),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        val ready = state.status == BriefingStatus.Ready
        if (ready) {
            ShifaSecondaryButton(
                tr(if (state.copied) Res.string.copied else Res.string.visitBriefingCopy),
                {
                    clipboard.setText(AnnotatedString(state.briefing?.briefingText.orEmpty()))
                    copied()
                },
                size = ShifaButtonSize.Compact,
            )
        }
        // Retry for a failed or stuck briefing, not after the visit (flow 05/02 §6).
        if (!completed && (state.status == BriefingStatus.Failed || state.status == BriefingStatus.Pending)) {
            ShifaSecondaryButton(
                tr(Res.string.retryBriefing),
                retry,
                loading = state.retrying,
                enabled = !state.retrying,
                size = ShifaButtonSize.Compact,
            )
        }
    }
}

package shifa.doctor.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiRepository
import shifa.doctor.data.ai.toAiMessage
import shifa.doctor.data.api.requestId
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.PatientBriefing
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.copied
import shifa.doctor.generated.resources.patientBriefingCopy
import shifa.doctor.generated.resources.patientBriefingEmpty
import shifa.doctor.generated.resources.patientBriefingError
import shifa.doctor.generated.resources.patientBriefingGenerating
import shifa.doctor.generated.resources.patientBriefingRegenerate
import shifa.doctor.generated.resources.patientBriefingSources
import shifa.doctor.generated.resources.patientBriefingSourcesAppointmentsOnly
import shifa.doctor.generated.resources.patientBriefingSourcesWithAppointments
import shifa.doctor.generated.resources.patientBriefingTitle
import shifa.doctor.generated.resources.retryBriefing
import shifa.doctor.generated.resources.visitBriefingDisclaimer
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.AiResponseText
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.tr

data class PatientBriefingUiState(
    val loading: Boolean = false,
    // The last briefing this session (kept when a Regenerate fails); null until one arrives.
    val briefing: PatientBriefing? = null,
    // Nothing to brief on: the backend's 400 (no documents, visits, forms or notes) or a blank text.
    val empty: Boolean = false,
    val error: CodeBanner? = null,
    val copied: Boolean = false,
)

/**
 * The AI summary of one patient (AI-09, 02 §6, flow 03/20): made on the first open in the app's language, then kept
 * for the session (the shell keeps this ViewModel per patient), so reopening is instant; Regenerate asks again. A
 * request still running when the sheet closes keeps going and shows on reopen (Flutter dropped it and paid twice).
 */
class PatientBriefingViewModel(
    private val patientId: Long,
    private val repo: AiRepository = aiRepository,
    private val language: () -> AppLanguage = { languageStorage.language.value },
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientBriefingUiState())
    val state: StateFlow<PatientBriefingUiState> = _state.asStateFlow()

    /** The sheet opened: the first time, generate; after that, show what there is. */
    fun open() {
        val s = _state.value
        if (s.briefing == null && !s.empty && s.error == null && !s.loading) generate()
    }

    /** One request at a time (Flutter restarted it on a second tap: two paid calls). */
    fun generate() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null, copied = false) }
        viewModelScope.launch {
            repo.patientBriefing(patientId, language()).fold(
                { b ->
                    val blank = b.briefing.isBlank()
                    _state.update { it.copy(loading = false, briefing = b.takeUnless { blank }, empty = blank) }
                },
                { e ->
                    when (e) {
                        // Nothing to brief on any more: the empty state replaces an older text (it no longer holds).
                        is ApiException.ValidationError ->
                            _state.update { it.copy(loading = false, empty = true, briefing = null) }
                        else -> {
                            // A 403: the plan may have changed; the profile read hides the button if so.
                            if (e is ApiException.Forbidden) launch { profile.refresh() }
                            // The daily limit, the 60 s running out (flow 03/20 §4), the AI service down.
                            val text = e.toAiMessage(Res.string.patientBriefingError)
                            _state.update {
                                it.copy(
                                    loading = false,
                                    error = CodeBanner(text, requestId = e.requestId),
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    fun copied() = _state.update { it.copy(copied = true) }
}

/** The sheet over patient detail (design `DaPatients` "brief" sheet). */
@Composable
fun PatientBriefingSheet(
    patientId: Long,
    patientName: String,
    onClose: () -> Unit,
    viewModel: PatientBriefingViewModel =
        viewModel(key = "patient-briefing-$patientId") { PatientBriefingViewModel(patientId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open() }
    ProfileSheet(onClose) { PatientBriefingTray(state, patientName, viewModel::generate, viewModel::copied, onClose) }
}

@Composable
internal fun PatientBriefingTray(
    state: PatientBriefingUiState,
    patientName: String,
    generate: () -> Unit,
    copied: () -> Unit,
    close: () -> Unit,
) = ShifaTray(tr(Res.string.patientBriefingTitle), close) {
    val clipboard = LocalClipboardManager.current
    val b = state.briefing
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            patientName,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        codeBanner(state.error)?.invoke()
        when {
            state.loading && b == null -> {
                Text(
                    tr(Res.string.patientBriefingGenerating),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
                ShifaSkeleton(Modifier.fillMaxWidth(0.5f).height(ShifaSize.S20))
                repeat(6) { ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.S16)) }
            }
            b != null -> {
                sources(b)?.let { Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary) }
                AiResponseText(b.briefing, style = ShifaTextStyles.bodyText())
                Text(
                    tr(Res.string.visitBriefingDisclaimer),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            state.empty ->
                Text(
                    tr(Res.string.patientBriefingEmpty),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        if (b != null) {
            ShifaSecondaryButton(
                tr(if (state.copied) Res.string.copied else Res.string.patientBriefingCopy),
                {
                    clipboard.setText(AnnotatedString(b.briefing))
                    copied()
                },
                size = ShifaButtonSize.Compact,
            )
        }
        if (b != null || state.error != null || state.empty) {
            ShifaSecondaryButton(
                tr(if (b != null) Res.string.patientBriefingRegenerate else Res.string.retryBriefing),
                generate,
                loading = state.loading,
                enabled = !state.loading,
                size = ShifaButtonSize.Compact,
            )
        }
    }
}

/** "Based on 4 document(s) and 7 appointment(s).", one side alone, or nothing when both are 0. */
@Composable
private fun sources(b: PatientBriefing): String? {
    val docs = b.documentCount.toString()
    val visits = b.appointmentCount.toString()
    return when {
        b.documentCount > 0 && b.appointmentCount > 0 ->
            tr(Res.string.patientBriefingSourcesWithAppointments, docs, visits)
        b.documentCount > 0 -> tr(Res.string.patientBriefingSources, docs)
        b.appointmentCount > 0 -> tr(Res.string.patientBriefingSourcesAppointmentsOnly, visits)
        else -> null
    }
}

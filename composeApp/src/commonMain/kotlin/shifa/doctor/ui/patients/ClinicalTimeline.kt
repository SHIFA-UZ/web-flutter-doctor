package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiRepository
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.VisitFacts
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicalTimeline
import shifa.doctor.generated.resources.clinicalTimelineEmpty
import shifa.doctor.generated.resources.clinicalTimelineHint
import shifa.doctor.generated.resources.ic_activity_18
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.visitFactsLoadFailed
import shifa.doctor.ui.calendar.longDate
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.visit.Patterns
import shifa.doctor.ui.visit.factTypeLabel
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr

data class ClinicalTimelineUiState(
    val timeline: VisitFacts? = null,
    val error: CodeBanner? = null,
)

/** The patient's documented visit facts (AI-10, flow 03/21): read on open and on every return to the chart. */
class ClinicalTimelineViewModel(
    private val patientId: Long,
    private val repo: AiRepository = aiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicalTimelineUiState())
    val state: StateFlow<ClinicalTimelineUiState> = _state.asStateFlow()

    private var reading: Job? = null

    fun load() {
        reading?.cancel()
        reading =
            viewModelScope.launch {
                repo.timeline(patientId).fold(
                    { t -> _state.update { it.copy(timeline = t, error = null) } },
                    { e ->
                        // A failed re-read keeps what is shown; only a first failure shows the error row.
                        val banner =
                            CodeBanner(e.toUserMessage(Res.string.visitFactsLoadFailed), requestId = e.requestId)
                        _state.update { it.copy(error = banner.takeIf { _ -> it.timeline == null }) }
                    },
                )
            }
    }
}

@Composable
fun ClinicalTimeline(
    patientId: Long,
    viewModel: ClinicalTimelineViewModel =
        viewModel(key = "clinical-timeline-$patientId") { ClinicalTimelineViewModel(patientId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    ClinicalTimelineCard(state, PracticeClock(profileRepository.me.value?.profile?.timeZone), viewModel::load)
}

/**
 * DaPatients "Clinical timeline": a dot and a line per fact, "Diagnosis · text", the quote, when it was documented
 * (practice time; Flutter showed the raw ISO string), then the top clinic patterns. Empty and failed reads say so
 * (Flutter hid the card).
 */
@Composable
internal fun ClinicalTimelineCard(
    state: ClinicalTimelineUiState,
    clock: PracticeClock,
    retry: () -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Icon(painterResource(Res.drawable.ic_activity_18), null, tint = ShifaColors.Text.BrandStrong)
        Text(tr(Res.string.clinicalTimeline), style = ShifaTextStyles.contextName(), color = ShifaColors.Text.Primary)
    }
    Text(tr(Res.string.clinicalTimelineHint), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    val timeline = state.timeline
    when {
        timeline == null && state.error != null ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                Text(
                    tr(state.error.text),
                    Modifier.weight(1f, fill = false),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Error,
                )
                ShifaLink(tr(Res.string.retry), retry)
            }
        timeline == null -> repeat(3) { ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.S20)) }
        timeline.facts.isEmpty() ->
            Text(
                tr(Res.string.clinicalTimelineEmpty),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        else -> {
            timeline.facts.forEach { f ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
                    Column(
                        Modifier.width(ShifaSize.S10).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .padding(top = ShifaSize.S4)
                                .size(ShifaSize.S10)
                                .background(ShifaColors.Interactive.Primary, ShifaRadius.Full),
                        )
                        Box(Modifier.width(ShifaSize.S2).weight(1f).background(ShifaColors.Bg.Disabled))
                    }
                    Column(
                        Modifier.weight(1f).padding(bottom = ShifaSize.S8),
                        verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
                    ) {
                        Text(
                            "${factTypeLabel(f.factType)} · ${f.text}",
                            style = ShifaTextStyles.chipSlot(),
                            color = ShifaColors.Text.Primary,
                        )
                        f.quote?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "“$it”",
                                style = ShifaTextStyles.metaSmall().copy(fontStyle = FontStyle.Italic),
                                color = ShifaColors.Text.Secondary,
                            )
                        }
                        documentedAt(f.committedAt ?: f.createdAt, clock)?.let {
                            Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                        }
                    }
                }
            }
            if (timeline.patterns.isNotEmpty()) {
                HorizontalDivider(color = ShifaColors.Border.Default)
                Patterns(timeline.patterns.take(4))
            }
        }
    }
}

/** "21 October 2026 · 10:42" in practice time; nothing for a time the app can't read. */
@Composable
private fun documentedAt(
    iso: String,
    clock: PracticeClock,
): String? =
    runCatching { Instant.parse(iso) }.getOrNull()?.let {
        val local = clock.local(it)
        "${longDate(local.date)} · ${displayTime(local.time)}"
    }

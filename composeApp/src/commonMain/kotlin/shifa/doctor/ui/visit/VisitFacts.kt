package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiRepository
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.ClinicPattern
import shifa.doctor.data.model.CommitVisitFactsRequest
import shifa.doctor.data.model.VisitFact
import shifa.doctor.data.model.VisitFacts
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.documentEverything
import shifa.doctor.generated.resources.documenting
import shifa.doctor.generated.resources.droppedFacts
import shifa.doctor.generated.resources.factTypeDiagnosis
import shifa.doctor.generated.resources.factTypeMedication
import shifa.doctor.generated.resources.factTypeSymptom
import shifa.doctor.generated.resources.factTypeTherapy
import shifa.doctor.generated.resources.factsAlreadyDocumented
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_sparkles_18
import shifa.doctor.generated.resources.intelCardPatterns
import shifa.doctor.generated.resources.intelKeep
import shifa.doctor.generated.resources.intelPatternCount
import shifa.doctor.generated.resources.intelReject
import shifa.doctor.generated.resources.intelScheduleHint
import shifa.doctor.generated.resources.notesSaveFailed
import shifa.doctor.generated.resources.refresh
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.visitFactCards
import shifa.doctor.generated.resources.visitFactCardsHint
import shifa.doctor.generated.resources.visitFactsCommitted
import shifa.doctor.generated.resources.visitFactsCommittedWithTasks
import shifa.doctor.generated.resources.visitFactsDocumentFailed
import shifa.doctor.generated.resources.visitFactsLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural
import kotlin.time.Duration.Companion.seconds

data class VisitFactsUiState(
    // Null until the first answer.
    val facts: VisitFacts? = null,
    // Drafts the doctor turned down; kept across reloads (Flutter lost them on refresh).
    val rejected: Set<Long> = emptySet(),
    val loading: Boolean = false,
    // The read failed and nothing is shown yet.
    val loadError: CodeBanner? = null,
    val documenting: Boolean = false,
    val documentError: CodeBanner? = null,
    // What the last "Document everything" did: facts documented, medication tasks drafted.
    val done: Pair<Int, Int>? = null,
) {
    val drafts: List<VisitFact> get() = facts?.keptDrafts.orEmpty()
    val dropped: List<VisitFact> get() = facts?.dropped.orEmpty()
    val committed: Int get() = facts?.facts.orEmpty().count { it.isCommitted }

    /** Nothing kept, dropped or documented: the panel isn't drawn (there was no scribe). */
    val empty: Boolean get() = facts != null && drafts.isEmpty() && dropped.isEmpty() && committed == 0

    val canDocument: Boolean get() = !documenting && drafts.any { it.id !in rejected }
}

/**
 * The scribe's facts for one visit (AI-10, flow 03/21): read on open and on return, Keep / Reject per draft (local
 * until committed), "Document everything" commits the kept ones and records the rejected ones, then reads again.
 */
class VisitFactsViewModel(
    private val appointmentId: Long,
    private val repo: AiRepository = aiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VisitFactsUiState())
    val state: StateFlow<VisitFactsUiState> = _state.asStateFlow()

    private var reading: Job? = null

    fun load() {
        reading?.cancel()
        _state.update { it.copy(loading = true) }
        reading =
            viewModelScope.launch {
                repo.visitFacts(appointmentId).fold(
                    { f ->
                        _state.update { s ->
                            val drafts = f.keptDrafts.map { it.id }.toSet()
                            s.copy(facts = f, rejected = s.rejected intersect drafts, loading = false, loadError = null)
                        }
                    },
                    { e ->
                        val banner =
                            CodeBanner(e.toUserMessage(Res.string.visitFactsLoadFailed), requestId = e.requestId)
                        _state.update { it.copy(loading = false, loadError = banner.takeIf { _ -> it.facts == null }) }
                    },
                )
            }
    }

    fun toggle(id: Long) =
        _state.update {
                s ->
            s.copy(rejected = if (id in s.rejected) s.rejected - id else s.rejected + id, done = null)
        }

    /**
     * The backend also appends the documented facts to the visit's note (`appendConsultationNote`): the notes are
     * saved first ([flushNotes]; nothing is committed if they can't be), and the same text is handed to [committed] so
     * the open editor holds it too and its next save doesn't drop it.
     */
    fun document(
        flushNotes: suspend () -> Boolean = { true },
        committed: (String) -> Unit = {},
    ) {
        val s = _state.value
        if (!s.canDocument) return
        val accept = s.drafts.map { it.id }.filter { it !in s.rejected }
        _state.update { it.copy(documenting = true, documentError = null, done = null) }
        viewModelScope.launch {
            if (!flushNotes()) {
                val banner = CodeBanner(Res.string.notesSaveFailed)
                return@launch _state.update { it.copy(documenting = false, documentError = banner) }
            }
            repo.commit(CommitVisitFactsRequest(appointmentId, accept, s.rejected.toList())).fold(
                { r ->
                    committed(r.committedFacts.joinToString("\n") { it.text.trim() }.trim())
                    // The answer applied at once (the backend rejects the drafts not accepted), so the drafts can't
                    // be documented twice while the read below runs, or if it fails.
                    val done = r.committedFacts.associateBy { it.id }
                    _state.update { st ->
                        st.copy(
                            facts =
                                st.facts?.let { v ->
                                    v.copy(
                                        facts =
                                            v.facts.map {
                                                    f ->
                                                done[f.id] ?: if (f.isDraft) f.copy(status = "REJECTED") else f
                                            },
                                    )
                                },
                            documenting = false,
                            rejected = emptySet(),
                            done = r.committedFacts.size to r.createdTaskIds.size,
                        )
                    }
                    load()
                },
                { e ->
                    val text = e.toUserMessage(Res.string.visitFactsDocumentFailed)
                    _state.update {
                        it.copy(
                            documenting = false,
                            documentError = CodeBanner(text, requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }
}

class VisitFactsActions(
    val reload: () -> Unit = {},
    val toggle: (Long) -> Unit = {},
    val document: () -> Unit = {},
)

/**
 * The panel in the visit workspace, with its own ViewModel per visit. [scribing]: a recording went to the scribe, so
 * facts may still come; [flushNotes] and [notesAppended] keep the open notes in step with a commit.
 */
@Composable
fun VisitFactsPanel(
    appointmentId: Long,
    scribing: Boolean,
    flushNotes: suspend () -> Boolean,
    notesAppended: (String) -> Unit,
    viewModel: VisitFactsViewModel =
        viewModel(key = "visit-facts-$appointmentId") { VisitFactsViewModel(appointmentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // On open and on every return (the scribe may have finished meanwhile; flow 03/21 §8).
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose {}
    }
    // While the scribe works and nothing has come yet, read again every 10 s (only while this is on screen).
    // ponytail: fixed-interval read; follow the scribe's status instead if 10 s proves too slow or too chatty.
    val waiting = scribing && (state.facts == null || state.empty)
    LaunchedEffect(waiting) {
        while (waiting) {
            delay(SCRIBE_POLL)
            viewModel.load()
        }
    }
    VisitFactsCard(
        state,
        VisitFactsActions(viewModel::load, viewModel::toggle) { viewModel.document(flushNotes, notesAppended) },
    )
}

private val SCRIBE_POLL = 10.seconds

/** DaVisit "Visit facts from the call"; drawn only while there is something to show or a failure to retry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VisitFactsCard(
    state: VisitFactsUiState,
    actions: VisitFactsActions,
) {
    if (state.empty || (state.facts == null && state.loadError == null && !state.loading)) return
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Icon(painterResource(Res.drawable.ic_sparkles_18), null, tint = ShifaColors.Text.BrandStrong)
            Text(
                tr(Res.string.visitFactCards),
                Modifier.weight(1f),
                style = ShifaTextStyles.contextName(),
                color = ShifaColors.Text.Primary,
            )
            if (state.facts != null) {
                ShifaIconButton(
                    Res.drawable.ic_refresh_22,
                    tr(Res.string.refresh),
                    actions.reload,
                    variant = ShifaIconButtonVariant.Ghost,
                    enabled = !state.loading,
                )
            }
        }
        val facts = state.facts
        when {
            facts == null && state.loadError != null ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        tr(state.loadError.text),
                        Modifier.weight(1f, fill = false),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Error,
                    )
                    ShifaLink(tr(Res.string.retry), actions.reload)
                }
            facts == null -> repeat(3) { ShifaSkeleton(Modifier.fillMaxWidth().height(ShifaSize.S48)) }
            else -> {
                Text(
                    tr(Res.string.visitFactCardsHint),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
                state.drafts.forEach { f ->
                    FactRow(
                        f,
                        rejected = f.id in state.rejected,
                        editable = !state.documenting,
                    ) { actions.toggle(f.id) }
                }
                if (state.dropped.isNotEmpty()) {
                    Text(
                        tr(Res.string.droppedFacts),
                        style = ShifaTextStyles.legend(),
                        color = ShifaColors.Text.Warning,
                    )
                    state.dropped.forEach { FactRow(it, rejected = true, editable = false, onToggle = null) }
                }
                Patterns(facts.patterns.take(3))
                if (state.drafts.isEmpty() && state.committed > 0 && state.done == null) {
                    Text(
                        tr(Res.string.factsAlreadyDocumented),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Primary,
                    )
                }
                if (state.drafts.isNotEmpty()) {
                    ShifaPrimaryButton(
                        tr(if (state.documenting) Res.string.documenting else Res.string.documentEverything),
                        actions.document,
                        Modifier.fillMaxWidth(),
                        enabled = state.canDocument,
                        loading = state.documenting,
                    )
                }
                state.documentError?.let {
                    Text(tr(it.text), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Error)
                }
                state.done?.let { (n, tasks) ->
                    Text(
                        if (tasks > 0) {
                            tr(Res.string.visitFactsCommittedWithTasks, n.toString(), tasks.toString())
                        } else {
                            trPlural(Res.plurals.visitFactsCommitted, n, n.toString())
                        },
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.BrandStrong,
                    )
                }
            }
        }
    }
}

/** One fact: the type pill, the text (struck through when rejected), the quote and schedule hint, Keep / Reject. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactRow(
    f: VisitFact,
    rejected: Boolean,
    editable: Boolean,
    onToggle: (() -> Unit)?,
) = Column(
    Modifier
        .fillMaxWidth()
        .alpha(if (rejected) 0.55f else 1f)
        .background(if (rejected) ShifaColors.Bg.Surface else ShifaColors.Bg.Canvas, ShifaRadius.ControlMd)
        .padding(ShifaSize.S10),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        Text(
            factTypeLabel(f.factType),
            Modifier
                .background(ShifaColors.Bg.Surface, ShifaRadius.Full)
                .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
            style = ShifaTextStyles.legend(),
            color = ShifaColors.Text.Secondary,
        )
        Text(
            f.text,
            style = ShifaTextStyles.chipSlot(),
            color = ShifaColors.Text.Primary,
            textDecoration = if (rejected && onToggle != null) TextDecoration.LineThrough else null,
        )
    }
    f.quote?.takeIf { it.isNotBlank() }?.let {
        Text(
            "“$it”",
            style = ShifaTextStyles.metaSmall().copy(fontStyle = FontStyle.Italic),
            color = ShifaColors.Text.Secondary,
        )
    }
    f.scheduleHint?.takeIf { it.isNotBlank() }?.let {
        Text(
            tr(Res.string.intelScheduleHint, it),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    if (onToggle == null) {
        f.dropReason?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Warning)
        }
    } else if (editable) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            ShifaChip(tr(Res.string.intelKeep), !rejected, { if (rejected) onToggle() })
            ShifaChip(tr(Res.string.intelReject), rejected, { if (!rejected) onToggle() })
        }
    }
}

/** "Clinic patterns": "Label · 14×" rows (labels as the backend sends them, flow 03/12). */
@Composable
internal fun Patterns(
    patterns: List<ClinicPattern>,
    // The chat's patterns card has its own title (flow 03/12: one shared row component in all three places).
    caption: Boolean = true,
) {
    if (patterns.isEmpty()) return
    if (caption) {
        Text(
            tr(Res.string.intelCardPatterns),
            style = ShifaTextStyles.legend(),
            color = ShifaColors.Text.Secondary,
        )
    }
    patterns.forEach { p ->
        Text(
            "${p.label.ifBlank { p.key }} · ${tr(Res.string.intelPatternCount, p.count.toString())}",
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
    }
}

/** The fact type in the app's language; a type the app doesn't know shows as sent. */
@Composable
internal fun factTypeLabel(type: String): String = FACT_TYPES[type.uppercase()]?.let { tr(it) } ?: type

private val FACT_TYPES: Map<String, StringResource> =
    mapOf(
        "SYMPTOM" to Res.string.factTypeSymptom,
        "DIAGNOSIS" to Res.string.factTypeDiagnosis,
        "THERAPY" to Res.string.factTypeTherapy,
        "MEDICATION" to Res.string.factTypeMedication,
    )

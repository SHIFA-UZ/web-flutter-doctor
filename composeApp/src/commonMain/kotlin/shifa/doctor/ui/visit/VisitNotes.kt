package shifa.doctor.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.data.consultation.consultationRepository
import shifa.doctor.data.model.ConsultationNote
import shifa.doctor.data.model.DraftNote
import shifa.doctor.data.model.joinedManualNotes
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadEarlierNotes
import shifa.doctor.generated.resources.notesSaveFailed
import shifa.doctor.ui.emailcode.CodeBanner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The SOAP labels in the app's language at save time (they are written into the saved body, 05/05 §4). */
data class SoapLabels(
    val subjective: String = "Subjective",
    val objective: String = "Objective",
    val assessment: String = "Assessment",
    val plan: String = "Plan",
)

/** What the doctor types in General mode: four optional SOAP fields and the free notes. */
data class SoapNotes(
    val subjective: String = "",
    val objective: String = "",
    val assessment: String = "",
    val plan: String = "",
    val free: String = "",
) {
    val blank: Boolean get() = listOf(subjective, objective, assessment, plan, free).all { it.isBlank() }

    /** The saved body: "Label\ntext" per filled SOAP field, then the free notes, blank lines between (Flutter's). */
    fun body(labels: SoapLabels): String =
        (
            listOf(
                labels.subjective to subjective,
                labels.objective to objective,
                labels.assessment to assessment,
                labels.plan to plan,
            ).filter { it.second.isNotBlank() }.map { (label, text) -> "$label\n${text.trim()}" } + free.trim()
        ).filter { it.isNotEmpty() }.joinToString("\n\n")
}

data class VisitNotesState(
    val text: SoapNotes = SoapNotes(),
    // The visit's saved notes and Shifa AI's pending drafts; null until read.
    val notes: List<ConsultationNote>? = null,
    val drafts: List<DraftNote>? = null,
    // Earlier notes couldn't be read (05/05 §6: "Could not load earlier notes · Retry").
    val loadError: CodeBanner? = null,
    // The text changed since it was last saved; a failed autosave says so.
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveError: CodeBanner? = null,
    // Drafts being saved as a note or dropped.
    val busy: Set<String> = emptySet(),
    val banner: CodeBanner? = null,
)

/**
 * A visit's General-mode notes (APPT-03, spec 05 §2.2, flow 05/05), owned by the visit's ViewModel in its [scope].
 * The text is saved [debounce] after the last edit and when the screen leaves ([flush]); Flutter saved only on leave,
 * mode switch or End, so a crash lost the visit's notes. The backend keeps one manual note per visit and updates it,
 * and ignores blank text (so the notes can't be emptied). The doctor's earlier notes fill the free notes once, and
 * only while everything is still empty. Shifa AI's drafts are saved as a note or dropped, then both lists are read
 * again.
 */
class VisitNotes(
    private val appointmentId: Long,
    private val patientId: () -> Long?,
    private val scope: CoroutineScope,
    private val repo: ConsultationRepository = consultationRepository,
    private val debounce: Duration = AUTOSAVE,
) {
    private val _state = MutableStateFlow(VisitNotesState())
    val state: StateFlow<VisitNotesState> = _state.asStateFlow()

    /** Set by the screen from its strings, so the saved body is in the app's language. */
    var labels = SoapLabels()

    private var hydrated = false
    private val autosave = Autosave(scope, debounce) { save() }

    // Each read moves it on; only the newest may write the lists (an older one could bring back a draft just saved).
    private var reads = 0

    fun load() {
        scope.launch { read() }
    }

    /** The notes and drafts read now; returns when they are in [state] (or a newer read took over). */
    suspend fun read() {
        val read = ++reads
        _state.update { it.copy(loadError = null) }
        coroutineScope {
            val drafts = async { repo.drafts(appointmentId) }
            val notes = repo.notes(appointmentId)
            val draftList = drafts.await()
            if (read != reads) return@coroutineScope
            val failure = notes.exceptionOrNull() ?: draftList.exceptionOrNull()
            val manual = notes.getOrNull()?.let(::joinedManualNotes).orEmpty()
            val fill = !hydrated && manual.isNotEmpty() && _state.value.text.blank
            if (notes.isSuccess) hydrated = true
            _state.update { s ->
                s.copy(
                    notes = notes.getOrNull() ?: s.notes,
                    drafts = draftList.getOrNull() ?: s.drafts,
                    text = if (fill) s.text.copy(free = manual) else s.text,
                    loadError =
                        failure?.let {
                            CodeBanner(it.toUserMessage(Res.string.couldNotLoadEarlierNotes), requestId = it.requestId)
                        },
                )
            }
        }
    }

    /** A change by the doctor; saved [debounce] after the last one. */
    fun edit(change: (SoapNotes) -> SoapNotes) {
        val before = _state.value.text
        val after = change(before)
        if (after == before) return
        // An edit before the earlier notes arrived means they must not fill the field over it.
        hydrated = true
        _state.update { it.copy(text = after, dirty = true, saveError = null) }
        autosave.changed()
    }

    /** Text from a helper (Shifa AI, the last 025-2) added to the free notes after a blank line. */
    fun append(text: String) {
        val add = text.trim().ifEmpty { return }
        edit { it.copy(free = listOf(it.free.trimEnd(), add).filter(String::isNotEmpty).joinToString("\n\n")) }
    }

    /**
     * Text the server already added to the visit's note (documented visit facts, AI-10), mirrored here as
     * [append] would add it, but not unsaved: the next save then sends a body that still holds it.
     */
    fun appendSaved(text: String) {
        val add = text.trim().ifEmpty { return }
        hydrated = true
        _state.update { s ->
            s.copy(
                text =
                    s.text.copy(
                        free = listOf(s.text.free.trimEnd(), add).filter(String::isNotEmpty).joinToString("\n\n"),
                    ),
            )
        }
    }

    /**
     * Saves now what's unsaved (leaving the screen, switching mode, ending the visit), after a save already running;
     * true when nothing is left.
     */
    suspend fun flush(): Boolean {
        autosave.flush()
        return !_state.value.dirty
    }

    private suspend fun save() {
        val s = _state.value
        if (!s.dirty) return
        val version = autosave.changes
        val body = s.text.body(labels)
        // Blank text isn't sent (the backend ignores it); nothing is left to save.
        if (body.isBlank()) return _state.update { it.copy(dirty = autosave.changes != version) }
        _state.update { it.copy(saving = true) }
        // A PUT that has left can't be called back: it runs to its answer (Autosave holds its lock meanwhile, so a
        // newer save never overtakes it on the server).
        val result =
            try {
                withContext(NonCancellable) { repo.saveNotes(appointmentId, body) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        _state.update {
            it.copy(
                // An edit made while saving stays unsaved (its own timer saves it).
                dirty = autosave.changes != version || result.isFailure,
                saveError =
                    result.exceptionOrNull()?.let { e ->
                        CodeBanner(e.toUserMessage(Res.string.notesSaveFailed), requestId = e.requestId)
                    },
            )
        }
    }

    /** A Shifa AI draft saved as the visit's note; the notes and drafts are read again. */
    fun confirm(draft: DraftNote) = act(draft) { repo.confirmDraft(draft.id, patientId(), appointmentId) }

    fun discard(draft: DraftNote) = act(draft) { repo.discardDraft(draft.id) }

    private fun act(
        draft: DraftNote,
        call: suspend () -> Result<Unit>,
    ) {
        if (draft.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + draft.id, banner = null) }
        scope.launch {
            val result = call()
            _state.update {
                it.copy(
                    busy = it.busy - draft.id,
                    banner =
                        result.exceptionOrNull()?.let { e ->
                            CodeBanner(e.toUserMessage(Res.string.notesSaveFailed), requestId = e.requestId)
                        },
                )
            }
            load()
        }
    }

    fun dismissBanner() = _state.update { it.copy(banner = null) }
}

val AUTOSAVE = 2.seconds

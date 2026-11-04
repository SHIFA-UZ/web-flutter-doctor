package shifa.doctor.ui.visit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.dental.DentalRepository
import shifa.doctor.data.dental.dentalRepository
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.model.DentalRecord
import shifa.doctor.data.model.DoctorService
import shifa.doctor.data.model.dentalCatalog
import shifa.doctor.data.model.parsePercent
import shifa.doctor.data.model.percentText
import shifa.doctor.data.model.toothLine
import shifa.doctor.data.services.ServicesRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dentalDocSaveFailed
import shifa.doctor.generated.resources.dentalLoadFailed
import shifa.doctor.generated.resources.failedToLoad
import shifa.doctor.ui.emailcode.CodeBanner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class DentalNotesState(
    val record: DentalRecord = DentalRecord(),
    // The discount as typed ("12,5"); the record holds its number.
    val discount: String = "",
    // The saved record was read. Until then nothing can be edited: a save would replace it with an empty one.
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadError: CodeBanner? = null,
    // The doctor's active services for the picker, in group order; null until read.
    val services: List<DoctorService>? = null,
    val servicesError: CodeBanner? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saveError: CodeBanner? = null,
) {
    // The board's "Enter a discount between 0 and 100." (the saved discount is clamped).
    val discountInvalid: Boolean get() = parsePercent(discount) > 100

    // Something to save in the summary: a service or clinical notes.
    val hasContent: Boolean get() = record.lineCount > 0 || record.notes.isNotBlank()
}

/**
 * A dental visit's record in free mode (DENTAL-02, 05 §4.3, board `DaVisit` dental mode), owned by the visit's
 * ViewModel: services per tooth and for the visit, dentition, discount and clinical notes, saved [debounce] after the
 * last change and when the visit leaves, switches mode or ends ([flush]). Flutter saved an empty record over one it
 * failed to read; here nothing is edited until the record was read.
 */
class DentalNotes(
    private val appointmentId: Long,
    private val scope: CoroutineScope,
    private val repo: DentalRepository = dentalRepository,
    private val catalog: ServicesRepository = ServicesRepository(),
    debounce: Duration = DENTAL_AUTOSAVE,
) {
    private val _state = MutableStateFlow(DentalNotesState())
    val state: StateFlow<DentalNotesState> = _state.asStateFlow()

    private val autosave = Autosave(scope, debounce) { save() }

    private var reading: Deferred<Boolean>? = null

    /** Reads the record and the services; again after a failure (Retry). */
    fun load() {
        if (_state.value.services == null) loadServices()
        read()
    }

    /** True once the record has been read: waits for a read on its way, or reads again after a failure (End). */
    suspend fun loaded(): Boolean = _state.value.loaded || read().await()

    private fun read(): Deferred<Boolean> {
        reading?.takeIf { it.isActive || _state.value.loaded }?.let { return it }
        _state.update { it.copy(loading = true, loadError = null) }
        return scope.async {
            val result = repo.record(appointmentId)
            _state.update { s ->
                result.fold(
                    onSuccess = { saved ->
                        val record = saved ?: DentalRecord()
                        s.copy(
                            loading = false,
                            loaded = true,
                            record = record,
                            discount = record.discountPercent.takeIf { it > 0 }?.let(::percentText).orEmpty(),
                        )
                    },
                    onFailure = { e ->
                        s.copy(
                            loading = false,
                            loadError =
                                CodeBanner(
                                    e.toUserMessage(Res.string.dentalLoadFailed),
                                    requestId = e.requestId,
                                ),
                        )
                    },
                )
            }
            result.isSuccess
        }.also { reading = it }
    }

    fun loadServices() {
        _state.update { it.copy(servicesError = null) }
        scope.launch {
            val result = catalog.services()
            _state.update { s ->
                s.copy(
                    services = result.getOrNull()?.let(::dentalCatalog) ?: s.services,
                    servicesError =
                        result.exceptionOrNull()?.let { e ->
                            CodeBanner(e.toUserMessage(Res.string.failedToLoad), requestId = e.requestId)
                        },
                )
            }
        }
    }

    fun edit(change: (DentalRecord) -> DentalRecord) {
        val s = _state.value
        if (!s.loaded) return
        val after = change(s.record)
        if (after == s.record) return
        _state.update { it.copy(record = after, dirty = true, saveError = null) }
        autosave.changed()
    }

    fun add(
        tooth: String,
        service: DoctorService,
    ) = edit { it.withLines(tooth, it.lines(tooth) + service.toothLine()) }

    fun remove(
        tooth: String,
        index: Int,
    ) = edit { r -> r.withLines(tooth, r.lines(tooth).filterIndexed { i, _ -> i != index }) }

    fun dentition(to: Dentition) = edit { it.copy(dentition = if (to == Dentition.Primary) "primary" else "permanent") }

    /** The discount as typed (digits and one separator); the record gets its number. */
    fun discount(text: String) {
        if (!_state.value.loaded) return
        val clean = text.filter { it.isDigit() || it == '.' || it == ',' }
        _state.update { it.copy(discount = clean) }
        edit { it.copy(discountPercent = parsePercent(clean)) }
    }

    fun notes(text: String) = edit { it.copy(notes = text) }

    /** Text from a helper (Shifa AI, the last 025-2) added to the clinical notes after a blank line (Flutter's). */
    fun append(text: String) {
        val add = text.trim().ifEmpty { return }
        edit { r -> r.copy(notes = listOf(r.notes.trimEnd(), add).filter(String::isNotEmpty).joinToString("\n\n")) }
    }

    /** Saves now what's unsaved; true when nothing is left (a record never read has nothing to save). */
    suspend fun flush(): Boolean {
        autosave.flush()
        return !_state.value.dirty
    }

    private suspend fun save() {
        val s = _state.value
        if (!s.dirty) return
        val version = autosave.changes
        _state.update { it.copy(saving = true) }
        // A PUT that has left runs to its answer (see VisitNotes.save).
        val result =
            try {
                withContext(NonCancellable) { repo.save(appointmentId, s.record) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        _state.update {
            it.copy(
                dirty = autosave.changes != version || result.isFailure,
                saveError =
                    result.exceptionOrNull()?.let { e ->
                        CodeBanner(e.toUserMessage(Res.string.dentalDocSaveFailed), requestId = e.requestId)
                    },
            )
        }
    }
}

// Flutter's dental autosave (1200 ms).
val DENTAL_AUTOSAVE = 1200.milliseconds

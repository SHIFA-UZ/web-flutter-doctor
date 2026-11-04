package shifa.doctor.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.catalog.DocumentationTemplate
import shifa.doctor.data.catalog.documentationTemplateFor
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.data.consultation.consultationRepository
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.formsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.model.SignatureState
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiNotesNotReadyTryLater
import shifa.doctor.generated.resources.appointmentLoadFailed
import shifa.doctor.generated.resources.noForm0252Yet
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.signatureRequestFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.patients.chronicWarned

/** How the visit is documented (05 §2.4): free notes, the dental record (DENTAL-02) or the 025-2 form. */
enum class VisitMode { General, Dental, Form0252 }

/** Where the doctor goes from the visit: back, the patient's record, the 025-2 editor (the notes are saved first). */
enum class VisitExit { Back, Patient, Form }

/** The helper region over the notes (05 §2.1): Shifa AI's drafts and notes, or the last 025-2's texts. */
enum class VisitHelper { Ai, Form }

data class VisitUiState(
    val loading: Boolean = true,
    val error: CodeBanner? = null,
    val notFound: Boolean = false,
    val visit: AppointmentDetail? = null,
    val patient: Patient? = null,
    // The visit's reason, from the patient's visit list (the doctor's detail has none).
    val reason: String? = null,
    val lastVisit: Instant? = null,
    // The patient's 025-2 forms, newest first (the 025-2 mode lists them; the helper reads the latest).
    val forms: List<PatientForm> = emptyList(),
    // The doctor's profession allows the dental record.
    val dental: Boolean = false,
    val mode: VisitMode = VisitMode.General,
    val soapOpen: Boolean = false,
    val helper: VisitHelper? = null,
    // The Shifa AI note shown in the helper's carousel.
    val savedIndex: Int = 0,
    val menu: Boolean = false,
    val focus: Boolean = false,
    val signing: Boolean = false,
    val chronicWarning: Boolean = false,
    val banner: CodeBanner? = null,
    // Notes not saved on leaving: ask before going there.
    val askLeave: VisitExit? = null,
    // Ready to go there (the screen navigates, then calls [VisitViewModel.leaving]).
    val left: VisitExit? = null,
) {
    val kind: VisitKind get() = visit?.kind ?: VisitKind.InPerson

    val signature: SignatureState get() = visit?.signature ?: SignatureState.None
}

/**
 * APPT-04 (flow 05/04, spec 05 §2.1–2.4; board `DaVisit` "VISIT WORKSPACE"): one visit's workspace, in the clinic
 * (video adds its pane with VIDEO-02). The visit, its patient, the patient's visits and 025-2 forms are read together;
 * the notes are [notes] (APPT-03); the signature is read again every 10 s only while awaited (APPT-02). Leaving saves
 * the notes first and asks when they couldn't be saved.
 */
class VisitViewModel(
    private val appointmentId: Long,
    private val appointments: AppointmentsRepository = appointmentsRepository,
    private val patients: PatientsRepository = patientsRepository,
    private val forms: FormsRepository = formsRepository,
    consultation: ConsultationRepository = consultationRepository,
    private val profession: () -> String? = { profileRepository.me.value?.profile?.profession },
    private val warned: MutableSet<Long> = chronicWarned,
) : ViewModel() {
    private val _state = MutableStateFlow(VisitUiState())
    val state: StateFlow<VisitUiState> = _state.asStateFlow()

    val notes = VisitNotes(appointmentId, { _state.value.visit?.patientId }, viewModelScope, consultation)

    private var watching: Job? = null

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val visit =
                appointments.detail(appointmentId).getOrElse { e ->
                    return@launch _state.update {
                        it.copy(
                            loading = false,
                            notFound = e is ApiException.NotFound,
                            error =
                                CodeBanner(
                                    e.toUserMessage(Res.string.appointmentLoadFailed),
                                    requestId = e.requestId,
                                ),
                        )
                    }
                }
            val dental = documentationTemplateFor(profession()) == DocumentationTemplate.Dental
            _state.update {
                it.copy(
                    loading = false,
                    visit = visit,
                    dental = dental,
                    // The profession's mode on the first open only (05 §2.4: never over the doctor's choice).
                    mode = if (it.visit == null && dental) VisitMode.Dental else it.mode,
                )
            }
            notes.load()
            if (visit.signature == SignatureState.Waiting) watchSignature()
            val patientId = visit.patientId ?: return@launch
            // The patient's facts are extras: a failure leaves them out, the visit still works.
            val visits = async { patients.appointments(patientId) }
            val formList = async { forms.list(patientId) }
            val patient = patients.patient(patientId).getOrNull()
            val history = visits.await().getOrNull().orEmpty()
            val start = visit.start
            _state.update { s ->
                s.copy(
                    patient = patient ?: s.patient,
                    reason = history.firstOrNull { it.id == appointmentId }?.reason?.takeIf { it.isNotBlank() },
                    lastVisit =
                        history
                            .filter { it.id != appointmentId && it.status.equals("COMPLETED", ignoreCase = true) }
                            .mapNotNull { runCatching { Instant.parse(it.startAt) }.getOrNull() }
                            .filter { start == null || it < start }
                            .maxOrNull(),
                    forms = formList.await().getOrNull()?.filter { it.templateId == FormTemplates.F025_2 }.orEmpty(),
                    // The chronic-disease warning, once per patient per session (05 §2.2, shared with the record).
                    chronicWarning = patient?.chronic != null && warned.add(patientId),
                )
            }
        }
    }

    /** The signature read again every 10 s while awaited; one reader at a time. */
    private fun watchSignature() {
        watching?.cancel()
        watching =
            viewModelScope.launch {
                appointments.signatureUpdates(appointmentId).collect { r ->
                    r.getOrNull()?.let { d -> _state.update { it.copy(visit = d) } }
                }
            }
    }

    fun requestSignature() {
        val s = _state.value
        if (s.signing || s.visit == null || s.signature != SignatureState.None) return
        _state.update { it.copy(signing = true, banner = null) }
        viewModelScope.launch {
            // The notes go first, so what the patient is asked to sign is saved; unsaved, nothing is asked.
            if (!notes.flush()) return@launch _state.update { it.copy(signing = false) }
            val result = appointments.requestSignature(appointmentId)
            _state.update {
                it.copy(
                    signing = false,
                    visit = if (result.isSuccess) it.visit?.copy(signatureRequested = true) else it.visit,
                    banner =
                        result.fold(
                            { CodeBanner(Res.string.patientSignatureRequestSent, error = false) },
                            {
                                    e ->
                                CodeBanner(e.toUserMessage(Res.string.signatureRequestFailed), requestId = e.requestId)
                            },
                        ),
                )
            }
            if (result.isSuccess) watchSignature()
        }
    }

    /** A mode change saves the notes first (they autosave, so nothing is asked; the backend can't empty them). */
    fun mode(to: VisitMode) {
        if (to == _state.value.mode) return _state.update { it.copy(menu = false) }
        _state.update { it.copy(mode = to, menu = false, helper = null) }
        viewModelScope.launch { notes.flush() }
    }

    /** The helper from the notes menu; says so when there is nothing to show (board `DaVisit` notes menu). */
    fun helper(which: VisitHelper) {
        val n = notes.state.value
        val empty =
            when (which) {
                VisitHelper.Ai -> n.drafts.isNullOrEmpty() && n.notes.orEmpty().none { it.fromAi }
                VisitHelper.Form -> _state.value.forms.isEmpty()
            }
        _state.update {
            if (empty) {
                it.copy(
                    menu = false,
                    banner =
                        CodeBanner(
                            if (which == VisitHelper.Ai) {
                                Res.string.aiNotesNotReadyTryLater
                            } else {
                                Res.string.noForm0252Yet
                            },
                            error = false,
                        ),
                )
            } else {
                it.copy(menu = false, helper = which, savedIndex = 0)
            }
        }
    }

    /** A helper's text into the free notes (General mode, where they show). */
    fun addToNotes(text: String) {
        notes.append(text)
        _state.update { it.copy(mode = VisitMode.General) }
    }

    fun ui(change: (VisitUiState) -> VisitUiState) = _state.update(change)

    /**
     * Leaving for [to]: the notes are saved first; when that fails the doctor is asked (05/04 §10). Going back stops
     * the signature reads (the shell keeps this ViewModel; a reopened visit gets a new one).
     */
    fun leave(
        to: VisitExit = VisitExit.Back,
        force: Boolean = false,
    ) {
        if (force) return go(to)
        viewModelScope.launch { if (notes.flush()) go(to) else _state.update { it.copy(askLeave = to) } }
    }

    private fun go(to: VisitExit) {
        if (to == VisitExit.Back) watching?.cancel()
        _state.update { it.copy(askLeave = null, left = to) }
    }

    /** The screen went where [VisitUiState.left] said. */
    fun leaving() = _state.update { it.copy(left = null) }
}

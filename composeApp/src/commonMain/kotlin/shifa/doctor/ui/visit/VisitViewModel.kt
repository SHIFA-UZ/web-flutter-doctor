package shifa.doctor.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.catalog.DocumentationTemplate
import shifa.doctor.data.catalog.documentationTemplateFor
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.data.consultation.VisitSummary
import shifa.doctor.data.consultation.VisitSummaryLabels
import shifa.doctor.data.consultation.VisitSummaryPdf
import shifa.doctor.data.consultation.consultationRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.UploadFile
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.formsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.model.SignatureState
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.model.composeVisitDocumentationText
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.aiDocumentationWillAppearShortly
import shifa.doctor.generated.resources.aiNotesNeedPro
import shifa.doctor.generated.resources.aiNotesNotReadyTryLater
import shifa.doctor.generated.resources.aiNotesUploaded
import shifa.doctor.generated.resources.appointmentLoadFailed
import shifa.doctor.generated.resources.endFailed
import shifa.doctor.generated.resources.noForm0252Yet
import shifa.doctor.generated.resources.noItemsToSave
import shifa.doctor.generated.resources.notesSaveFailed
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.scribeUploadFailed
import shifa.doctor.generated.resources.signatureRequestFailed
import shifa.doctor.generated.resources.summaryNotSaved
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.patients.chronicWarned
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.doctorDisplayName
import kotlin.time.Duration.Companion.seconds

/** How the visit is documented (05 §2.4): free notes, the dental record (DENTAL-02) or the 025-2 form. */
enum class VisitMode { General, Dental, Form0252 }

/** The End pipeline's steps (flow 05/12 §3), as the progress list shows them. Plan and payment join with DENTAL-03. */
enum class EndStep { Notes, Complete, Scribe, Summary }

/** The End sheet and its progress (APPT-07). */
data class EndUiState(
    // The confirmation sheet with the checklist.
    val confirm: Boolean = false,
    val running: Boolean = false,
    val steps: List<EndStep> = emptyList(),
    val at: EndStep? = null,
    // Seconds waited for the AI notes (the step shows them; Continue without waiting skips).
    val scribeSeconds: Int = 0,
    // The visit was completed on the server: from here only the summary can be retried.
    val completed: Boolean = false,
    // Done: the summary saved, or why there is none.
    val done: Boolean = false,
    val summary: PatientDocument? = null,
    val noSummary: StringResource? = null,
    // The visit was completed but its summary couldn't be saved: Retry summary (the sheet, or the footer once closed).
    val summaryFailed: Boolean = false,
    // The sheet was closed on a failed summary; the footer offers Retry summary.
    val hidden: Boolean = false,
    val error: CodeBanner? = null,
)

/** The words the summary and its document need, in the app's language (the screen builds them). */
class EndLabels(
    val summary: VisitSummaryLabels = VisitSummaryLabels(),
    val aiHeading: String = "From Shifa AI",
    val doctorHeading: String = "Doctor notes",
    val documentTitle: (date: String, time: String) -> String = { d, t -> "Appointment documentation $d $t" },
    val endedAt: (LocalDateTime) -> String = { "${displayDate(it.date)} ${displayTime(it.time)}" },
    val visitType: (VisitKind) -> String = { it.name },
    val gender: (String) -> String? = { it },
)

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
    // SCRIBE-01: a recording went to the AI scribe (End waits for its notes, SCRIBE-02).
    val awaitingScribe: Boolean = false,
    // A recording that couldn't be uploaded is kept for another try.
    val scribeRetry: Boolean = false,
    // A recording is on its way: no second one meanwhile (uploads never overlap).
    val scribeUploading: Boolean = false,
    // APPT-07: End appointment.
    val end: EndUiState = EndUiState(),
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
    private val consultation: ConsultationRepository = consultationRepository,
    private val language: () -> AppLanguage = { languageStorage.language.value },
    private val profession: () -> String? = { profileRepository.me.value?.profile?.profession },
    private val documents: DocumentsRepository = documentsRepository,
    private val summaryPdf: (VisitSummary, VisitSummaryLabels) -> ByteArray? = { s, l -> VisitSummaryPdf().pdf(s, l) },
    private val clock: () -> PracticeClock = { PracticeClock(profileRepository.me.value?.profile?.timeZone) },
    // The calendar's day of the visit and today read again once the visit is completed (flow 05/12 step 4).
    private val completedOn: suspend (PracticeClock, LocalDate) -> Unit = { c, d -> calendarRepository.changed(c, d) },
    private val doctor: () -> Pair<String, String?> = {
        profileRepository.me.value?.profile.let { doctorDisplayName(it?.firstName, it?.lastName, null) to it?.clinic }
    },
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

    private var unsent: Pair<ByteArray, String?>? = null

    /**
     * SCRIBE-01 (flow 05/06 §5): the consultation's recording to the AI scribe; its notes come back as Shifa AI's
     * drafts. A failed upload keeps the recording for "Upload the recording again" (Flutter lost it). The answer is
     * the message to show.
     */
    suspend fun uploadScribe(
        audio: ByteArray,
        recordingId: String?,
    ): CodeBanner {
        unsent = audio to recordingId
        _state.update { it.copy(scribeUploading = true, scribeRetry = false) }
        val result =
            try {
                consultation.uploadRecording(appointmentId, audio, language(), recordingId)
            } finally {
                _state.update { it.copy(scribeUploading = false) }
            }
        if (result.isSuccess) unsent = null
        _state.update {
            it.copy(
                awaitingScribe = it.awaitingScribe || result.isSuccess,
                scribeRetry = result.isFailure,
            )
        }
        return result.fold(
            { CodeBanner(Res.string.aiNotesUploaded, error = false) },
            { e ->
                CodeBanner(
                    when (e) {
                        is ApiException.RateLimit -> Res.string.aiDailyLimitReached
                        is ApiException.Forbidden -> Res.string.aiNotesNeedPro
                        else -> Res.string.scribeUploadFailed
                    },
                    requestId = e.requestId,
                )
            },
        )
    }

    /** The kept recording, sent again. */
    fun retryScribe() {
        if (_state.value.scribeUploading) return
        val (audio, id) = unsent ?: return
        viewModelScope.launch {
            // The upload first: it updates the state itself, so it can't run inside an update (which may run again).
            val banner = uploadScribe(audio, id)
            _state.update { it.copy(banner = banner) }
        }
    }

    /** Set by the screen: the summary's words in the app's language. */
    var endLabels = EndLabels()

    private var ending: Job? = null
    private var waitingScribe: Job? = null
    private var endedAt: LocalDateTime? = null

    fun openEnd() = _state.update { if (it.end.running) it else it.copy(end = EndUiState(confirm = true)) }

    /** Closes the sheet; a completed visit whose summary failed keeps that state for the footer's Retry summary. */
    fun closeEnd() =
        _state.update {
            when {
                it.end.running -> it
                it.end.completed && it.end.summaryFailed -> it.copy(end = it.end.copy(hidden = true))
                else -> it.copy(end = EndUiState())
            }
        }

    /**
     * End appointment (APPT-07, flow 05/12): the notes saved, the visit completed (`awaitingScribe` when a recording
     * went to the scribe), the scribe's notes waited for (SCRIBE-02: every 3 s, at most 120 s, "Continue without
     * waiting"), the visit and its notes read again, then the summary made and filed under the patient. A failure
     * before completion leaves the visit open (End again); after it, only the summary is retried ("Visit completed.
     * Summary could not be saved.", Flutter left the doctor stuck). Runs once at a time.
     */
    fun end() {
        val s = _state.value
        if (s.end.running || s.visit == null) return
        val steps =
            listOfNotNull(EndStep.Notes, EndStep.Complete, EndStep.Scribe.takeIf { s.awaitingScribe }, EndStep.Summary)
        _state.update { it.copy(end = EndUiState(running = true, steps = steps, at = EndStep.Notes)) }
        ending =
            viewModelScope.launch {
                if (!notes.flush()) return@launch fail(CodeBanner(Res.string.notesSaveFailed))
                step(EndStep.Complete)
                val body = notes.state.value.text.body(notes.labels)
                val done =
                    consultation.complete(
                        appointmentId,
                        doctorNotes = body,
                        awaitingScribe = s.awaitingScribe,
                    ).getOrElse {
                            e ->
                        return@launch fail(CodeBanner(e.toUserMessage(Res.string.endFailed), requestId = e.requestId))
                    }
                endedAt = clock().local(currentInstant())
                _state.update {
                    it.copy(
                        visit = it.visit?.copy(status = "COMPLETED"),
                        end = it.end.copy(completed = true),
                    )
                }
                s.visit.start?.let { start -> launch { completedOn(clock(), clock().local(start).date) } }
                if ((done.scribePending || s.awaitingScribe) && !done.hasScribeNote) waitForScribe()
                summary()
            }
    }

    private fun step(at: EndStep) =
        _state.update { s ->
            // A step that turned out to be needed (the server says the scribe is pending) joins the list.
            val steps = if (at in s.end.steps) s.end.steps else s.end.steps.dropLast(1) + at + s.end.steps.last()
            s.copy(end = s.end.copy(at = at, steps = steps))
        }

    private fun fail(error: CodeBanner) =
        _state.update {
            it.copy(end = it.end.copy(running = false, at = null, error = error))
        }

    /** SCRIBE-02: the scribe's status every 3 s (120 s at most) until its notes are there, or the doctor skips. */
    private suspend fun waitForScribe() {
        step(EndStep.Scribe)
        coroutineScope {
            val started = currentInstant()
            val ticker =
                launch {
                    while (true) {
                        _state.update {
                            it.copy(
                                end = it.end.copy(scribeSeconds = (currentInstant() - started).inWholeSeconds.toInt()),
                            )
                        }
                        delay(1.seconds)
                    }
                }
            waitingScribe = launch { consultation.scribeUpdates(appointmentId).collect {} }
            waitingScribe?.join()
            ticker.cancel()
        }
    }

    /** Continue without waiting: the summary is made without the AI notes (they arrive later). */
    fun skipScribe() {
        waitingScribe?.cancel()
    }

    /** Retry summary: the visit is already completed; the re-read and the summary run again. */
    fun retrySummary() {
        val s = _state.value.end
        if (s.running || !s.completed) return
        _state.update {
            it.copy(
                end =
                    it.end.copy(
                        running = true,
                        summaryFailed = false,
                        hidden = false,
                        error = null,
                        at = EndStep.Summary,
                    ),
            )
        }
        ending = viewModelScope.launch { summary() }
    }

    private suspend fun summary() {
        step(EndStep.Summary)
        val visit = appointments.detail(appointmentId).getOrNull() ?: _state.value.visit
        notes.read()
        val n = notes.state.value
        // Unread notes would make a summary without them (or none at all): it fails and is retried instead.
        if (n.loadError != null) return summaryFailed()
        val labels = endLabels
        // The doctor's text as typed when the server has no manual note yet (Flutter's extraDoctorNotes).
        val local = n.text.body(notes.labels).takeIf { n.notes.orEmpty().none { note -> !note.fromAi } }.orEmpty()
        val text =
            composeVisitDocumentationText(
                labels.aiHeading,
                labels.doctorHeading,
                n.notes.orEmpty(),
                n.drafts.orEmpty(),
                local,
            )
        if (visit != null) _state.update { it.copy(visit = visit.copy(status = "COMPLETED")) }
        if (text.isBlank()) {
            val pending = _state.value.awaitingScribe
            return _state.update {
                it.copy(
                    end =
                        it.end.copy(
                            running = false,
                            done = true,
                            at = null,
                            noSummary = if (pending) AI_LATER else Res.string.noItemsToSave,
                        ),
                )
            }
        }
        val patient = _state.value.patient
        val at = endedAt ?: clock().local(currentInstant())
        val (doctorName, clinic) = doctor()
        val start = visit?.start
        val end = visit?.end
        val pdf =
            summaryPdf(
                VisitSummary(
                    appointmentId = appointmentId,
                    clinic = clinic,
                    endedAt = labels.endedAt(at),
                    patientName = visit?.patientName ?: patient?.name.orEmpty(),
                    patientId = visit?.patientId,
                    birthDate = patient?.birth?.let(::displayDate),
                    gender = patient?.gender?.takeIf { it.isNotBlank() }?.let(labels.gender),
                    doctorName = doctorName,
                    profession = profession(),
                    visitType = labels.visitType(visit?.kind ?: VisitKind.InPerson),
                    minutes = if (start != null && end != null) (end - start).inWholeMinutes.toInt() else null,
                    notes = text,
                    signatureBase64 = visit?.patientSignatureImageBase64,
                    signedOn =
                        visit?.signedAt?.let {
                            clock().local(
                                it,
                            )
                        }?.let { "${displayDate(it.date)} ${displayTime(it.time)}" },
                ),
                labels.summary,
            )
        val patientId = visit?.patientId
        val uploaded =
            if (pdf == null || patientId == null) {
                null
            } else {
                val day = at.date.toString()
                documents.upload(
                    patientId,
                    UploadFile(pdf, "appointment_${appointmentId}_$day.pdf", DocumentFile.PDF),
                    labels.documentTitle(displayDate(at.date), displayTime(at.time)),
                    DocumentCategory.APPOINTMENT_NOTE.name,
                    appointmentId = appointmentId,
                ).getOrNull()
            }
        if (uploaded == null) return summaryFailed()
        _state.update { it.copy(end = it.end.copy(running = false, at = null, done = true, summary = uploaded)) }
    }

    private fun summaryFailed() =
        _state.update {
            it.copy(
                end =
                    it.end.copy(
                        running = false,
                        at = null,
                        summaryFailed = true,
                        error = CodeBanner(Res.string.summaryNotSaved),
                    ),
            )
        }

    /** A mode change saves the notes first (they autosave, so nothing is asked; the backend can't empty them). */
    fun mode(to: VisitMode) {
        if (to == _state.value.mode) return _state.update { it.copy(menu = false) }
        _state.update { it.copy(mode = to, menu = false, helper = null) }
        viewModelScope.launch { notes.flush() }
    }

    /** The helper from the notes menu; says so when there is nothing to show (board `DaVisit` notes menu). */
    fun helper(which: VisitHelper) {
        _state.update { it.copy(menu = false) }
        // Shifa AI's part is read again first: the scribe's notes arrive while the visit is open (flow 05/06 §6).
        viewModelScope.launch {
            if (which == VisitHelper.Ai) notes.read()
            showHelper(which)
        }
    }

    private fun showHelper(which: VisitHelper) {
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

private val AI_LATER = Res.string.aiDocumentationWillAppearShortly

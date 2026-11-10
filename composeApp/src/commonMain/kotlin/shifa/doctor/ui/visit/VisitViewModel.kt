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
import kotlinx.coroutines.withTimeoutOrNull
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
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.data.consultation.PlanSummaryLine
import shifa.doctor.data.consultation.VisitSummary
import shifa.doctor.data.consultation.VisitSummaryLabels
import shifa.doctor.data.consultation.VisitSummaryPdf
import shifa.doctor.data.consultation.VisitSummaryPlan
import shifa.doctor.data.consultation.consultationRepository
import shifa.doctor.data.dental.DentalRepository
import shifa.doctor.data.dental.dentalRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.documents.UploadFile
import shifa.doctor.data.documents.documentsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.finance.financeRepository
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.formsRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.DocumentFile
import shifa.doctor.data.model.FormTemplates
import shifa.doctor.data.model.IcdSuggestion
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.model.SignatureState
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.model.composeVisitDocumentationText
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.data.patients.patientsRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.services.ServicesRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.data.treatmentplans.treatmentPlansRepository
import shifa.doctor.data.video.VideoRepository
import shifa.doctor.data.video.videoRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.aiDocumentationWillAppearShortly
import shifa.doctor.generated.resources.aiNotesNeedPro
import shifa.doctor.generated.resources.aiNotesNotReadyTryLater
import shifa.doctor.generated.resources.aiNotesUploaded
import shifa.doctor.generated.resources.amountGreaterThanZero
import shifa.doctor.generated.resources.appointmentLoadFailed
import shifa.doctor.generated.resources.appointmentPlanNoLinesOnTooth
import shifa.doctor.generated.resources.dentalDocSaveFailed
import shifa.doctor.generated.resources.dentalLoadFailed
import shifa.doctor.generated.resources.endFailed
import shifa.doctor.generated.resources.icdAppliedOnOpen
import shifa.doctor.generated.resources.noForm0252Yet
import shifa.doctor.generated.resources.noItemsToSave
import shifa.doctor.generated.resources.noShowMarked
import shifa.doctor.generated.resources.notesSaveFailed
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.photoLimit
import shifa.doctor.generated.resources.recordingAlreadyProcessing
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.scribeUploadFailed
import shifa.doctor.generated.resources.signatureRequestFailed
import shifa.doctor.generated.resources.summaryNotSaved
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.patients.chronicWarned
import shifa.doctor.utils.PickedImage
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.VideoCall
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.doctorDisplayName
import kotlin.time.Duration.Companion.seconds

/** How the visit is documented (05 §2.4): free notes, the dental record (DENTAL-02) or the 025-2 form. */
enum class VisitMode { General, Dental, Form0252 }

/** The End pipeline's steps (flow 05/12 §3), as the progress list shows them. Plan and payment join with DENTAL-03. */
enum class EndStep { Notes, Plan, Payment, Complete, Scribe, Summary }

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
    val photo: (PhotoKind) -> String = { if (it == PhotoKind.Before) "Before Treatment" else "After Treatment" },
    val method: (PayMethod) -> String = { it.name },
    val untitledPlan: String = "(untitled plan)",
)

/** Where the doctor goes from the visit: back, the patient's record, the 025-2 editor (the notes are saved first). */
enum class VisitExit { Back, Patient, Form }

/** The helper region over the notes (05 §2.1): Shifa AI's drafts and notes, or the last 025-2's texts. */
enum class VisitHelper { Ai, Form }

/** APPT-05: which treatment photo. */
enum class PhotoKind { Before, After }

/** A before/after photo, kept with the visit until End puts it into the summary (Flutter's; board `DaVisit`). */
class VisitPhoto(
    val kind: PhotoKind,
    val name: String,
    val bytes: ByteArray,
)

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
    // BE-10: "Mark as no-show" from a call the patient never joined: asked, the patient told, on its way.
    val askNoShow: Boolean = false,
    val notifyNoShow: Boolean = true,
    val markingNoShow: Boolean = false,
    // SCRIBE-01: a recording went to the AI scribe (End waits for its notes, SCRIBE-02).
    val awaitingScribe: Boolean = false,
    // A recording that couldn't be uploaded is kept for another try.
    val scribeRetry: Boolean = false,
    // A recording is on its way: no second one meanwhile (uploads never overlap).
    val scribeUploading: Boolean = false,
    // APPT-07: End appointment.
    val end: EndUiState = EndUiState(),
    // APPT-08: an AI-suggested ICD-10 code applied here, filled into the 025-2 opened next.
    val icd: IcdSuggestion? = null,
    // DENTAL-02: the tooth (or the general key) whose services sheet is open.
    val dentalTooth: String? = null,
    // APPT-05: the photos taken, the Camera / Gallery sheet for one kind, the kind a picker is open for, the camera
    // refused.
    val photos: List<VisitPhoto> = emptyList(),
    val photoSheet: PhotoKind? = null,
    val photoPending: PhotoKind? = null,
    val cameraDenied: Boolean = false,
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
    private val dentalRepo: DentalRepository = dentalRepository,
    private val services: ServicesRepository = ServicesRepository(),
    private val plans: TreatmentPlansRepository = treatmentPlansRepository,
    private val finance: FinanceRepository = financeRepository,
    private val clinics: ClinicsRepository = clinicsRepository,
    // VIDEO-02: a video visit's calls (Daily); null for a visit in person.
    calls: (() -> VideoCall)? = null,
    videoRepo: VideoRepository = videoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VisitUiState())
    val state: StateFlow<VisitUiState> = _state.asStateFlow()

    val notes = VisitNotes(appointmentId, { _state.value.visit?.patientId }, viewModelScope, consultation)

    // DENTAL-02: read for a dental doctor only.
    val dentalRecord = DentalNotes(appointmentId, viewModelScope, dentalRepo, services)

    // VIDEO-02: the call of a video visit, kept here so it survives opening the patient's record mid-call.
    val video: VideoVisit? =
        calls?.let {
            VideoVisit(appointmentId, viewModelScope, it, {
                _state.value.visit.let {
                        v ->
                    v?.start to v?.end
                }
            }, videoRepo)
        }

    // DENTAL-03: the patient's treatment plan in this visit.
    val plan =
        VisitPlan(appointmentId, viewModelScope, plans, finance, clinics) {
            appointments.detail(appointmentId).map { it.fulfilledLineIds }
        }

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
            // The patient's record before the workspace opens (at most PATIENT_WAIT), so a chronic-disease warning comes
            // before any action, never after a doctor has started (flow 02/02 §8; Flutter skipped it when not loaded).
            val early =
                visit.patientId?.let {
                        id ->
                    withTimeoutOrNull(PATIENT_WAIT) { patients.patient(id).getOrNull() }
                }
            _state.update {
                it.copy(
                    loading = false,
                    visit = visit,
                    dental = dental,
                    // The profession's mode on the first open only (05 §2.4: never over the doctor's choice).
                    mode = if (it.visit == null && dental) VisitMode.Dental else it.mode,
                    patient = early ?: it.patient,
                    chronicWarning =
                        it.chronicWarning || (
                            early?.chronic != null && visit.patientId?.let(
                                warned::add,
                            ) == true
                        ),
                )
            }
            notes.load()
            if (dental) dentalRecord.load()
            if (visit.signature == SignatureState.Waiting) watchSignature()
            val patientId = visit.patientId ?: return@launch
            // An open visit only: a completed one has nothing left to tick or pay (flow 05/08 §2).
            if (!visit.completed && _state.value.end == EndUiState()) {
                plan.load(patientId, visit.linkedPlanId, visit.fulfilledLineIds)
            }
            // The patient's facts are extras: a failure leaves them out, the visit still works.
            val visits = async { patients.appointments(patientId) }
            val formList = async { forms.list(patientId) }
            // Read here only when it didn't come in time above.
            val patient = early ?: patients.patient(patientId).getOrNull()
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
                    chronicWarning = s.chronicWarning || (patient?.chronic != null && warned.add(patientId)),
                )
            }
        }
    }

    // Whether the visit is on screen: the signature isn't polled from another tab or behind the app.
    private var front = true

    /** The screen came to the front or left it: the awaited signature is read again on return, never in between. */
    fun foreground(on: Boolean) {
        front = on
        if (!on) {
            watching?.cancel()
        } else if (_state.value.signature == SignatureState.Waiting) {
            watchSignature()
        }
    }

    /** The signature read again every 10 s while awaited and on screen; one reader at a time. */
    private fun watchSignature() {
        watching?.cancel()
        if (!front) return
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
    ): CodeBanner =
        // In this ViewModel's scope, not the caller's: the recorder's scope ends when the doctor opens the patient's
        // record mid-upload, which cancelled the upload and left the recording with no "Upload again".
        viewModelScope.async { sendScribe(audio, recordingId) }.await()

    private suspend fun sendScribe(
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

    fun openEnd() =
        _state.update {
            when {
                it.end.running -> it
                // The scribe's recording is still going up: ended now, the visit would be filed as if there were none
                // (its notes are only waited for once the upload has answered).
                it.scribeUploading -> it.copy(banner = CodeBanner(Res.string.recordingAlreadyProcessing, error = false))
                else -> it.copy(end = EndUiState(confirm = true))
            }
        }

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
        val p = plan.state.value
        // Before any step: the plan's ticks are applied (and lock the plan) before the payment is sent, so an amount
        // that can't be paid must stop End here, not after them.
        if (p.pendingPayment && p.amountInvalid) {
            return _state.update {
                it.copy(end = EndUiState(confirm = true, error = CodeBanner(Res.string.amountGreaterThanZero)))
            }
        }
        val steps =
            listOfNotNull(
                EndStep.Notes,
                EndStep.Plan.takeIf { p.ticks.isNotEmpty() },
                EndStep.Payment.takeIf { p.pendingPayment },
                EndStep.Complete,
                EndStep.Scribe.takeIf { s.awaitingScribe },
                EndStep.Summary,
            )
        _state.update { it.copy(end = EndUiState(running = true, steps = steps, at = EndStep.Notes)) }
        // A video visit leaves its call first (flow 05/12; the visit's End, not the call's).
        video?.release()
        ending =
            viewModelScope.launch {
                if (!notes.flush()) return@launch fail(CodeBanner(Res.string.notesSaveFailed))
                // A dental record that couldn't be read would be left out of the summary: read it first (or stop).
                if (s.dental && !dentalRecord.loaded()) return@launch fail(CodeBanner(Res.string.dentalLoadFailed))
                if (!dentalRecord.flush()) return@launch fail(CodeBanner(Res.string.dentalDocSaveFailed))
                // DENTAL-03: the ticked plan lines, then the payment; either failing leaves the visit open.
                if (EndStep.Plan in steps) {
                    step(EndStep.Plan)
                    plan.applyTicks()?.let { return@launch fail(CodeBanner(it)) }
                }
                if (EndStep.Payment in steps) {
                    step(EndStep.Payment)
                    plan.recordPayment()?.let { return@launch fail(CodeBanner(it)) }
                }
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
        // As saved: the discount clamped, the notes trimmed.
        val dental = dentalRecord.state.value.takeIf { it.loaded && it.hasContent }?.record?.payload()
        val photos = _state.value.photos.map { labels.photo(it.kind) to it.bytes }
        val planPart = planSummary(labels)
        if (text.isBlank() && dental == null && photos.isEmpty() && planPart == null) {
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
                    dental = dental,
                    photos = photos,
                    plan = planPart,
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
        if (to == VisitMode.Dental) dentalRecord.load()
        viewModelScope.launch {
            notes.flush()
            dentalRecord.flush()
        }
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

    /** APPT-08: the suggestion is kept for the 025-2 (opened from the Form 025-2 mode) and that mode shows it. */
    fun applyIcd(suggestion: IcdSuggestion) {
        mode(VisitMode.Form0252)
        _state.update {
            it.copy(
                helper = null,
                icd = suggestion,
                banner = CodeBanner(Res.string.icdAppliedOnOpen, error = false, args = listOf(suggestion.code)),
            )
        }
    }

    /** A helper's text into the dental record's clinical notes in Dental mode (Flutter's), else the free notes. */
    fun addToNotes(text: String) {
        if (_state.value.mode == VisitMode.Dental && dentalRecord.state.value.loaded) return dentalRecord.append(text)
        notes.append(text)
        _state.update { it.copy(mode = VisitMode.General) }
    }

    fun ui(change: (VisitUiState) -> VisitUiState) = _state.update(change)

    /** DENTAL-03: the plan's part of the summary: lines completed and the payment in this visit; null when neither. */
    private fun planSummary(labels: EndLabels): VisitSummaryPlan? {
        val p = plan.state.value
        val d = p.detail?.takeIf { p.selectedId != null && it.summary.id == p.selectedId } ?: return null
        val done = d.lines.filter { it.id in p.fulfilled }
        if (done.isEmpty() && p.recorded == null) return null
        return VisitSummaryPlan(
            title = d.summary.title?.takeIf { it.isNotBlank() } ?: "${labels.untitledPlan} #${d.summary.id}",
            totalMinor = d.summary.totalMinor,
            paidMinor = d.summary.paidMinor,
            owedMinor = d.summary.owedMinor,
            currency = d.summary.currency,
            lines = done.map { PlanSummaryLine(it.tooth, it.title, it.lineTotalMinor, it.currency) },
            payment = p.recorded?.let { it to labels.method(p.method) },
        )
    }

    /** A tooth's (or the general) plan lines; says so when there are none (board `DaVisit`). */
    fun openPlanTooth(key: String) {
        if (!plan.open(key)) {
            _state.update { it.copy(banner = CodeBanner(Res.string.appointmentPlanNoLinesOnTooth, error = false)) }
        }
    }

    /** APPT-05: Camera / Gallery for [kind]; at most [MAX_PHOTOS] (flow 05/04 §8), one picker at a time. */
    fun photo(kind: PhotoKind) =
        _state.update {
            when {
                it.photoPending != null -> it.copy(menu = false)
                it.photos.size >= MAX_PHOTOS ->
                    it.copy(menu = false, banner = CodeBanner(Res.string.photoLimit, error = false))
                else -> it.copy(menu = false, photoSheet = kind)
            }
        }

    /** A picker opens for the sheet's kind: its answer goes to that kind, whatever is opened meanwhile. */
    fun pickingPhoto() =
        _state.update {
            it.copy(
                photoPending = it.photoSheet,
                photoSheet = null,
                cameraDenied = false,
            )
        }

    /** The picker's answer (null: cancelled or refused); the label is "Before Treatment · name". */
    fun addPhoto(picked: PickedImage?) =
        _state.update { s ->
            val kind = s.photoPending ?: return@update s
            val add = picked?.takeIf { s.photos.size < MAX_PHOTOS }?.let { VisitPhoto(kind, it.fileName, it.bytes) }
            s.copy(photoPending = null, photos = s.photos + listOfNotNull(add))
        }

    fun removePhoto(index: Int) = _state.update { s -> s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }) }

    /**
     * Leaving for [to]: the notes are saved first; when that fails the doctor is asked (05/04 §10). Going back stops
     * the signature reads (the shell keeps this ViewModel; a reopened visit gets a new one).
     */
    fun leave(
        to: VisitExit = VisitExit.Back,
        force: Boolean = false,
    ) {
        if (force) return go(to)
        viewModelScope.launch {
            val saved = notes.flush() and dentalRecord.flush()
            // Photos live only here until a summary holds them: going back without one asks first (the patient's record
            // and the 025-2 come back to this visit).
            val pending = _state.value.photos.isNotEmpty() || plan.state.value.hasPending
            val lose = to == VisitExit.Back && pending && !_state.value.end.done
            if (saved && !lose) go(to) else _state.update { it.copy(askLeave = to) }
        }
    }

    private fun go(to: VisitExit) {
        // The shell keeps this ViewModel: going back lets the photos go (a reopened visit gets a new one).
        if (to == VisitExit.Back) {
            watching?.cancel()
            video?.release()
            _state.update { it.copy(photos = emptyList()) }
        }
        _state.update { it.copy(askLeave = null, left = to) }
    }

    fun askNoShow(ask: Boolean) =
        _state.update { if (it.markingNoShow) it else it.copy(askNoShow = ask, notifyNoShow = true) }

    fun notifyNoShow(on: Boolean) = _state.update { it.copy(notifyNoShow = on) }

    /**
     * The visit recorded as missed (BE-10): the call is left, the notes saved, the calendar day read again, and the
     * doctor goes back with "Marked as no-show"; a refusal (e.g. 409 outside the window) stays here as a banner.
     */
    fun noShow() {
        val s = _state.value
        if (s.markingNoShow) return
        // The patient joined while the question was open: not a no-show.
        if (video?.state?.value?.patientSeen == true) return _state.update { it.copy(askNoShow = false) }
        _state.update { it.copy(markingNoShow = true) }
        viewModelScope.launch {
            appointments.noShow(appointmentId, s.notifyNoShow).fold(
                {
                    _state.update {
                        it.copy(
                            markingNoShow = false,
                            askNoShow = false,
                            visit = it.visit?.copy(status = "NO_SHOW"),
                        )
                    }
                    s.visit?.start?.let { start -> launch { completedOn(clock(), clock().local(start).date) } }
                    ShellNotice.pending.value = Notice(Res.string.noShowMarked, success = true)
                    leave(VisitExit.Back)
                },
                { e ->
                    _state.update {
                        it.copy(
                            markingNoShow = false,
                            askNoShow = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    /** The screen went where [VisitUiState.left] said. */
    fun leaving() = _state.update { it.copy(left = null) }

    override fun onCleared() {
        video?.release()
    }
}

private val AI_LATER = Res.string.aiDocumentationWillAppearShortly

// Flow 05/04 §8: photos per visit.
const val MAX_PHOTOS = 20

// How long a visit waits for its patient's record before opening without it (flow 02/02 §8: about 3 s).
private val PATIENT_WAIT = kotlin.time.Duration.parse("3s")

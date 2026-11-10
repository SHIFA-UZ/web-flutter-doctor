package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.consultation.VisitSummaryLabels
import shifa.doctor.data.forms.Icd10Hit
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.IcdSuggestion
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.SignatureState
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.model.stripScribeTranscript
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.DoctorFeature
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.addToNotes
import shifa.doctor.generated.resources.afterTreatment
import shifa.doctor.generated.resources.aiNote
import shifa.doctor.generated.resources.aiNotesHeading
import shifa.doctor.generated.resources.aiNotesNeedPro
import shifa.doctor.generated.resources.aiSuggestedDiagnoses
import shifa.doctor.generated.resources.allergyChip
import shifa.doctor.generated.resources.apply
import shifa.doctor.generated.resources.appointmentEndedDocumentationSaved
import shifa.doctor.generated.resources.appointmentGone
import shifa.doctor.generated.resources.appointmentLoadFailed
import shifa.doctor.generated.resources.appointmentPlanFinanceOutstanding
import shifa.doctor.generated.resources.appointmentPlanFinancePaid
import shifa.doctor.generated.resources.appointmentPlanFinanceSessionPayment
import shifa.doctor.generated.resources.appointmentPlanFinanceTotal
import shifa.doctor.generated.resources.appointmentTreatmentPlanTitle
import shifa.doctor.generated.resources.back
import shifa.doctor.generated.resources.beforeTreatment
import shifa.doctor.generated.resources.chooseFromGallery
import shifa.doctor.generated.resources.clinicPaymentsColAmount
import shifa.doctor.generated.resources.clinicTreatmentPlansUntitled
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.complaints
import shifa.doctor.generated.resources.consultationFocusModeTitle
import shifa.doctor.generated.resources.consultationUploadHint
import shifa.doctor.generated.resources.dateOfBirth
import shifa.doctor.generated.resources.dentalClinicalNotes
import shifa.doctor.generated.resources.dentalDiscount
import shifa.doctor.generated.resources.dentalGeneralServicesShort
import shifa.doctor.generated.resources.dentalPdfHeader
import shifa.doctor.generated.resources.dentalSubtotal
import shifa.doctor.generated.resources.dentalTotal
import shifa.doctor.generated.resources.diagnosis
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.docMode0252
import shifa.doctor.generated.resources.docModeDental
import shifa.doctor.generated.resources.docModeGeneral
import shifa.doctor.generated.resources.docSectionClinical
import shifa.doctor.generated.resources.docSectionImaging
import shifa.doctor.generated.resources.docSectionLaboratory
import shifa.doctor.generated.resources.docSectionOther
import shifa.doctor.generated.resources.docSectionPrivate
import shifa.doctor.generated.resources.doctor
import shifa.doctor.generated.resources.doctorNotesSection
import shifa.doctor.generated.resources.documents
import shifa.doctor.generated.resources.documentsFinalizeHint
import shifa.doctor.generated.resources.draft
import shifa.doctor.generated.resources.duration
import shifa.doctor.generated.resources.endAppointment
import shifa.doctor.generated.resources.enterNotes
import shifa.doctor.generated.resources.firstVisit
import shifa.doctor.generated.resources.focusMode
import shifa.doctor.generated.resources.form0252
import shifa.doctor.generated.resources.form0252VisitHint
import shifa.doctor.generated.resources.formListEntry
import shifa.doctor.generated.resources.formNotSignedYet
import shifa.doctor.generated.resources.formSignedBadge
import shifa.doctor.generated.resources.formsTab
import shifa.doctor.generated.resources.fromLast0252Form
import shifa.doctor.generated.resources.fromShifaAi
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.genderFemale
import shifa.doctor.generated.resources.genderMale
import shifa.doctor.generated.resources.ic_alert_16
import shifa.doctor.generated.resources.ic_camera_22
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_clipboard_22
import shifa.doctor.generated.resources.ic_eye_16
import shifa.doctor.generated.resources.ic_image_22
import shifa.doctor.generated.resources.ic_lock_16
import shifa.doctor.generated.resources.ic_menu_16
import shifa.doctor.generated.resources.ic_mic_16
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_upload_16
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.icdToApply
import shifa.doctor.generated.resources.inPerson
import shifa.doctor.generated.resources.intelDiscardRecording
import shifa.doctor.generated.resources.intelService
import shifa.doctor.generated.resources.keepRecording
import shifa.doctor.generated.resources.lastForm0252
import shifa.doctor.generated.resources.lastVisitOn
import shifa.doctor.generated.resources.leaveAnyway
import shifa.doctor.generated.resources.leaveUnsavedBody
import shifa.doctor.generated.resources.leaveUnsavedTitle
import shifa.doctor.generated.resources.noDocumentsYet
import shifa.doctor.generated.resources.notes
import shifa.doctor.generated.resources.notesNotSavedBody
import shifa.doctor.generated.resources.notesNotSavedTitle
import shifa.doctor.generated.resources.openForm0252
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientAgeYears
import shifa.doctor.generated.resources.patientId
import shifa.doctor.generated.resources.patientRecordUnavailable
import shifa.doctor.generated.resources.patientSigned
import shifa.doctor.generated.resources.photoSummaryHint
import shifa.doctor.generated.resources.recommended
import shifa.doctor.generated.resources.recordingForAiNotes
import shifa.doctor.generated.resources.removePhoto
import shifa.doctor.generated.resources.requestSignature
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.retryScribeUpload
import shifa.doctor.generated.resources.retrySummary
import shifa.doctor.generated.resources.saveAsNote
import shifa.doctor.generated.resources.soapAssessment
import shifa.doctor.generated.resources.soapNotesSectionSubtitle
import shifa.doctor.generated.resources.soapNotesSectionTitle
import shifa.doctor.generated.resources.soapObjective
import shifa.doctor.generated.resources.soapPlan
import shifa.doctor.generated.resources.soapSubjective
import shifa.doctor.generated.resources.staffVisitsBody
import shifa.doctor.generated.resources.staffVisitsTitle
import shifa.doctor.generated.resources.startAiNotes
import shifa.doctor.generated.resources.startAiNotesAgain
import shifa.doctor.generated.resources.startsIn
import shifa.doctor.generated.resources.stay
import shifa.doctor.generated.resources.summaryAppointmentId
import shifa.doctor.generated.resources.summaryDoctorSignature
import shifa.doctor.generated.resources.summaryDocumentTitle
import shifa.doctor.generated.resources.summaryFooter
import shifa.doctor.generated.resources.summaryMinutes
import shifa.doctor.generated.resources.summaryNotSaved
import shifa.doctor.generated.resources.summaryPage
import shifa.doctor.generated.resources.summaryPatientSignature
import shifa.doctor.generated.resources.summaryPlanDoneThisVisit
import shifa.doctor.generated.resources.summarySignatures
import shifa.doctor.generated.resources.summarySignedElectronically
import shifa.doctor.generated.resources.summaryTitle
import shifa.doctor.generated.resources.summaryVideoVisit
import shifa.doctor.generated.resources.summaryVisitType
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.generated.resources.tooth
import shifa.doctor.generated.resources.treatment
import shifa.doctor.generated.resources.upload
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.visitTime
import shifa.doctor.generated.resources.voiceRecordingFinishHint
import shifa.doctor.generated.resources.waitingForPatientSignature
import shifa.doctor.ui.calendar.NoShowQuestion
import shifa.doctor.ui.calendar.SlotPickSheet
import shifa.doctor.ui.calendar.status
import shifa.doctor.ui.documents.DocumentCard
import shifa.doctor.ui.documents.DocumentEditSheets
import shifa.doctor.ui.documents.DocumentUploadViewModel
import shifa.doctor.ui.documents.DocumentsActions
import shifa.doctor.ui.documents.PatientDocumentsViewModel
import shifa.doctor.ui.documents.UploadSheets
import shifa.doctor.ui.documents.UploadStatus
import shifa.doctor.ui.documents.UploadUiState
import shifa.doctor.ui.documents.documentTitle
import shifa.doctor.ui.documents.rememberUploadSources
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.patients.ChronicWarning
import shifa.doctor.ui.patients.chronicLabel
import shifa.doctor.ui.profile.CameraDeniedBanner
import shifa.doctor.ui.profile.ProfileChip
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.speech.DictationState
import shifa.doctor.ui.speech.LocalDictation
import shifa.doctor.ui.speech.RecordingPill
import shifa.doctor.ui.speech.SpeechOrPlainField
import shifa.doctor.ui.speech.appendTranscript
import shifa.doctor.ui.speech.rememberAllowed
import shifa.doctor.ui.speech.rememberDictation
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.utils.KeepScreenOn
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.rememberCallPermissions
import shifa.doctor.utils.rememberCameraLauncher
import shifa.doctor.utils.rememberImagePickerLauncher
import shifa.doctor.utils.rememberMicPermission
import shifa.doctor.utils.rememberVideoCallFactory
import shifa.doctor.utils.tr
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The board's document groups (05 §2.1: laboratory, imaging, clinical, forms, private, other). */
enum class DocBucket(
    val label: StringResource,
) {
    Laboratory(Res.string.docSectionLaboratory),
    Imaging(Res.string.docSectionImaging),
    Clinical(Res.string.docSectionClinical),
    Forms(Res.string.formsTab),
    Private(Res.string.docSectionPrivate),
    Other(Res.string.docSectionOther),
    ;

    companion object {
        private val IMAGING =
            setOf(
                DocumentCategory.MRI,
                DocumentCategory.CT_SCAN,
                DocumentCategory.XRAY,
                DocumentCategory.ULTRASOUND,
                DocumentCategory.MAMMOGRAPHY,
                DocumentCategory.IMAGING_OTHER,
            )

        fun of(d: PatientDocument): DocBucket {
            val kind = d.kind
            return when {
                kind == null || kind == DocumentCategory.OTHER_MEDICAL -> Other
                kind.lab -> Laboratory
                kind in IMAGING -> Imaging
                kind == DocumentCategory.FORM_025_2 -> Forms
                !kind.medical -> Private
                else -> Clinical
            }
        }
    }
}

class VisitActions(
    val back: () -> Unit = {},
    val ui: ((VisitUiState) -> VisitUiState) -> Unit = {},
    val retry: () -> Unit = {},
    val mode: (VisitMode) -> Unit = {},
    val helper: (VisitHelper) -> Unit = {},
    val addToNotes: (String) -> Unit = {},
    val edit: ((SoapNotes) -> SoapNotes) -> Unit = {},
    val retryNotes: () -> Unit = {},
    val confirmDraft: (shifa.doctor.data.model.DraftNote) -> Unit = {},
    val discardDraft: (shifa.doctor.data.model.DraftNote) -> Unit = {},
    val dismissNotesBanner: () -> Unit = {},
    val requestSignature: () -> Unit = {},
    val openPatient: () -> Unit = {},
    val openForm: () -> Unit = {},
    val documents: DocumentsActions = DocumentsActions(),
    // SCRIBE-01: start (after the microphone is allowed), drop, process the recording; a failed upload sent again.
    val startScribe: () -> Unit = {},
    val cancelScribe: () -> Unit = {},
    val finishScribe: () -> Unit = {},
    val retryScribe: () -> Unit = {},
    val dismissScribe: () -> Unit = {},
    // APPT-07: the End sheet; Retry summary from the footer after it was closed on a failed summary.
    val openEnd: () -> Unit = {},
    val retrySummary: () -> Unit = {},
    // APPT-08: an AI ICD suggestion applied (kept for the 025-2), or dropped.
    val applyIcd: (IcdSuggestion) -> Unit = {},
    val clearIcd: () -> Unit = {},
    // DENTAL-02: the dental record's changes and its services sheet.
    val dental: DentalActions = DentalActions(),
    // DENTAL-03: the treatment plan: choosing it, its lines, ticks and the payment.
    val plan: PlanActions = PlanActions(),
    // VIDEO-02: the call (join, camera, microphone, flip, leave).
    val video: VideoActions = VideoActions(),
    // APPT-05: a photo kind's Camera / Gallery sheet, its sources, a photo removed.
    val photo: (PhotoKind) -> Unit = {},
    val takePhoto: () -> Unit = {},
    val choosePhoto: () -> Unit = {},
    val removePhoto: (Int) -> Unit = {},
)

/**
 * The visit workspace (APPT-04, flow 05/04, board `DaVisit` "VISIT WORKSPACE"; phone, single column). Clinic staff
 * get the board's "Visits are run by the doctor" instead (flow 01/18). Not built yet: the video pane (VIDEO-02),
 * the treatment plan (DENTAL-03) and before/after photos (APPT-05).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VisitScreen(
    appointmentId: Long,
    opened: Long,
    staff: Boolean,
    onBack: () -> Unit,
    onOpenPatient: (Long) -> Unit = {},
    // A document in the viewer: the patient, the document, its title.
    onOpenDocument: (Long, PatientDocument, String) -> Unit = { _, _, _ -> },
    // The 025-2 editor for a new form: the patient, and the ICD code applied in the visit (APPT-08).
    onOpenForm: (Long, Icd10Hit?) -> Unit = { _, _ -> },
    // Flow 05/14: Book follow-up opens the booking of the picked free slot for this patient.
    onBook: (CalendarEntry.FreeSlot, PatientOption) -> Unit = { _, _ -> },
    // VIDEO-02: a video visit, with its call.
    video: Boolean = false,
    viewModel: VisitViewModel =
        rememberVideoCallFactory().let { calls ->
            viewModel(
                key = "visit-$appointmentId-$opened",
            ) { VisitViewModel(appointmentId, calls = calls.takeIf { video }) }
        },
) {
    if (staff) return StaffCannotRunVisits(onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notes by viewModel.notes.state.collectAsStateWithLifecycle()
    val dental by viewModel.dentalRecord.state.collectAsStateWithLifecycle()
    val planState by viewModel.plan.state.collectAsStateWithLifecycle()
    // Flow 05/14: the slot picker for a follow-up, its open stamp; null when closed.
    var followUp by remember { mutableStateOf<Long?>(null) }
    val videoState = viewModel.video?.state?.collectAsStateWithLifecycle()?.value
    val callPart = viewModel.video?.let { v -> videoState?.let { VideoPart(it, v.call) } }
    LifecycleResumeEffect(viewModel) {
        viewModel.foreground(true)
        onPauseOrDispose { viewModel.foreground(false) }
    }
    viewModel.video?.let { v ->
        val permissions = rememberCallPermissions(v::permissions)
        // Asked once on open; read again on every return to the app, so permissions granted in Settings count at once
        // (reading, not asking: the system's dialog itself pauses and resumes the app).
        LaunchedEffect(v) { permissions(true) }
        LifecycleResumeEffect(v) {
            permissions(false)
            onPauseOrDispose {}
        }
        if (videoState?.live == true) KeepScreenOn()
    }
    viewModel.notes.labels =
        SoapLabels(
            tr(Res.string.soapSubjective),
            tr(Res.string.soapObjective),
            tr(Res.string.soapAssessment),
            tr(Res.string.soapPlan),
        )
    viewModel.endLabels = rememberEndLabels()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(state.left) {
        val to = state.left ?: return@LaunchedEffect
        val patient = state.visit?.patientId
        viewModel.leaving()
        when (to) {
            VisitExit.Back -> onBack()
            VisitExit.Patient -> patient?.let(onOpenPatient)
            VisitExit.Form -> patient?.let { onOpenForm(it, state.icd?.let { s -> Icd10Hit(s.code, s.title) }) }
        }
    }
    val scribe = rememberDictation()
    val scribeState by scribe.state.collectAsStateWithLifecycle()
    // The AI scribe's recorder lives in this screen: whatever leaves it (or ends the visit) while it records asks
    // first, instead of dropping the recording without a word.
    var afterRecording by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guarded = { action: () -> Unit ->
        when {
            // Recording: nothing is sent yet, so leaving discards it; ask.
            scribeState.field != null && !scribeState.transcribing -> afterRecording = action
            // ✓ was tapped and the recorder is handing the audio over (a moment): it would be lost with the screen.
            scribeState.transcribing && !state.scribeUploading -> Unit
            // Uploading: that runs in the ViewModel and survives leaving; End itself waits for it (openEnd).
            else -> action()
        }
    }
    val back = {
        when {
            state.focus -> viewModel.ui { it.copy(focus = false) }
            // Back in a call asks to leave the call first, like the red button (flow 05/11).
            videoState?.live == true -> viewModel.video?.askLeave(true)
            else -> guarded { viewModel.leave() }
        }
        Unit
    }
    BackHandler(onBack = back)
    val patientId = state.visit?.patientId
    val scribeAllowed = rememberAllowed(DoctorFeature.AiNotes)
    // AI-10: the scribe's facts, with the AI notes plan (the scribe makes them).
    val facts: (@Composable () -> Unit)? =
        if (scribeAllowed) {
            {
                VisitFactsPanel(
                    appointmentId,
                    state.awaitingScribe,
                    viewModel.notes::flush,
                    viewModel.notes::appendSaved,
                )
            }
        } else {
            null
        }
    val askScribe = rememberMicPermission { granted -> if (granted) scribe.start(SCRIBE) else scribe.denied() }
    // APPT-05: the answer goes to the kind the picker was opened for (VisitViewModel.pickingPhoto).
    val gallery = rememberImagePickerLauncher(viewModel::addPhoto)
    val camera =
        rememberCameraLauncher(viewModel::addPhoto) {
            viewModel.addPhoto(null)
            viewModel.ui { it.copy(cameraDenied = true) }
        }
    val pick = { source: () -> Unit ->
        viewModel.pickingPhoto()
        source()
    }
    val actions =
        VisitActions(
            back = back,
            ui = viewModel::ui,
            retry = viewModel::load,
            mode = viewModel::mode,
            helper = viewModel::helper,
            addToNotes = viewModel::addToNotes,
            edit = viewModel.notes::edit,
            retryNotes = viewModel.notes::load,
            confirmDraft = viewModel.notes::confirm,
            discardDraft = viewModel.notes::discard,
            dismissNotesBanner = viewModel.notes::dismissBanner,
            requestSignature = viewModel::requestSignature,
            openPatient = { if (patientId != null) guarded { viewModel.leave(VisitExit.Patient) } },
            openForm = { if (patientId != null) guarded { viewModel.leave(VisitExit.Form) } },
            startScribe = askScribe,
            cancelScribe = scribe::cancel,
            finishScribe = { scribe.finishWith { audio, id -> viewModel.uploadScribe(audio, id) } },
            retryScribe = viewModel::retryScribe,
            dismissScribe = scribe::dismiss,
            openEnd = { guarded(viewModel::openEnd) },
            retrySummary = viewModel::retrySummary,
            applyIcd = viewModel::applyIcd,
            clearIcd = { viewModel.ui { it.copy(icd = null) } },
            dental =
                DentalActions(
                    retry = viewModel.dentalRecord::load,
                    open = { t -> viewModel.ui { it.copy(dentalTooth = t) } },
                    edit = viewModel.dentalRecord::edit,
                    dentition = viewModel.dentalRecord::dentition,
                    discount = viewModel.dentalRecord::discount,
                    add = viewModel.dentalRecord::add,
                    remove = viewModel.dentalRecord::remove,
                    retryServices = viewModel.dentalRecord::loadServices,
                ),
            video =
                viewModel.video?.let { v ->
                    VideoActions(
                        join = v::join,
                        camera = v::toggleCamera,
                        mic = v::toggleMic,
                        flip = v::flip,
                        askLeave = v::askLeave,
                        presence = v::watchPresence,
                        noShow = { viewModel.askNoShow(true) },
                        leave = v::leave,
                        dismissError = v::dismissError,
                    )
                } ?: VideoActions(),
            plan =
                PlanActions(
                    select = viewModel.plan::select,
                    retry = viewModel.plan::retry,
                    open = { k -> if (k == null) viewModel.plan.open(null) else viewModel.openPlanTooth(k) },
                    toggle = viewModel.plan::toggle,
                    amount = viewModel.plan::amount,
                    method = viewModel.plan::method,
                    toDental = { viewModel.mode(VisitMode.Dental) },
                ),
            photo = viewModel::photo,
            takePhoto = { pick(camera) },
            choosePhoto = { pick(gallery) },
            removePhoto = viewModel::removePhoto,
        )
    // Dictation for the notes fields (AI-06); the AI scribe (SCRIBE-01) has its own recorder, its state passed down.
    CompositionLocalProvider(LocalDictation provides rememberDictation()) {
        // VIDEO-02: a video visit starts on the pre-join page, then the workspace with the call on top. Drawn in this
        // flow, so the sheets below (leaving with unsaved work) still show over it.
        if (callPart?.state?.stage == VideoStage.PreJoin && state.visit != null) {
            PreJoin(state, callPart.state, callPart.call, scheduleOf(state), actions)
        } else if (patientId == null) {
            VisitContent(
                state,
                notes,
                actions,
                scribe = scribeState,
                scribeAllowed = scribeAllowed,
                dental = dental,
                plan = planState,
                video = callPart,
                facts = facts,
            )
        } else {
            // The documents and uploads need the patient: they start once the visit says who it is.
            PatientParts(
                patientId,
                state,
                notes,
                dental,
                planState,
                actions,
                { patient, document, title -> guarded { onOpenDocument(patient, document, title) } },
                scribeState,
                scribeAllowed,
                callPart,
                facts,
            )
        }
        if (videoState?.confirmLeave == true) {
            ProfileSheet(
                { actions.video.askLeave(false) },
            ) { LeaveCall(actions.video) }
        }
        state.dentalTooth?.let { tooth ->
            ProfileSheet({ actions.dental.open(null) }) { DentalServicesSheet(tooth, dental, actions.dental) }
        }
        planState.sheet?.let {
                key ->
            ProfileSheet({ actions.plan.open(null) }) { PlanFulfilSheet(key, planState, actions.plan) }
        }
    }
    if (state.askNoShow) {
        NoShowQuestion(
            notify = state.notifyNoShow,
            busy = state.markingNoShow,
            onNotify = viewModel::notifyNoShow,
            onConfirm = viewModel::noShow,
            onClose = { viewModel.askNoShow(false) },
        )
    }
    afterRecording?.let { action ->
        val close = { afterRecording = null }
        ProfileSheet(close) {
            ShifaDialogSheet(
                tr(Res.string.intelDiscardRecording),
                tr(Res.string.discard),
                {
                    close()
                    scribe.cancel()
                    action()
                },
                tone = DialogTone.Destructive,
                cancelText = tr(Res.string.keepRecording),
                onCancel = close,
            )
        }
    }
    state.askLeave?.let { to ->
        // Photos, ticks or a payment would be lost (APPT-05, DENTAL-03); otherwise the notes couldn't be saved.
        val photos = state.photos.isNotEmpty() || planState.hasPending
        ProfileSheet({ viewModel.ui { it.copy(askLeave = null) } }) {
            ShifaDialogSheet(
                tr(if (photos) Res.string.leaveUnsavedTitle else Res.string.notesNotSavedTitle),
                tr(Res.string.leaveAnyway),
                { viewModel.leave(to, force = true) },
                body = tr(if (photos) Res.string.leaveUnsavedBody else Res.string.notesNotSavedBody),
                cancelText = tr(Res.string.stay),
                onCancel = { viewModel.ui { it.copy(askLeave = null) } },
            )
        }
    }
    if (state.menu) NotesMenu(state, actions)
    state.photoSheet?.let {
        val close = { viewModel.ui { s -> s.copy(photoSheet = null) } }
        ProfileSheet(close) { PhotoSource(it, actions, close) }
    }
    val end = state.end
    if ((end.confirm || end.running || end.completed || end.error != null) && !end.hidden) {
        val clock = remember { PracticeClock(profileRepository.me.value?.profile?.timeZone) }
        val visit = state.visit
        val schedule = scheduleOf(state, clock)
        val minutes = visit?.start?.let { ((currentInstant() - it).inWholeMinutes).toInt().coerceAtLeast(0) } ?: 0
        val doneText = end.noSummary ?: Res.string.appointmentEndedDocumentationSaved
        val title = end.summary?.let { documentTitle(it) }
        EndSheet(
            state,
            notes,
            dental.takeIf { state.dental },
            schedule,
            minutes,
            EndActions(
                end = viewModel::end,
                close = viewModel::closeEnd,
                skipScribe = viewModel::skipScribe,
                retrySummary = viewModel::retrySummary,
                finish = {
                    ShellNotice.pending.value =
                        if (end.summaryFailed) Notice(Res.string.summaryNotSaved) else Notice(doneText, success = true)
                    viewModel.leave(force = true)
                },
                openSummary = { end.summary?.let { d -> patientId?.let { onOpenDocument(it, d, title.orEmpty()) } } },
                // The slot picker over the visit (the End sheet steps aside; its notice is posted with Done).
                followUp =
                    patientId?.let {
                        {
                            viewModel.ui { s -> s.copy(end = s.end.copy(hidden = true)) }
                            followUp = currentInstant().toEpochMilliseconds()
                        }
                    },
            ),
            planState,
        )
    }
    followUp?.let { opened ->
        val visit = state.visit
        val patientId = visit?.patientId
        // The done step comes back when the picker closes, and is there on return from the booking (Done, Open
        // Summary, Book follow-up again).
        val back = {
            followUp = null
            viewModel.ui { s -> s.copy(end = s.end.copy(hidden = false)) }
        }
        SlotPickSheet(opened, { slot ->
            back()
            if (patientId != null) onBook(slot, PatientOption(patientId, visit.patientName, state.patient?.photoUrl))
        }, back)
    }
    state.patient?.chronic?.takeIf { state.chronicWarning }?.let {
        ChronicWarning(chronicLabel(it)) { viewModel.ui { s -> s.copy(chronicWarning = false) } }
    }
}

@Composable
private fun PatientParts(
    patientId: Long,
    state: VisitUiState,
    notes: VisitNotesState,
    dental: DentalNotesState,
    plan: VisitPlanState,
    actions: VisitActions,
    onOpenDocument: (Long, PatientDocument, String) -> Unit,
    scribe: DictationState,
    scribeAllowed: Boolean,
    video: VideoPart?,
    facts: (@Composable () -> Unit)?,
    documents: PatientDocumentsViewModel =
        viewModel(key = "visit-documents-$patientId") { PatientDocumentsViewModel(patientId) },
    uploads: DocumentUploadViewModel =
        viewModel(key = "visit-upload-$patientId") { DocumentUploadViewModel(patientId) },
) {
    val docs by documents.state.collectAsStateWithLifecycle()
    val upload by uploads.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { documents.load() }
    LaunchedEffect(upload.uploaded) { if (upload.uploaded > 0) documents.load() }
    var opening by remember { mutableStateOf<PatientDocument?>(null) }
    opening?.let { d ->
        val title = documentTitle(d)
        LaunchedEffect(d) {
            opening = null
            onOpenDocument(patientId, d, title)
        }
    }
    val sources = rememberUploadSources(uploads)
    VisitContent(
        state,
        notes,
        VisitActions(
            back = actions.back,
            ui = actions.ui,
            retry = actions.retry,
            mode = actions.mode,
            helper = actions.helper,
            addToNotes = actions.addToNotes,
            edit = actions.edit,
            retryNotes = actions.retryNotes,
            confirmDraft = actions.confirmDraft,
            discardDraft = actions.discardDraft,
            dismissNotesBanner = actions.dismissNotesBanner,
            requestSignature = actions.requestSignature,
            openPatient = actions.openPatient,
            openForm = actions.openForm,
            startScribe = actions.startScribe,
            cancelScribe = actions.cancelScribe,
            finishScribe = actions.finishScribe,
            retryScribe = actions.retryScribe,
            dismissScribe = actions.dismissScribe,
            openEnd = actions.openEnd,
            retrySummary = actions.retrySummary,
            applyIcd = actions.applyIcd,
            clearIcd = actions.clearIcd,
            dental = actions.dental,
            video = actions.video,
            plan = actions.plan,
            photo = actions.photo,
            takePhoto = actions.takePhoto,
            choosePhoto = actions.choosePhoto,
            removePhoto = actions.removePhoto,
            documents =
                DocumentsActions(
                    retry = documents::load,
                    open = { opening = it },
                    requestAccess = documents::requestAccess,
                    ui = documents::ui,
                    upload = { uploads.ui { it.copy(options = true) } },
                    cancelUpload = uploads::cancelUpload,
                    retryUpload = uploads::retry,
                    dismissUpload = { uploads.ui { it.copy(banner = null) } },
                    edit = documents::startEdit,
                ),
        ),
        docs,
        upload,
        scribe = scribe,
        scribeAllowed = scribeAllowed,
        dental = dental,
        plan = plan,
        video = video,
        facts = facts,
    )
    UploadSheets(upload, uploads, sources)
    DocumentEditSheets(docs.edit, documents)
}

@Composable
internal fun VisitContent(
    state: VisitUiState,
    notes: VisitNotesState,
    actions: VisitActions,
    documents: shifa.doctor.ui.documents.PatientDocumentsUiState = shifa.doctor.ui.documents.PatientDocumentsUiState(),
    upload: UploadUiState = UploadUiState(),
    clock: PracticeClock = PracticeClock(profileRepository.me.value?.profile?.timeZone),
    now: () -> Instant = ::currentInstant,
    // The AI scribe's recording (SCRIBE-01): its state and whether the plan has AI notes; null: not offered.
    scribe: DictationState? = null,
    scribeAllowed: Boolean = false,
    dental: DentalNotesState = DentalNotesState(),
    plan: VisitPlanState = VisitPlanState(),
    // VIDEO-02: the call's state and the call (for its video tiles); null for a visit in person.
    video: VideoPart? = null,
    // AI-10: the visit facts panel (draws nothing until the scribe made facts); null without the AI notes plan.
    facts: (@Composable () -> Unit)? = null,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    val visit = state.visit
    when {
        visit == null && state.error != null ->
            Gone(
                if (state.notFound) tr(Res.string.appointmentGone) else tr(state.error.text),
                state.error.requestId,
                actions,
            )
        visit == null -> {
            ShifaSubHeader("", Modifier.statusBarsPadding(), onBack = actions.back)
            Column(Modifier.padding(ShifaSpacing.Inset.Md), verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
                repeat(4) { ShifaSkeleton(Modifier.fillMaxWidth().size(ShifaSize.S80)) }
            }
        }
        state.focus -> FocusMode(notes, actions)
        else -> {
            Header(state, actions, clock, now)
            // Dictation's messages (AI-06) join the visit's and the notes'.
            val dictation = LocalDictation.current
            val heard = dictation?.state?.collectAsStateWithLifecycle()?.value?.banner
            val scribed = scribe?.banner
            val gap = Modifier.padding(horizontal = ShifaSpacing.Inset.Md)
            LazyColumn(
                Modifier.weight(1f).imePadding(),
                contentPadding = PaddingValues(vertical = ShifaSpacing.Inset.Md),
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
            ) {
                item { Box(gap) { Chips(state, actions) } }
                video?.let { v ->
                    item { Box(gap) { VideoPane(visit.patientName.orEmpty(), v.state, v.call, actions.video, now) } }
                }
                listOfNotNull(state.banner, notes.banner, heard, scribed).forEach { b ->
                    item {
                        Box(gap) {
                            codeBanner(b) {
                                actions.ui { it.copy(banner = null) }
                                actions.dismissNotesBanner()
                                dictation?.dismiss()
                                actions.dismissScribe()
                            }?.invoke()
                        }
                    }
                }
                facts?.let { panel -> item { Box(gap) { panel() } } }
                item { Box(gap) { NotesCard(state, notes, dental, plan, actions, scribe, scribeAllowed) } }
                documentRows(documents, upload, actions.documents, gap, hasPatient = visit.patientId != null)
            }
            Footer(state, actions)
        }
    }
}

@Composable
private fun Header(
    state: VisitUiState,
    actions: VisitActions,
    clock: PracticeClock,
    now: () -> Instant,
) {
    val visit = state.visit ?: return
    val patient = state.patient
    val name = visit.patientName ?: patient?.name.orEmpty()
    // The visit's clock, every second: time since the start, or until it.
    var tick by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            tick = now()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface)
            .statusBarsPadding()
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S8),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            ShifaIconButton(
                Res.drawable.ic_chev_l_16,
                tr(Res.string.back),
                actions.back,
                variant = ShifaIconButtonVariant.Secondary,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) {
                    Text(
                        name,
                        Modifier.weight(1f, fill = false),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    status(visit.status)?.let { (label, bg, ink) -> ProfileChip(label, bg, ink) }
                }
                val today = clock.local(now()).date
                listOfNotNull(
                    patient?.age(today)?.let { tr(Res.string.patientAgeYears, it) },
                    patient?.let { "ID #${it.id}" },
                ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                }
                val start = visit.start
                val end = visit.end
                if (start != null && end != null) {
                    Text(
                        listOfNotNull(
                            "${displayTime(clock.local(start).time)} – ${displayTime(clock.local(end).time)}",
                            state.reason,
                            if (state.kind == VisitKind.Video) tr(Res.string.videoCall) else visit.location,
                        ).joinToString(" · "),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            visit.start?.let { start ->
                val elapsed = tick - start
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        tr(if (elapsed.isNegative()) Res.string.startsIn else Res.string.visitTime),
                        style = ShifaTextStyles.caption(),
                        color = ShifaColors.Text.Secondary,
                    )
                    Text(
                        clockText(elapsed.absoluteValue),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Primary,
                    )
                }
            }
        }
    }
}

/** "mm:ss", or "h:mm:ss" from an hour. */
internal fun clockText(d: Duration): String {
    val total = d.inWholeSeconds
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60

    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

/** Allergy, chronic disease, last visit (board `DaVisit` patient summary); tapping opens the patient. */
@Composable
private fun Chips(
    state: VisitUiState,
    actions: VisitActions,
) {
    val patient = state.patient ?: return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        @Composable
        fun chip(
            text: String,
            bg: Color,
            ink: Color,
        ) = Box(Modifier.clickable(onClick = actions.openPatient)) { ProfileChip(text, bg, ink) }
        patient.allergies?.takeIf { it.isNotBlank() }?.let {
            chip(tr(Res.string.allergyChip, it.trim()), ShifaColors.Bg.ErrorSubtle, ShifaColors.Text.Error)
        }
        patient.chronic?.let { chip(chronicLabel(it), ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning) }
        chip(
            state.lastVisit?.let {
                tr(
                    Res.string.lastVisitOn,
                    displayDate(PracticeClock(profileRepository.me.value?.profile?.timeZone).local(it).date),
                )
            }
                ?: tr(Res.string.firstVisit),
            ShifaColors.Bg.Surface,
            ShifaColors.Text.Secondary,
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        content = content,
    )

@Composable
private fun NotesCard(
    state: VisitUiState,
    notes: VisitNotesState,
    dental: DentalNotesState,
    plan: VisitPlanState,
    actions: VisitActions,
    scribe: DictationState?,
    scribeAllowed: Boolean,
) = Card {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        Text(
            tr(
                when (state.mode) {
                    VisitMode.General -> Res.string.notes
                    VisitMode.Dental -> Res.string.docModeDental
                    VisitMode.Form0252 -> Res.string.form0252
                },
            ),
            Modifier.weight(1f),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
        )
        if (state.mode == VisitMode.General) {
            ShifaIconButton(
                Res.drawable.ic_eye_16,
                tr(Res.string.focusMode),
                { actions.ui { it.copy(focus = true) } },
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
        ShifaIconButton(
            Res.drawable.ic_menu_16,
            tr(Res.string.notes),
            { actions.ui { it.copy(menu = true) } },
            variant = ShifaIconButtonVariant.Secondary,
        )
    }
    val modes = VisitMode.entries.filter { it != VisitMode.Dental || state.dental }
    SegmentedTabs(
        modes.map { TabItem(tr(modeLabel(it))) },
        modes.indexOf(state.mode).coerceAtLeast(0),
        { actions.mode(modes[it]) },
        block = true,
    )
    // DENTAL-03: the patient's treatment plan, while the visit is open.
    if (state.visit?.completed != true) PlanPanel(plan, state.mode == VisitMode.Dental, state.dental, actions.plan)
    when (state.helper) {
        VisitHelper.Ai -> AiHelper(state, notes, actions)
        VisitHelper.Form -> FormHelper(state.forms.firstOrNull(), actions)
        null -> Unit
    }
    when (state.mode) {
        VisitMode.General -> General(state, notes, actions, scribe, scribeAllowed)
        VisitMode.Dental -> DentalPanel(dental, state.dentalTooth, actions.dental, plan, actions.plan)
        VisitMode.Form0252 -> Form0252(state.forms, state.visit?.patientId != null, state.icd, actions)
    }
    if (state.cameraDenied) CameraDeniedBanner { actions.ui { it.copy(cameraDenied = false) } }
    if (state.mode != VisitMode.Form0252) Photos(state.photos, actions)
}

/** APPT-05: the photos taken, "Before Treatment · IMG_0012.jpg", each removable (board `DaVisit` photo chips). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Photos(
    photos: List<VisitPhoto>,
    actions: VisitActions,
) {
    if (photos.isEmpty()) return
    val before = tr(Res.string.beforeTreatment)
    val after = tr(Res.string.afterTreatment)
    val remove = tr(Res.string.removePhoto)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
    ) {
        photos.forEachIndexed { i, p ->
            Row(
                Modifier
                    .height(ShifaSize.S30)
                    .background(ShifaColors.Bg.Canvas, ShifaRadius.Full)
                    .padding(start = ShifaSize.S10, end = ShifaSize.S6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                Text(
                    "${if (p.kind == PhotoKind.Before) before else after} · ${p.name}",
                    Modifier.widthIn(max = ShifaSize.S240),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painterResource(Res.drawable.ic_x_16),
                    remove,
                    Modifier.clip(ShifaRadius.Full).clickable(role = Role.Button) { actions.removePhoto(i) },
                    tint = ShifaColors.Icon.Secondary,
                )
            }
        }
    }
}

/** APPT-05: Camera or Gallery for a photo kind (board `DaVisit` "photo source"). */
@Composable
internal fun PhotoSource(
    kind: PhotoKind,
    actions: VisitActions,
    close: () -> Unit,
) = ShifaTray(tr(if (kind == PhotoKind.Before) Res.string.beforeTreatment else Res.string.afterTreatment), close) {
    Text(
        tr(Res.string.photoSummaryHint),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    ShifaSettingsItem(tr(Res.string.takePhoto), actions.takePhoto, icon = Res.drawable.ic_camera_22)
    ShifaSettingsItem(
        tr(Res.string.chooseFromGallery),
        actions.choosePhoto,
        icon = Res.drawable.ic_image_22,
        divider = false,
    )
}

private fun modeLabel(m: VisitMode) =
    when (m) {
        VisitMode.General -> Res.string.docModeGeneral
        VisitMode.Dental -> Res.string.docModeDental
        VisitMode.Form0252 -> Res.string.docMode0252
    }

@Composable
private fun General(
    state: VisitUiState,
    notes: VisitNotesState,
    actions: VisitActions,
    scribe: DictationState?,
    scribeAllowed: Boolean,
) {
    val text = notes.text
    // A SOAP field recording keeps the section open (flow 05/05 §8: its pill would go with it).
    val recordingSoap =
        LocalDictation.current?.state?.collectAsStateWithLifecycle()?.value?.field?.startsWith("soap-") == true
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !recordingSoap) { actions.ui { it.copy(soapOpen = !it.soapOpen) } }
                .padding(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(Res.string.soapNotesSectionTitle),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    tr(Res.string.soapNotesSectionSubtitle),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            Icon(painterResource(Res.drawable.ic_chev_d_16), null, tint = ShifaColors.Icon.Secondary)
        }
        if (state.soapOpen) {
            Column(Modifier.padding(ShifaSize.S12), verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                listOf<Triple<StringResource, (SoapNotes) -> String, (SoapNotes, String) -> SoapNotes>>(
                    Triple(Res.string.soapSubjective, { it.subjective }) { n, v -> n.copy(subjective = v) },
                    Triple(Res.string.soapObjective, { it.objective }) { n, v -> n.copy(objective = v) },
                    Triple(Res.string.soapAssessment, { it.assessment }) { n, v -> n.copy(assessment = v) },
                    Triple(Res.string.soapPlan, { it.plan }) { n, v -> n.copy(plan = v) },
                ).forEach { (label, get, set) ->
                    SpeechOrPlainField(
                        get(text),
                        { v -> actions.edit { set(it, v) } },
                        tr(label),
                        "soap-${label.key}",
                        { t -> actions.edit { set(it, appendTranscript(get(it), t)) } },
                        Modifier.fillMaxWidth(),
                        multiline = true,
                        areaMinHeight = ShifaSize.S56,
                    )
                }
            }
        }
    }
    SpeechOrPlainField(
        text.free,
        { v -> actions.edit { it.copy(free = v) } },
        tr(Res.string.enterNotes),
        "free",
        { t -> actions.edit { it.copy(free = appendTranscript(it.free, t)) } },
        Modifier.fillMaxWidth(),
        multiline = true,
    )
    scribe?.let { ScribeRecorder(state, it, scribeAllowed, actions) }
    notes.saveError?.let { codeBanner(it)?.invoke() }
    notes.loadError?.let {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            codeBanner(it)?.invoke()
            ShifaLink(tr(Res.string.retry), actions.retryNotes)
        }
    }
}

@Composable
private fun AiHelper(
    state: VisitUiState,
    notes: VisitNotesState,
    actions: VisitActions,
) = HelperBox(tr(Res.string.fromShifaAi), Res.drawable.ic_sparkles_22, actions) {
    notes.drafts.orEmpty().forEach { d ->
        Column(
            Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd).padding(ShifaSize.S10),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            ProfileChip(tr(Res.string.draft), ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning)
            Text(stripScribeTranscript(d.body), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
            if (d.icdSuggestions.isNotEmpty()) {
                Text(
                    tr(Res.string.aiSuggestedDiagnoses),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
                d.icdSuggestions.forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    ) {
                        Text(
                            "${c.code} ${c.title}",
                            Modifier.weight(1f),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Primary,
                        )
                        if (c.isTop) {
                            ProfileChip(
                                tr(Res.string.recommended),
                                ShifaColors.Bg.SuccessSubtle,
                                ShifaColors.Text.Primary,
                            )
                        }
                        ShifaLink(tr(Res.string.apply), { actions.applyIcd(c) })
                    }
                }
            }
            val busy = d.id in notes.busy
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
                ShifaSecondaryButton(tr(Res.string.discard), {
                    actions.discardDraft(d)
                }, enabled = !busy, size = ShifaButtonSize.Compact)
                ShifaPrimaryButton(tr(Res.string.saveAsNote), {
                    actions.confirmDraft(d)
                }, enabled = !busy, size = ShifaButtonSize.Compact)
            }
        }
    }
    val saved = notes.notes.orEmpty().filter { it.fromAi }
    if (saved.isNotEmpty()) {
        val i = state.savedIndex.coerceIn(saved.indices)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            Text(
                tr(Res.string.aiNote),
                Modifier.weight(1f),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            if (saved.size > 1) {
                ShifaIconButton(Res.drawable.ic_chev_l_16, null, {
                    actions.ui { it.copy(savedIndex = (i - 1 + saved.size) % saved.size) }
                }, variant = ShifaIconButtonVariant.Secondary)
                Text("${i + 1}/${saved.size}", style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                ShifaIconButton(Res.drawable.ic_chev_r_16, null, {
                    actions.ui { it.copy(savedIndex = (i + 1) % saved.size) }
                }, variant = ShifaIconButtonVariant.Secondary)
            }
            ShifaIconButton(
                Res.drawable.ic_plus_16,
                tr(Res.string.addToNotes),
                { actions.addToNotes(saved[i].displayText) },
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
        Text(saved[i].displayText, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
    }
}

@Composable
private fun FormHelper(
    form: PatientForm?,
    actions: VisitActions,
) = HelperBox(tr(Res.string.lastForm0252), Res.drawable.ic_clipboard_22, actions) {
    form ?: return@HelperBox
    listOf(
        Res.string.complaints to form.complaints,
        Res.string.diagnosis to listOfNotNull(form.diagnosisCode, form.diagnosis).joinToString(" ").ifBlank { null },
        Res.string.treatment to form.treatment,
    ).forEach { (label, value) ->
        val text = value?.trim()?.ifEmpty { null } ?: return@forEach
        val labelText = tr(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            Column(Modifier.weight(1f)) {
                Text(labelText, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
                Text(text, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
            }
            ShifaIconButton(
                Res.drawable.ic_plus_16,
                tr(Res.string.addToNotes),
                { actions.addToNotes("$labelText: $text") },
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun HelperBox(
    title: String,
    icon: DrawableResource,
    actions: VisitActions,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S10),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        Icon(painterResource(icon), null, Modifier.size(ShifaSize.S16), tint = ShifaColors.Icon.Brand)
        Text(title, Modifier.weight(1f), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Brand)
        ShifaIconButton(Res.drawable.ic_x_16, tr(Res.string.close), {
            actions.ui { it.copy(helper = null) }
        }, variant = ShifaIconButtonVariant.Secondary)
    }
    content()
}

@Composable
private fun Form0252(
    forms: List<PatientForm>,
    hasPatient: Boolean,
    icd: IcdSuggestion?,
    actions: VisitActions,
) {
    Text(tr(Res.string.form0252VisitHint), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
    // The applied AI code goes into the new form's ICD-10 (APPT-08); × drops it.
    icd?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            Text(
                tr(Res.string.icdToApply, it.code, it.title),
                Modifier.weight(1f),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Brand,
            )
            ShifaIconButton(
                Res.drawable.ic_x_16,
                tr(Res.string.close),
                actions.clearIcd,
                variant = ShifaIconButtonVariant.Secondary,
            )
        }
    }
    forms.forEach { f ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text(
                tr(Res.string.formListEntry, tr(Res.string.form0252), f.day?.let(::displayDate) ?: f.date),
                Modifier.weight(1f),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(if (f.signed) Res.string.formSignedBadge else Res.string.formNotSignedYet),
                style = ShifaTextStyles.caption(),
                color = if (f.signed) ShifaColors.Text.Brand else ShifaColors.Text.Secondary,
            )
        }
    }
    if (hasPatient) ShifaPrimaryButton(tr(Res.string.openForm0252), actions.openForm, Modifier.fillMaxWidth())
}

/** The patient's documents grouped as the board shows them, then the upload row (05 §2.1). */
private fun LazyListScope.documentRows(
    state: shifa.doctor.ui.documents.PatientDocumentsUiState,
    upload: UploadUiState,
    actions: DocumentsActions,
    gap: Modifier,
    hasPatient: Boolean,
) {
    item { Text(tr(Res.string.documents), gap, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary) }
    // A visit without a patient (flow 05/04 §8): no documents to read, nothing to upload to.
    if (!hasPatient) {
        return item {
            Text(
                tr(Res.string.patientRecordUnavailable),
                gap,
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
    state.banner?.let { b -> item { Box(gap) { codeBanner(b) { actions.ui { it.copy(banner = null) } }?.invoke() } } }
    val docs = state.documents
    when {
        state.error != null ->
            item {
                ShifaLoadError(
                    tr(state.error.text),
                    actions.retry,
                    gap,
                    requestId = state.error.requestId,
                    compact = true,
                )
            }
        docs == null -> item { Box(gap) { ShifaSkeleton(Modifier.fillMaxWidth().size(ShifaSize.S64)) } }
        docs.isEmpty() ->
            item {
                Text(
                    tr(Res.string.noDocumentsYet),
                    gap,
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        else ->
            docs.groupBy(DocBucket::of).toSortedMapByOrdinal().forEach { (bucket, list) ->
                item(key = "bucket-$bucket") {
                    Text(tr(bucket.label), gap, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
                }
                items(
                    list,
                    key = { "visit-doc-${it.id}" },
                ) { d -> Box(gap) { DocumentCard(d, d.id in state.requesting, actions) } }
            }
    }
    item {
        Column(gap, verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
            ShifaSecondaryButton(
                tr(Res.string.upload),
                actions.upload,
                Modifier.fillMaxWidth(),
                icon = Res.drawable.ic_upload_16,
                enabled = upload.uploading == null,
            )
            Text(
                tr(Res.string.consultationUploadHint),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
            UploadStatus(upload, actions.cancelUpload, actions.retryUpload, actions.dismissUpload)
            Text(
                tr(Res.string.documentsFinalizeHint),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
}

private fun <V> Map<DocBucket, V>.toSortedMapByOrdinal(): List<Pair<DocBucket, V>> =
    entries.sortedBy {
        it.key.ordinal
    }.map { it.key to it.value }

/** The signature state and Request signature (board `DaVisit` action bar); End appointment joins with APPT-07. */
@Composable
private fun Footer(
    state: VisitUiState,
    actions: VisitActions,
) = Column(Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface).navigationBarsPadding()) {
    HorizontalDivider(color = ShifaColors.Border.Default)
    Row(
        Modifier.fillMaxWidth().padding(ShifaSpacing.Inset.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        // End appointment (APPT-07) for a visit still open; the signature state or request beside it.
        if (state.visit?.completed != true) {
            ShifaPrimaryButton(tr(Res.string.endAppointment), actions.openEnd, Modifier.weight(1f), destructive = true)
        } else if (state.end.summaryFailed) {
            // Completed, but its summary wasn't saved: try again from here (flow 05/12 §6).
            ShifaPrimaryButton(tr(Res.string.retrySummary), actions.retrySummary, Modifier.weight(1f))
        }
        when (state.signature) {
            SignatureState.Signed ->
                Text(
                    tr(Res.string.patientSigned),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Brand,
                )
            SignatureState.Waiting ->
                Text(
                    tr(Res.string.waitingForPatientSignature),
                    Modifier.weight(1f),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Secondary,
                )
            SignatureState.None ->
                ShifaSecondaryButton(
                    tr(Res.string.requestSignature),
                    actions.requestSignature,
                    Modifier.weight(1f),
                    loading = state.signing,
                    enabled = !state.signing,
                )
        }
    }
}

/** The free notes alone, full screen (flow 05/05 "focus mode"); the same text, saved the same way. */
@Composable
private fun FocusMode(
    notes: VisitNotesState,
    actions: VisitActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Surface)) {
    ShifaSubHeader(tr(Res.string.consultationFocusModeTitle), Modifier.statusBarsPadding(), onBack = actions.back)
    // Dictation's messages show here too (focus mode hides the workspace's banners).
    val dictation = LocalDictation.current
    dictation?.state?.collectAsStateWithLifecycle()?.value?.banner?.let { b ->
        Box(Modifier.padding(horizontal = ShifaSpacing.Inset.Md)) { codeBanner(b) { dictation.dismiss() }?.invoke() }
    }
    SpeechOrPlainField(
        notes.text.free,
        { v -> actions.edit { it.copy(free = v) } },
        tr(Res.string.enterNotes),
        "free",
        { t -> actions.edit { it.copy(free = appendTranscript(it.free, t)) } },
        Modifier.fillMaxWidth().weight(1f).padding(ShifaSpacing.Inset.Md).imePadding(),
        multiline = true,
    )
}

@Composable
private fun Gone(
    message: String,
    requestId: String?,
    actions: VisitActions,
) = Column(Modifier.fillMaxSize()) {
    ShifaSubHeader("", Modifier.statusBarsPadding(), onBack = actions.back)
    EmptyState(
        tr(Res.string.appointmentLoadFailed),
        Res.drawable.ic_alert_16,
        Modifier.weight(1f),
        body = listOfNotNull(message, requestId).joinToString("\n"),
    ) {
        ShifaSecondaryButton(tr(Res.string.retry), actions.retry)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StaffCannotRunVisits(onBack: () -> Unit) =
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        BackHandler(onBack = onBack)
        ShifaSubHeader("", Modifier.statusBarsPadding(), onBack = onBack)
        EmptyState(
            tr(Res.string.staffVisitsTitle),
            Res.drawable.ic_users_22,
            Modifier.weight(1f),
            body = tr(Res.string.staffVisitsBody),
        )
    }

/** The notes menu (board `DaVisit` "Notes"): the helpers, then the modes. Photos join with APPT-05. */
@Composable
private fun NotesMenu(
    state: VisitUiState,
    actions: VisitActions,
) {
    val close = { actions.ui { it.copy(menu = false) } }
    ProfileSheet(close) {
        ShifaTray(tr(Res.string.notes), close) {
            ShifaSettingsItem(
                tr(Res.string.fromShifaAi),
                { actions.helper(VisitHelper.Ai) },
                icon = Res.drawable.ic_sparkles_22,
            )
            ShifaSettingsItem(tr(Res.string.fromLast0252Form), {
                actions.helper(VisitHelper.Form)
            }, icon = Res.drawable.ic_clipboard_22)
            // APPT-05: before/after photos (General and Dental modes, as on the board).
            if (state.mode != VisitMode.Form0252) {
                ShifaSettingsItem(
                    tr(Res.string.beforeTreatment),
                    { actions.photo(PhotoKind.Before) },
                    icon = Res.drawable.ic_camera_22,
                )
                ShifaSettingsItem(
                    tr(Res.string.afterTreatment),
                    { actions.photo(PhotoKind.After) },
                    icon = Res.drawable.ic_camera_22,
                )
            }
            VisitMode.entries.filter { it != VisitMode.Dental || state.dental }.forEach { m ->
                ShifaSettingsItem(
                    tr(modeLabel(m)),
                    { actions.mode(m) },
                    selected = m == state.mode,
                    divider = m != VisitMode.Form0252,
                )
            }
        }
    }
}

/**
 * Start AI Notes under the free notes (flow 05/06, board `DaVisit` "AI scribe"): PRO only (`AI_NOTES`; without it the
 * button shows locked), the recording pill with "Recording for AI Notes" and its hint, ✓ uploads (a spinner meanwhile),
 * then "Start AI Notes again". A failed upload offers to send the kept recording again; nothing new starts while a
 * recording is on its way.
 */
@Composable
private fun ScribeRecorder(
    state: VisitUiState,
    rec: DictationState,
    allowed: Boolean,
    actions: VisitActions,
) {
    when {
        !allowed ->
            ShifaSecondaryButton(tr(Res.string.aiNotesNeedPro), {
            }, Modifier.fillMaxWidth(), icon = Res.drawable.ic_lock_16, enabled = false)
        rec.field == SCRIBE ->
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                Text(
                    tr(Res.string.recordingForAiNotes),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Primary,
                )
                RecordingPill(rec, actions.cancelScribe, actions.finishScribe)
                Text(
                    tr(Res.string.voiceRecordingFinishHint),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        state.scribeRetry || state.scribeUploading ->
            ShifaSecondaryButton(
                tr(Res.string.retryScribeUpload),
                actions.retryScribe,
                Modifier.fillMaxWidth(),
                loading = state.scribeUploading,
                enabled = !state.scribeUploading,
            )
        else ->
            ShifaSecondaryButton(
                tr(if (state.awaitingScribe) Res.string.startAiNotesAgain else Res.string.startAiNotes),
                actions.startScribe,
                Modifier.fillMaxWidth(),
                icon = Res.drawable.ic_mic_16,
            )
    }
}

private const val SCRIBE = "scribe"

/** The summary's words in the app's language (PDF-01), for End appointment. */
@Composable
private fun rememberEndLabels(): EndLabels {
    val summary =
        VisitSummaryLabels(
            title = tr(Res.string.summaryTitle),
            appointmentId =
                tr(Res.string.summaryAppointmentId, "%ID%").let {
                        t ->
                    { id: Long -> t.replace("%ID%", "$id") }
                },
            patient = tr(Res.string.patient),
            patientId = tr(Res.string.patientId),
            birthDate = tr(Res.string.dateOfBirth),
            gender = tr(Res.string.gender),
            doctor = tr(Res.string.doctor),
            visitType = tr(Res.string.summaryVisitType),
            duration = tr(Res.string.duration),
            minutes = tr(Res.string.summaryMinutes, "%M%").let { t -> { m: Int -> t.replace("%M%", "$m") } },
            notes = tr(Res.string.dentalClinicalNotes),
            signatures = tr(Res.string.summarySignatures),
            doctorSignature = tr(Res.string.summaryDoctorSignature),
            patientSignature = tr(Res.string.summaryPatientSignature),
            signedElectronically =
                tr(Res.string.summarySignedElectronically, "%D%").let {
                        t ->
                    { d: String -> t.replace("%D%", d) }
                },
            footer = tr(Res.string.summaryFooter),
            page =
                tr(Res.string.summaryPage, "%P%", "%N%").let {
                        t ->
                    { p: Int, n: Int -> t.replace("%P%", "$p").replace("%N%", "$n") }
                },
            dental = tr(Res.string.dentalPdfHeader),
            tooth = tr(Res.string.tooth),
            service = tr(Res.string.intelService),
            amount = tr(Res.string.clinicPaymentsColAmount),
            general = tr(Res.string.dentalGeneralServicesShort),
            subtotal = tr(Res.string.dentalSubtotal),
            discount = tr(Res.string.dentalDiscount),
            total = tr(Res.string.dentalTotal),
            plan = tr(Res.string.appointmentTreatmentPlanTitle),
            planTotal = tr(Res.string.appointmentPlanFinanceTotal),
            planPaid = tr(Res.string.appointmentPlanFinancePaid),
            planOutstanding = tr(Res.string.appointmentPlanFinanceOutstanding),
            doneThisVisit = tr(Res.string.summaryPlanDoneThisVisit),
            paymentThisVisit = tr(Res.string.appointmentPlanFinanceSessionPayment),
        )
    val inPerson = tr(Res.string.inPerson)
    val video = tr(Res.string.summaryVideoVisit)
    val title = tr(Res.string.summaryDocumentTitle, "%D%", "%T%")
    val male = tr(Res.string.genderMale)
    val female = tr(Res.string.genderFemale)
    val before = tr(Res.string.beforeTreatment)
    val after = tr(Res.string.afterTreatment)
    val methods = PayMethod.entries.associateWith { tr(methodLabel(it)) }
    val untitled = tr(Res.string.clinicTreatmentPlansUntitled)
    return EndLabels(
        photo = { if (it == PhotoKind.Before) before else after },
        method = { methods.getValue(it) },
        untitledPlan = untitled,
        summary = summary,
        aiHeading = tr(Res.string.aiNotesHeading),
        doctorHeading = tr(Res.string.doctorNotesSection),
        documentTitle = { d, t -> title.replace("%D%", d).replace("%T%", t) },
        visitType = { if (it == VisitKind.Video) video else inPerson },
        gender = { g ->
            when (g.trim().lowercase()) {
                "male" -> male
                "female" -> female
                else -> g
            }
        },
    )
}

/** The call's state with the call itself (its video tiles), for a video visit (VIDEO-02). */
internal class VideoPart(
    val state: VideoVisitState,
    val call: shifa.doctor.utils.VideoCall,
)

/** "10:00–10:30" on the practice clock; empty without times. */
private fun scheduleOf(
    state: VisitUiState,
    clock: PracticeClock = PracticeClock(profileRepository.me.value?.profile?.timeZone),
): String {
    val start = state.visit?.start ?: return ""
    val end = state.visit.end ?: return ""
    return "${displayTime(clock.local(start).time)}–${displayTime(clock.local(end).time)}"
}

@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.ConsultationNote
import shifa.doctor.data.model.DentalRecord
import shifa.doctor.data.model.DoctorService
import shifa.doctor.data.model.DraftNote
import shifa.doctor.data.model.IcdSuggestion
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientForm
import shifa.doctor.data.model.ServicePrice
import shifa.doctor.data.model.ToothServiceLine
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.videoCallNotYetAvailableMessage
import shifa.doctor.generated.resources.visitFactsLoadFailed
import shifa.doctor.ui.documents.PatientDocumentsUiState
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.utils.PracticeClock

private val now = Instant.parse("2026-11-04T05:12:30Z")
private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Asia/Tashkent") })

private val visit =
    AppointmentDetail(
        id = 102,
        patientId = 12,
        patientName = "Jasur Karimov",
        startAt = "2026-11-04T05:00:00Z",
        endAt = "2026-11-04T05:30:00Z",
        location = "Shifa Demo Clinic",
        status = "IN_PROGRESS",
        signatureRequested = true,
    )

private val patient =
    Patient(
        id = 12,
        name = "Jasur Karimov",
        birthDate = "1990-04-15",
        allergies = "Penicillin",
        chronicDisease = "Asthma",
    )

private val state =
    VisitUiState(
        loading = false,
        visit = visit,
        patient = patient,
        reason = "Toothache",
        lastVisit = Instant.parse("2026-10-20T06:00:00Z"),
        forms =
            listOf(
                PatientForm(id = 3, patientId = 12, date = "2026-10-20", patientSignedAt = "2026-10-20T07:00:00Z"),
            ),
        soapOpen = true,
        helper = VisitHelper.Ai,
        // APPT-05: a photo taken before treatment.
        photos = listOf(VisitPhoto(PhotoKind.Before, "IMG_0012.jpg", ByteArray(0))),
    )

private val notes =
    VisitNotesState(
        text = SoapNotes(subjective = "Night pain in 36 for three days", free = "Pulp removal, temporary filling."),
        notes =
            listOf(
                ConsultationNote(
                    1,
                    "SUBJECTIVE:\nPain on chewing.\n\nTRANSCRIPT:\nhidden",
                    source = ConsultationNote.AI_DRAFT,
                ),
            ),
        drafts =
            listOf(
                DraftNote(
                    "d1",
                    body = "ASSESSMENT:\nAcute pulpitis of 36.",
                    icdSuggestions = listOf(IcdSuggestion("K04.0", "Pulpitis", 0.82, isTop = true)),
                ),
            ),
    )

private val documents =
    PatientDocumentsUiState(
        listOf(
            PatientDocument(
                5,
                "Complete blood count",
                "2026-10-22",
                url = "f",
                category = "BLOOD_TEST",
                isSharedWithTeam = true,
            ),
            PatientDocument(8, "Form 025-2 (2026-10-20)", "2026-10-20", url = "f", category = "FORM_025_2"),
            PatientDocument(
                6,
                "Panoramic X-ray",
                "2026-10-19",
                canView = false,
                category = "XRAY",
                creatorLabel = "Malika Tursunova",
            ),
        ),
    )

// APPT-04: the workspace in General mode: SOAP open, Shifa AI's draft and note, grouped documents, signature awaited.
@Composable
internal fun VisitWorkspacePreview() =
    PreviewTheme {
        VisitContent(
            state,
            notes,
            VisitActions(),
            documents,
            clock = clock,
            now = { now },
            // SCRIBE-01: the AI scribe recording, 1:05 in.
            scribe = shifa.doctor.ui.speech.DictationState(field = "scribe", level = 0.5f, seconds = 65),
            scribeAllowed = true,
        )
    }

// APPT-04: Form 025-2 mode, the patient's signed form and Open Form 025-2; no helper.
@Composable
internal fun VisitForm0252Preview() =
    PreviewTheme {
        VisitContent(
            state.copy(mode = VisitMode.Form0252, helper = null, visit = visit.copy(signatureRequested = false)),
            notes,
            VisitActions(),
            documents,
            clock = clock,
            now = { now },
        )
    }

private val dental =
    DentalNotesState(
        loaded = true,
        record =
            DentalRecord(
                teeth =
                    mapOf(
                        "36" to
                            listOf(
                                ToothServiceLine(1, title = "Pulp removal", amountMinor = 45_000_000),
                                ToothServiceLine(2, title = "Temporary filling", amountMinor = 15_000_000),
                            ),
                        "__general__" to
                            listOf(
                                ToothServiceLine(3, title = "Panoramic X-ray", amountMinor = 2_500, currency = "USD"),
                            ),
                    ),
                discountPercent = 10.0,
                notes = "Lidocaine 2%, tolerated well.",
            ),
        discount = "10",
        services =
            shifa.doctor.data.model.dentalCatalog(
                listOf(
                    DoctorService(
                        1,
                        "Pulp removal",
                        groupName = "Endodontics",
                        prices = listOf(ServicePrice(45_000_000, "UZS")),
                    ),
                    DoctorService(
                        2,
                        "Temporary filling",
                        groupName = "Fillings",
                        prices = listOf(ServicePrice(15_000_000, "UZS")),
                    ),
                    DoctorService(
                        4,
                        "Composite filling",
                        groupName = "Fillings",
                        prices = listOf(ServicePrice(60_000_000, "UZS")),
                    ),
                ),
            ),
    )

// DENTAL-02: Dental visit record mode: the tooth map with 36's two services, one general service in USD (totals per
// currency), a 10 % discount and clinical notes.
@Composable
internal fun VisitDentalPreview() =
    PreviewTheme {
        VisitContent(
            state.copy(mode = VisitMode.Dental, dental = true, helper = null, soapOpen = false),
            notes,
            VisitActions(),
            documents,
            clock = clock,
            now = { now },
            dental = dental,
        )
    }

private val planSummary =
    shifa.doctor.data.model.TreatmentPlanSummary(
        id = 501,
        clinicId = 1,
        patientId = 12,
        title = "Treatment of 36 and 46",
        statusRaw = "ACTIVE",
        kindRaw = "COMPREHENSIVE",
        totalMinor = 330_000_000,
        paidMinor = 100_000_000,
        owedMinor = 230_000_000,
        linesCompletedCount = 1,
        linesTotalCount = 4,
    )

private fun planLine(
    id: Long,
    title: String,
    price: Long,
    fdi: String?,
    status: String = "PLANNED",
) = shifa.doctor.data.model.PlanLine(
    id,
    title = title,
    unitPriceMinor = price,
    lineTotalMinor = price,
    statusRaw = status,
    specialtyMetadata = fdi?.let { "{\"fdi\":\"$it\"}" },
)

private val plan =
    VisitPlanState(
        clinicId = 1,
        plans = listOf(planSummary),
        linkedId = 501,
        selectedId = 501,
        detail =
            shifa.doctor.data.model.TreatmentPlanDetail(
                planSummary,
                listOf(
                    planLine(11, "Root canal treatment", 80_000_000, "36"),
                    planLine(12, "Ceramic crown", 150_000_000, "36"),
                    planLine(13, "Composite filling", 60_000_000, "46", "COMPLETED"),
                    planLine(14, "Professional cleaning", 40_000_000, null),
                ),
            ),
        candidates =
            listOf(11L, 12L, 14L).map { shifa.doctor.data.model.FulfillmentCandidate(it) },
        ticks = setOf(11L),
        amount = "500 000",
    )

// DENTAL-03: a visit with the patient's plan: the panel (linked, progress), the plan-mode chart (36 partly done with a
// tick, 46 completed), the general lines and Plan finances with a payment typed.
@Composable
internal fun VisitDentalPlanPreview() =
    PreviewTheme {
        VisitContent(
            state.copy(mode = VisitMode.Dental, dental = true, helper = null, soapOpen = false, photos = emptyList()),
            notes,
            VisitActions(),
            documents,
            clock = clock,
            now = { now },
            dental = dental,
            plan = plan,
        )
    }

// DENTAL-03: tooth 36's planned lines, the root canal ticked.
@Composable
internal fun PlanFulfilSheetPreview() = PreviewTheme { PlanFulfilSheet("36", plan, PlanActions()) }

// DENTAL-04: the plan editor (the wizard hosts it, TP-04): 36's root canal and crown, cleaning, a 5 % discount.
@Composable
internal fun PlanEditorPreview() =
    PreviewTheme {
        val editor =
            PlanEditor(
                DentalRecord(
                    teeth =
                        mapOf(
                            "36" to
                                listOf(
                                    ToothServiceLine(
                                        serviceKey = "catalog:1",
                                        title = "Root canal treatment",
                                        amountMinor = 80_000_000,
                                    ),
                                    ToothServiceLine(
                                        serviceKey = "catalog:2",
                                        title = "Ceramic crown",
                                        amountMinor = 150_000_000,
                                    ),
                                ),
                            "__general__" to
                                listOf(
                                    ToothServiceLine(
                                        serviceKey = "catalog:3",
                                        title = "Cleaning",
                                        amountMinor = 40_000_000,
                                    ),
                                ),
                        ),
                    discountPercent = 5.0,
                    notes = "Phase 1: endodontics, then the crown.",
                ),
                emptyList(),
            )
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S14),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(
                    shifa.doctor.ui.theme.ShifaSize.S10,
                ),
        ) {
            DentalPanel(editor.state.value, null, DentalActions(), editor = true)
        }
    }

// DENTAL-04: a plan's teeth, read only (plan detail, TP-03): 36 planned, 46 completed.
@Composable
internal fun DentalPlanReadonlyPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .background(shifa.doctor.ui.theme.ShifaColors.Bg.Surface)
                .padding(shifa.doctor.ui.theme.ShifaSize.S14),
        ) { DentalPlanReadonly(plan.detail!!) }
    }

// A stand-in for previews: its tiles draw nothing (Daily's are real only on a device).
private object PreviewCall : shifa.doctor.utils.VideoCall {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(shifa.doctor.utils.VideoCallState())

    override fun startPreview() = Unit

    override fun join(
        roomUrl: String,
        token: String,
        camera: Boolean,
        mic: Boolean,
    ) = Unit

    override fun setCamera(on: Boolean) = Unit

    override fun setMic(on: Boolean) = Unit

    override fun flipCamera() = Unit

    override fun release() = Unit
}

// VIDEO-02: the pre-join page, the camera off, a refused room explained.
@Composable
internal fun VideoPreJoinPreview() =
    PreviewTheme {
        PreJoin(
            state,
            VideoVisitState(
                camera = true,
                mic = true,
                call = shifa.doctor.utils.VideoCallState(cameraOn = false),
                error = Res.string.videoCallNotYetAvailableMessage,
            ),
            PreviewCall,
            "10:00–10:30",
            VisitActions(),
        )
    }

// VIDEO-02: the workspace in a call: the patient joined (their tile, name, the doctor's corner), the controls; the
// microphone off.
@Composable
internal fun VideoInCallPreview() =
    PreviewTheme {
        VisitContent(
            state.copy(helper = null, soapOpen = false, photos = emptyList()),
            notes,
            VisitActions(),
            documents,
            clock = clock,
            now = { now },
            video =
                VideoPart(
                    VideoVisitState(
                        stage = VideoStage.InCall,
                        camera = true,
                        mic = true,
                        call = shifa.doctor.utils.VideoCallState(micOn = false, patientJoined = true),
                    ),
                    PreviewCall,
                ),
        )
    }

// VIDEO-02: the doctor left the call; the visit stays open with Rejoin.
@Composable
internal fun VideoLeftPreview() =
    PreviewTheme {
        VideoPane(
            "Jasur Karimov",
            VideoVisitState(stage = VideoStage.Left, camera = true, mic = true),
            PreviewCall,
            VideoActions(),
        )
    }

// APPT-05: Camera or Gallery for an after-treatment photo.
@Composable
internal fun PhotoSourcePreview() = PreviewTheme { PhotoSource(PhotoKind.After, VisitActions()) {} }

// DENTAL-02: tooth 36's services: the two added (removable), the group chips and the doctor's services.
@Composable
internal fun DentalServicesSheetPreview() = PreviewTheme { DentalServicesSheet("36", dental, DentalActions()) }

// APPT-04: clinic staff get the board's "Visits are run by the doctor".
@Composable
internal fun VisitStaffPreview() = PreviewTheme { StaffCannotRunVisits {} }

// APPT-06: a ready briefing with its meta line, the attached original, Copy.
@Composable
internal fun BriefingReadyPreview() =
    PreviewTheme {
        BriefingTray(
            BriefingUiState(
                shifa.doctor.data.model.AppointmentBriefing(
                    101,
                    "READY",
                    "Reason: follow-up after treatment.\n- Blood count from 22.10: haemoglobin slightly low.\n- No allergies recorded.",
                    1,
                    "2026-11-04T04:00:00Z",
                ),
                documents =
                    listOf(
                        PatientDocument(5, "Complete blood count", "2026-10-22", url = "f", category = "BLOOD_TEST"),
                    ),
            ),
            false,
            clock,
            {},
            {},
            {},
            {},
        )
    }

// APPT-06: a failed briefing with Retry briefing.
@Composable
internal fun BriefingFailedPreview() =
    PreviewTheme {
        BriefingTray(
            BriefingUiState(shifa.doctor.data.model.AppointmentBriefing(102, "FAILED")),
            false,
            clock,
            {},
            {},
            {},
            {},
        )
    }

// APPT-07: End appointment, what will be saved (notes, AI notes processing, signature awaited).
@Composable
internal fun EndConfirmPreview() =
    PreviewTheme {
        EndConfirm(state.copy(awaitingScribe = true, dental = true), notes, dental, "10:00–10:30", 18, EndActions())
    }

// APPT-07 / SCRIBE-02: the steps, waiting for the AI notes (0:42) with Continue without waiting.
@Composable
internal fun EndProgressPreview() =
    PreviewTheme {
        EndProgress(
            EndUiState(
                running = true,
                steps = listOf(EndStep.Notes, EndStep.Complete, EndStep.Scribe, EndStep.Summary),
                at = EndStep.Scribe,
                scribeSeconds = 42,
                completed = true,
            ),
            EndActions(),
        )
    }

// Flow 05/14: the visit ended, its summary saved: Done, Open Summary and Book follow-up.
@Composable
internal fun EndDonePreview() =
    PreviewTheme {
        EndProgress(
            EndUiState(
                steps = listOf(EndStep.Notes, EndStep.Complete, EndStep.Summary),
                completed = true,
                done = true,
                summary = PatientDocument(300, "Appointment documentation 06.11.2026 10:31", "2026-11-06", url = "f"),
            ),
            EndActions(followUp = {}),
        )
    }

// DENTAL-01: the tooth map, free mode (teeth with services and their counts) and plan mode (planned, partly done,
// completed), adult and child teeth.
@Composable
internal fun ToothMapPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(
                    shifa.doctor.ui.theme.ShifaSize.S16,
                ),
        ) {
            ToothMap(
                shifa.doctor.data.forms.Dentition.Permanent,
                mapOf(
                    "16" to 2,
                    "21" to 1,
                    "36" to 3,
                ),
                selected = "36",
                onTap = {
                },
            )
            ToothMap(
                shifa.doctor.data.forms.Dentition.Permanent,
                emptyMap(),
                states =
                    mapOf(
                        "16" to ToothPlanState.Done,
                        "17" to ToothPlanState.Partial,
                        "26" to ToothPlanState.Planned,
                        "46" to ToothPlanState.Planned,
                    ),
            )
            ToothMap(shifa.doctor.data.forms.Dentition.Primary, mapOf("54" to 1), onTap = {})
        }
    }

private fun vf(
    id: Long,
    type: String,
    text: String,
    status: String = "DRAFT",
    kept: Boolean = true,
    quote: String? = null,
    hint: String? = null,
    reason: String? = null,
    at: String = "2026-11-04T06:42:00Z",
) = shifa.doctor.data.model.VisitFact(
    id, 11, type, text, status, 101, quote, hint, kept, reason,
    committedAt =
        at.takeIf {
            status == "COMMITTED"
        },
    createdAt = at,
)

private val factPatterns =
    listOf(
        shifa.doctor.data.model.ClinicPattern(
            "med",
            "Spoken medication followed by a remote-care task",
            "MEDICATION",
            14,
        ),
        shifa.doctor.data.model.ClinicPattern("sym", "Symptom recorded on patient timeline", "SYMPTOM", 6),
    )

private val visitFacts =
    shifa.doctor.data.model.VisitFacts(
        listOf(
            vf(
                1,
                "MEDICATION",
                "Chlorhexidine 0.12 % rinse",
                quote = "rinse with chlorhexidine twice a day",
                hint = "twice a day for 7 days",
            ),
            vf(2, "SYMPTOM", "Mild swelling on the lower left jaw", quote = "a bit swollen, less than yesterday"),
            vf(3, "THERAPY", "Soft food for 3 more days"),
            vf(4, "DIAGNOSIS", "Dry socket", kept = false, reason = "Not said in the visit"),
        ),
        factPatterns,
    )

// AI-10: the scribe's facts: one rejected (struck through), one dropped by the model, patterns, Document everything.
@Composable
internal fun VisitFactsPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Box(Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16)) {
            VisitFactsCard(VisitFactsUiState(visitFacts, rejected = setOf(3)), VisitFactsActions())
        }
    }

// AI-10: documented (the message), a visit already documented, and a first read that failed.
@Composable
internal fun VisitFactsDonePreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ShifaSize.S16),
        ) {
            val committed =
                shifa.doctor.data.model.VisitFacts(
                    listOf(
                        vf(1, "MEDICATION", "Chlorhexidine 0.12 % rinse", "COMMITTED"),
                        vf(2, "SYMPTOM", "Mild swelling", "COMMITTED"),
                    ),
                )
            VisitFactsCard(VisitFactsUiState(committed, done = 2 to 1), VisitFactsActions())
            VisitFactsCard(VisitFactsUiState(committed), VisitFactsActions())
            VisitFactsCard(
                VisitFactsUiState(
                    loadError =
                        shifa.doctor.ui.emailcode.CodeBanner(
                            Res.string.visitFactsLoadFailed,
                            requestId = "r-1",
                        ),
                ),
                VisitFactsActions(),
            )
        }
    }

// AI-10: the patient's clinical timeline in practice time, the patterns; then the empty card.
@Composable
internal fun ClinicalTimelinePreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ShifaSize.S16),
        ) {
            shifa.doctor.ui.patients.ClinicalTimelineCard(
                shifa.doctor.ui.patients.ClinicalTimelineUiState(
                    shifa.doctor.data.model.VisitFacts(
                        listOf(
                            vf(
                                1,
                                "DIAGNOSIS",
                                "K02.1 caries on 26",
                                "COMMITTED",
                                quote = "deep caries on the upper left six",
                                at = "2026-10-22T05:42:00Z",
                            ),
                            vf(
                                2,
                                "MEDICATION",
                                "Ibuprofen 400 mg after meals",
                                "COMMITTED",
                                at = "2026-10-20T09:10:00Z",
                            ),
                        ),
                        factPatterns,
                    ),
                ),
                clock,
                {},
            )
            shifa.doctor.ui.patients.ClinicalTimelineCard(
                shifa.doctor.ui.patients.ClinicalTimelineUiState(shifa.doctor.data.model.VisitFacts()),
                clock,
                {},
            )
        }
    }

package shifa.doctor.ui.visit

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.ConsultationNote
import shifa.doctor.data.model.DraftNote
import shifa.doctor.data.model.IcdSuggestion
import shifa.doctor.data.model.Patient
import shifa.doctor.data.model.PatientDocument
import shifa.doctor.data.model.PatientForm
import shifa.doctor.ui.documents.PatientDocumentsUiState
import shifa.doctor.ui.theme.PreviewTheme
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
        VisitContent(state, notes, VisitActions(), documents, clock = clock, now = { now })
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

// APPT-04: clinic staff get the board's "Visits are run by the doctor".
@Composable
internal fun VisitStaffPreview() = PreviewTheme { StaffCannotRunVisits {} }

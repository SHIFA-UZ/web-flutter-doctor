package shifa.doctor.ui.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import shifa.doctor.data.model.Patient
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiRateLimit
import shifa.doctor.ui.forms.FormTemplateTray
import shifa.doctor.ui.forms.PatientFormsUiState
import shifa.doctor.ui.theme.PreviewTheme

private val today = LocalDate(2026, 11, 4)

private val people =
    listOf(
        Patient(
            12,
            "Dilnoza Rahimova",
            phones = listOf("+998 90 123 45 67"),
            birthDate = "1990-05-15",
            gender = "female",
        ),
        Patient(
            7,
            "Jasur Karimov",
            phones = listOf("+998 93 555 12 34"),
            birthDate = "1978-02-01",
            gender = "male",
            clinicalStatus = "AT_RISK",
        ),
        Patient(18, "Malika Tursunova", birthDate = "2001-09-30", gender = "female", clinicalStatus = "FOLLOW_UP"),
        Patient(25, "Bekzod Aliyev-Abdurakhmanov Islomjon o'g'li", phones = listOf("+998 97 000 11 22")),
        Patient(31, ""),
    )

@Composable
internal fun PatientsPreview() =
    PreviewTheme {
        PatientsContent(PatientsUiState(people, today = today), PatientsActions(create = {}))
    }

// WEB-07d (DaPatients web): the table under the header with New patient at the right.
@androidx.compose.runtime.Composable
internal fun PatientsWidePreview() =
    PreviewTheme {
        androidx.compose.runtime.CompositionLocalProvider(shifa.doctor.ui.widgets.LocalWideLayout provides true) {
            PatientsContent(PatientsUiState(people, today = today), PatientsActions(create = {}))
        }
    }

@Composable
internal fun PatientsEmptyPreview() =
    PreviewTheme {
        PatientsContent(PatientsUiState(emptyList(), today = today), PatientsActions(create = {}))
    }

// PAT-04: the create sheet after a try with no name, two phones (one taken) and a picked birth date.
@Composable
internal fun CreatePatientPreview() =
    PreviewTheme {
        CreatePatientTray(
            CreatePatientUiState(
                NewPatient(
                    phones = listOf("+998 90 123 45 67", "12"),
                    birthDate = LocalDate(1990, 5, 15),
                    bloodGroup = "O+",
                ),
                tried = true,
            ),
            {},
            {},
            {},
            {},
        )
    }

private val record =
    Patient(
        7,
        "Jasur Karimov",
        phones = listOf("+998 93 555 12 34"),
        birthDate = "1978-02-01",
        gender = "male",
        language = "ru",
        chronicDisease = "Diabetes",
        allergies = "Lidocaine",
        bloodGroup = "A+",
        clinicalStatus = "AT_RISK",
        atRisk = true,
        smsReminderEnabled = true,
    )

// PAT-05: an at-risk patient with a chronic disease, a past visit, SMS allowed and on, no patient-app login yet.
@Composable
internal fun PatientDetailPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(
                record,
                today = today,
                lastVisit = LocalDate(2026, 10, 21),
                favorite = true,
                smsAllowed = true,
                visits = visits,
            ),
            // A PRO doctor: "AI summary" under the star (AI-09).
            PatientDetailActions(briefing = {}),
        )
    }

// PAT-05: little known (no phone, birth date or language), an account, no visit; SMS allowed but no phone.
@Composable
internal fun PatientDetailSparsePreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(
                Patient(31, "", hasAccount = true, username = "patient.31"),
                today = today,
                visits = emptyList(),
                smsAllowed = true,
            ),
            PatientDetailActions(),
        )
    }

@Composable
internal fun PatientDetailNotFoundPreview() =
    PreviewTheme { PatientDetailContent(PatientDetailUiState(notFound = true), PatientDetailActions()) }

// PAT-06: Medical info with a second phone typed (Save shown) and allergies changed.
@Composable
internal fun PatientMedicalPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(
                record.copy(chronicDisease = "Diabetes (Type 2)", email = "jasur@example.com", address = "Chilonzor 9"),
                today = today,
                tab = RecordTab.Medical,
                phones = listOf("+998 93 555 12 34", "+998 90"),
                allergies = "Lidocaine, latex",
            ),
            PatientDetailActions(),
        )
    }

// CLINIC-06: the clinic's prophylaxis card (Medical info in a clinic's scope), last sent shown in the clinic's zone.
@Composable
internal fun ProphylaxisPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.background(
                shifa.doctor.ui.theme.ShifaColors.Bg.Canvas,
            ).padding(shifa.doctor.ui.theme.ShifaSpacing.Inset.Md),
        ) {
            ProphylaxisContent(
                ProphylaxisUiState(loaded = true, interval = 6, lastSentAt = "2026-10-01T05:00:00Z"),
                ProphylaxisActions(),
                shifa.doctor.utils.PracticeClock("Asia/Tashkent"),
            )
        }
    }

// PAT-06: "Edit details" (the create sheet, filled; Save once something changed).
@Composable
internal fun EditPatientPreview() =
    PreviewTheme {
        val form = NewPatient.of(record)
        CreatePatientTray(
            CreatePatientUiState(form.copy(allergies = "Lidocaine, latex"), initial = form),
            {},
            {},
            {},
            {},
        )
    }

private fun visit(
    id: Long,
    date: LocalDate,
    h: Int,
    status: String,
    reason: String?,
    video: Boolean = false,
    upcoming: Boolean = false,
) = Visit(
    id,
    kotlinx.datetime.Instant.fromEpochSeconds(id),
    date,
    kotlinx.datetime.LocalTime(h, 0),
    kotlinx.datetime.LocalTime(h, 30),
    status,
    video,
    null,
    reason,
    upcoming,
)

private val visits =
    listOf(
        visit(6, LocalDate(2026, 11, 20), 11, "CONFIRMED", "Crown fitting", upcoming = true),
        visit(5, LocalDate(2026, 11, 12), 9, "CONFIRMED", "Follow-up", video = true, upcoming = true),
        visit(4, today, 10, "IN_PROGRESS", "Check-up"),
        visit(3, LocalDate(2026, 11, 3), 15, "NO_SHOW", null),
        visit(2, LocalDate(2026, 10, 21), 11, "COMPLETED", "Filling 26"),
        visit(1, LocalDate(2026, 9, 30), 9, "CANCELLED", "Cleaning"),
        visit(0, LocalDate(2026, 9, 12), 14, "PAYMENT_FAILED", "Consultation", video = true),
    )

// PAT-07: the Appointments tab, practice-clock times, every status.
@Composable
internal fun PatientAppointmentsPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(record, today = today, visits = visits, tab = RecordTab.Appointments),
            PatientDetailActions(),
        )
    }

// PAT-07: the Activity tab, by month, Today / Yesterday.
@Composable
internal fun PatientActivityPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(record, today = today, visits = visits, tab = RecordTab.Activity),
            PatientDetailActions(),
        )
    }

// DOCS-04: the Documents tab: a shared result, a private note, a form PDF, a locked upload by the patient.
@Composable
internal fun PatientDocumentsPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(record, today = today, tab = RecordTab.Documents),
            PatientDetailActions(documents = shifa.doctor.ui.documents.DocumentsActions(edit = {})),
            shifa.doctor.ui.documents.PatientDocumentsUiState(
                listOf(
                    shifa.doctor.data.model.PatientDocument(
                        5,
                        "Complete blood count",
                        "2026-10-22",
                        url = "f",
                        category = "BLOOD_TEST",
                        isSharedWithTeam = true,
                    ),
                    shifa.doctor.data.model.PatientDocument(
                        7,
                        "Treatment notes",
                        "2026-10-21",
                        url = "f",
                        category = "INTERNAL_NOTE",
                        // The doctor's own upload: Edit next to Open (BE-21).
                        canEdit = true,
                    ),
                    shifa.doctor.data.model.PatientDocument(
                        8,
                        "Form 025-2 (2026-10-21)",
                        "2026-10-21",
                        url = "f",
                        category = "FORM_025_2",
                    ),
                    shifa.doctor.data.model.PatientDocument(
                        6,
                        "ECG from previous clinic, scanned at home, page 1 of 3",
                        "2026-09-29",
                        canView = false,
                        category = "ECG",
                        creatorLabel = "Jasur Karimov",
                    ),
                ),
            ),
            // FORM-05: the form PDF is linked to its form, so it gets Edit form.
            forms = PatientFormsUiState(listOf(form(31, "2026-10-21", documentId = 8))),
        )
    }

private fun form(
    id: Long,
    date: String,
    documentId: Long? = null,
    signedAt: String? = null,
    requested: Boolean = false,
    template: String = "025-2",
) = shifa.doctor.data.model.PatientForm(
    id = id,
    patientId = record.id,
    templateId = template,
    date = date,
    fullName = record.name,
    documentId = documentId,
    patientSignedAt = signedAt,
    signatureRequested = requested,
)

// FORM-05: the Forms tab: signed, signature requested, no PDF, and a template this app doesn't know.
@Composable
internal fun PatientFormsPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(record, today = today, tab = RecordTab.Forms),
            PatientDetailActions(),
            forms =
                PatientFormsUiState(
                    listOf(
                        form(33, "2026-11-04", documentId = 102, signedAt = "2026-11-04T09:10:00Z"),
                        form(32, "2026-10-21", documentId = 101, requested = true),
                        form(31, "2026-10-01"),
                        form(30, "2026-09-12", documentId = 90, template = "027"),
                    ),
                ),
        )
    }

// FORM-05: no forms yet.
@Composable
internal fun PatientFormsEmptyPreview() =
    PreviewTheme {
        PatientDetailContent(
            PatientDetailUiState(record, today = today, tab = RecordTab.Forms),
            PatientDetailActions(),
            forms = PatientFormsUiState(emptyList()),
        )
    }

// FORM-05: the template sheet.
@Composable
internal fun FormTemplatePreview() = PreviewTheme { FormTemplateTray({}, {}) }

private val briefingText =
    "## Snapshot\nType 2 diabetes; HbA1c **7.4 %** in October.\n\n" +
        "## Dental\n- Pain in **26** when chewing, sensitive to cold\n- Last cleaning: March 2026\n\n" +
        "## Watch\n- Penicillin allergy"

// AI-09: the briefing being made: the caption and a skeleton.
@Composable
internal fun PatientBriefingLoadingPreview() =
    PreviewTheme { PatientBriefingTray(PatientBriefingUiState(loading = true), "Dilnoza Rahimova", {}, {}, {}) }

// AI-09: ready: sources, the text with headings, bullets and bold, the disclaimer, Copy and Regenerate.
@Composable
internal fun PatientBriefingReadyPreview() =
    PreviewTheme {
        PatientBriefingTray(
            PatientBriefingUiState(briefing = shifa.doctor.data.model.PatientBriefing(briefingText, 2, 3)),
            "Dilnoza Rahimova",
            {},
            {},
            {},
        )
    }

// AI-09: nothing to brief on (the backend's 400), and the daily limit.
@Composable
internal fun PatientBriefingEmptyPreview() =
    PreviewTheme {
        androidx.compose.foundation.layout.Column {
            PatientBriefingTray(PatientBriefingUiState(empty = true), "Dilnoza Rahimova", {}, {}, {})
            PatientBriefingTray(
                PatientBriefingUiState(
                    error =
                        shifa.doctor.ui.emailcode.CodeBanner(
                            Res.string.aiRateLimit,
                            requestId = "7f3c-91",
                        ),
                ),
                "Dilnoza Rahimova",
                {},
                {},
                {},
            )
        }
    }

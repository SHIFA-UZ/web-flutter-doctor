package shifa.doctor.ui.forms

import androidx.compose.runtime.Composable
import shifa.doctor.data.model.ClinicalChip
import shifa.doctor.data.model.ClinicalDisease
import shifa.doctor.data.model.ClinicalSharedTemplate
import shifa.doctor.data.model.ClinicalTopDiagnosis
import shifa.doctor.data.model.LocalizedName
import shifa.doctor.ui.theme.PreviewTheme

private fun en(text: String) = LocalizedName(en = text)

private val disease =
    ClinicalDisease(
        "dis.003",
        3,
        "grp.caries",
        icdCodes = listOf("K02.1"),
        names = en("Moderate caries"),
        chips =
            listOf(
                ClinicalChip("c1", "complaints", en("Pain from sweet and cold food")),
                ClinicalChip("c2", "complaints", en("Food gets stuck in the tooth")),
                ClinicalChip("m1", "morbi", en("Reacting to food for [X] months"), listOf("X")),
                ClinicalChip("o1", "objective", en("Carious cavity within dentine")),
                ClinicalChip("t1", "treatment_1", en("Preparation and composite filling")),
                ClinicalChip("r1", "recommendations", en("Brush twice a day, fluoride paste")),
            ),
    )

// CLIN-02: no disease yet: the hint, the doctor's frequent diagnoses and Choose a disease.
@Composable
internal fun ClinicalPanelEmptyPreview() =
    PreviewTheme {
        ClinicalPanel(
            ClinicalPanelUiState(
                loading = false,
                top =
                    listOf(
                        ClinicalTopDiagnosis("dis.003", en("Moderate caries")),
                        ClinicalTopDiagnosis("dis.010", en("Acute pulpitis")),
                    ),
            ),
            "en",
            ClinicalPanelActions(),
        )
    }

// CLIN-02: a disease chosen: its chips per field (one unpicked), an [X] value, occlusion and shared texts.
@Composable
internal fun ClinicalPanelDiseasePreview() =
    PreviewTheme {
        ClinicalPanel(
            ClinicalPanelUiState(
                loading = false,
                disease = disease,
                occlusion = listOf(ClinicalChip("occ.norm", "occlusion", en("Orthognathic occlusion"))),
                mucosa = listOf(ClinicalSharedTemplate("mucosa.A", "mucosa", "oral_cavity", en("Normal mucosa"))),
                xray =
                    listOf(
                        ClinicalSharedTemplate(
                            "xray.caries",
                            "xray",
                            "xray",
                            en("Enamel and dentine radiolucency"),
                            correlates = listOf("grp.caries"),
                        ),
                    ),
                selected =
                    mapOf(
                        "c1" to emptyMap(),
                        "m1" to mapOf("X" to "3"),
                        "o1" to emptyMap(),
                        "t1" to emptyMap(),
                        "r1" to emptyMap(),
                        "occ.norm" to emptyMap(),
                    ),
            ),
            "en",
            ClinicalPanelActions(),
        )
    }

private val editing =
    shifa.doctor.data.model.PatientForm(
        id = 9,
        patientId = 14,
        date = "2026-11-04",
        fullName = "Jasur Aliyev",
        gender = "male",
        age = 41,
        address = "Chilonzor 9, Tashkent",
        diagnosis = "Acute pulpitis",
        diagnosisCode = "K04.0",
        diagnosisDisplay = "Pulpitis",
        complaints = "Night pain in 26, worse from cold.",
        treatment = "Pulp removal, temporary filling.",
        dentalChart =
            mapOf(
                "TOP_HIST_0_5" to "C",
                "TOP_HIST_0_date" to "2026-10-20",
                "TOP_HIST_0_doctor" to "Dr. Karimov",
                "TOP_0_10" to "P",
                "BOTTOM_0_3" to "П",
            ),
        followups = listOf(shifa.doctor.data.model.FormFollowup("2026-11-11", "Permanent filling placed", "Karimov")),
        signatureRequested = true,
    )

// FORM-04: the editor's top: header, General with the ICD chip, the engine card.
@Composable
internal fun FormEditorTopPreview() =
    PreviewTheme {
        FormEditorContent(
            FormEditorUiState(
                loading = false,
                form = editing,
                original = editing,
                today = kotlinx.datetime.LocalDate(2026, 11, 4),
                doctorName = "Dr. Karimov",
            ),
            ClinicalPanelUiState(loading = false),
            "en",
            FormEditorActions(),
            ClinicalPanelActions(),
        )
    }

package shifa.doctor.ui.patients

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import shifa.doctor.data.model.Patient
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

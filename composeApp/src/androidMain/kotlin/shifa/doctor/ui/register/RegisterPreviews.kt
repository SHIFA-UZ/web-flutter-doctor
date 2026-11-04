package shifa.doctor.ui.register

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import shifa.doctor.data.auth.ExistingPatient
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.catalog.Clinic
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.profile.Gender
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.doctorAccountExists
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.passwordsDoNotMatch
import shifa.doctor.generated.resources.timezoneDetectionFailed
import shifa.doctor.ui.theme.PreviewTheme

// DaAuth `reg1` (flow 01/07 §3a), its existing-patient branch (§3b) and `reg2`'s timezone (flow 01/08).
private val invite = Invite("SHIFA-DOC-2026", false)

private val typed =
    RegisterUiState(
        invite,
        firstName = "Aziza",
        lastName = "Karimova",
        email = "aziza.karimova@mail.uz",
        phone = "+998 90 123 45 67",
        password = "Shifa2026",
        confirm = "Shifa2026",
    )

@Preview(widthDp = 402, heightDp = 1300)
@Composable
internal fun RegisterFormPreview() = PreviewTheme { RegisterScreenContent(typed, RegisterActions()) }

@Preview(widthDp = 402, heightDp = 1300)
@Composable
internal fun RegisterErrorsPreview() =
    PreviewTheme {
        RegisterScreenContent(
            typed.copy(
                firstName = "",
                phone = "12345",
                confirm = "Shifa",
                firstNameError = Res.string.enterFirstName,
                emailError = Res.string.doctorAccountExists,
                phoneError = Res.string.invalidPhone,
                confirmError = Res.string.passwordsDoNotMatch,
            ),
            RegisterActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun RegisterExistingPatientPreview() =
    PreviewTheme {
        RegisterScreenContent(
            typed.copy(
                step = RegisterStep.ExistingPatient,
                existing = ExistingPatient("Aziza Karimova", null),
                timeZone = "Asia/Tashkent",
                password = "Shifa2026!",
                confirm = "Shifa2026!",
            ),
            RegisterActions(),
        )
    }

private val details =
    typed.copy(
        step = RegisterStep.Details,
        timeZone = "Asia/Tashkent",
        dob = LocalDate(1990, 3, 12),
        gender = Gender.Female,
        address = "Chilonzor 5, Tashkent",
        clinic = Clinic(2, "Shifa Dev Clinic"),
        professions = listOf(Profession("Dentist", "Stomatolog"), Profession("Orthodontist", "Ortodont")),
    )

@Preview(widthDp = 402, heightDp = 1100)
@Composable
internal fun RegisterDetailsPreview() = PreviewTheme { RegisterScreenContent(details, RegisterActions()) }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun ClinicPickerPreview() =
    PreviewTheme {
        ClinicPicker(
            details.copy(clinics = listOf(Clinic(2, "Shifa Dev Clinic"), Clinic(1, "Tashkent Med Center"))),
            RegisterActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun ClinicPickerFailedPreview() =
    PreviewTheme { ClinicPicker(details.copy(clinic = null, pickerFailed = true), RegisterActions()) }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun ProfessionPickerPreview() =
    PreviewTheme {
        ProfessionPicker(
            details.copy(
                catalog =
                    listOf(
                        Profession("Dentist", "Stomatolog"),
                        Profession("Orthodontist", "Ortodont"),
                        Profession("Cardiologist", "Kardiolog"),
                        Profession("Neurologist", "Nevrolog"),
                    ),
            ),
            RegisterActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun RegisterNoTimeZonePreview() =
    PreviewTheme {
        RegisterScreenContent(
            typed.copy(step = RegisterStep.Details, timeZoneError = Res.string.timezoneDetectionFailed),
            RegisterActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun TimeZonePickerPreview() =
    PreviewTheme {
        TimeZonePicker(
            typed.copy(timeZone = "Asia/Tashkent", detectedTimeZone = "Asia/Tashkent"),
            RegisterActions(),
        )
    }

// DaAuth `staffReg` (flow 01/10).
private val staff =
    RegisterUiState(Invite("SHIFA-STAFF-2026", true, 7, "Shifa Dental Clinic", "dilfuza@shifadental.uz"))
        .copy(firstName = "Dilfuza", lastName = "Yusupova", timeZone = "Asia/Tashkent", password = "Shifa2026")

@Preview(widthDp = 402, heightDp = 1100)
@Composable
internal fun RegisterStaffPreview() = PreviewTheme { RegisterScreenContent(staff, RegisterActions()) }

@Preview(widthDp = 402, heightDp = 1100)
@Composable
internal fun RegisterStaffNoEmailPreview() =
    PreviewTheme {
        RegisterScreenContent(
            RegisterUiState(Invite("SHIFA-STAFF-2026", true)).copy(timeZoneError = Res.string.timezoneDetectionFailed),
            RegisterActions(),
        )
    }

package shifa.doctor.ui.register

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.data.auth.ExistingPatient
import shifa.doctor.data.auth.Invite
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

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun RegisterDetailsPreview() =
    PreviewTheme {
        RegisterScreenContent(typed.copy(step = RegisterStep.Details, timeZone = "Asia/Tashkent"), RegisterActions())
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

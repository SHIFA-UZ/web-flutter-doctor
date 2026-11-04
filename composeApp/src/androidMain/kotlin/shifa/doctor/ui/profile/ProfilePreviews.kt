package shifa.doctor.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.DoctorProfile
import shifa.doctor.data.model.DoctorSubscription
import shifa.doctor.data.model.MeProfile
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.nameInvalid
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.photoUploadFailed
import shifa.doctor.ui.theme.PreviewTheme

// DaProfile root (flow 08/01): complete, incomplete (with the completeness card), staff, and the error state.
private val complete =
    DoctorMe(
        profile =
            DoctorProfile(
                firstName = "Aziza",
                lastName = "Karimova",
                dob = "1984-03-12",
                profession = "Dentist, Orthodontist",
                // No photo URL: Kamel cannot load one under Robolectric (the brand fallback shows).
                timeZone = "Asia/Tashkent",
                locationStreetAddress = "Chilonzor 9",
                locationCity = "Tashkent",
            ),
        contact = DoctorContact("+998901234567", "aziza@shifa.uz"),
        subscription = DoctorSubscription("PREMIUM", listOf("CALENDAR")),
    )

@Preview(widthDp = 402, heightDp = 1200)
@Composable
internal fun ProfileRootPreview() =
    PreviewTheme {
        ProfileScreenContent(ProfileUiState(me = complete), ProfileActions())
    }

@Preview(widthDp = 402, heightDp = 1200)
@Composable
internal fun ProfileIncompletePreview() =
    PreviewTheme {
        ProfileScreenContent(
            ProfileUiState(
                me =
                    complete.copy(
                        profile = DoctorProfile(firstName = "Aziza", lastName = "Karimova"),
                        subscription = null,
                    ),
            ),
            ProfileActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ProfileStaffPreview() =
    PreviewTheme {
        ProfileScreenContent(
            ProfileUiState(
                staff = true,
                staffMe = MeProfile(5, "CLINIC_STAFF", "Dilfuza", "Yusupova", "dilfuza@shifadental.uz"),
                staffNote = true,
            ),
            ProfileActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ProfileErrorPreview() =
    PreviewTheme {
        ProfileScreenContent(ProfileUiState(error = Res.string.networkError), ProfileActions())
    }

private val personal =
    PersonalInfo(
        "Aziza",
        "Karimova",
        kotlinx.datetime.LocalDate(1988, 3, 14),
        shifa.doctor.data.profile.Gender.Female,
        listOf(
            shifa.doctor.data.catalog.Profession("Cardiologist", "Kardiolog"),
            shifa.doctor.data.catalog.Profession("Internist (Internal Medicine)", "Terapevt"),
        ),
    )

@Composable
internal fun PersonalInfoPreview() =
    PreviewTheme {
        PersonalInfoContent(
            PersonalInfoUiState(saved = personal, form = personal.copy(firstName = "Zarina")),
            PersonalInfoActions(),
        )
    }

@Composable
internal fun PersonalInfoErrorsPreview() =
    PreviewTheme {
        PersonalInfoContent(
            PersonalInfoUiState(
                saved = personal,
                form =
                    personal.copy(
                        firstName = "",
                        lastName = "K4rimova",
                        dob = null,
                        gender = null,
                        professions = emptyList(),
                    ),
                firstNameError = Res.string.enterFirstName,
                lastNameError = Res.string.nameInvalid,
                uploading = true,
                banner =
                    shifa.doctor.ui.emailcode.CodeBanner(
                        Res.string.photoUploadFailed,
                        requestId = "req-8f2c41",
                    ),
            ),
            PersonalInfoActions(),
        )
    }

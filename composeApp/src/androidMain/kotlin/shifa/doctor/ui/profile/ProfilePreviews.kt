package shifa.doctor.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.DoctorProfile
import shifa.doctor.data.model.DoctorSubscription
import shifa.doctor.data.model.MeProfile
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.currentPasswordIncorrect
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.invalidIban
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.nameInvalid
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.photoUploadFailed
import shifa.doctor.generated.resources.servicePriceInvalid
import shifa.doctor.generated.resources.serviceTagDuplicate
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

private val contact =
    ContactForm(
        "+998 90 123 45 67",
        "aziza@shifa.dev",
        "Uzbekistan",
        "Tashkent",
        "Chilonzor",
        "Tashkent",
        "100115",
        "Bunyodkor Ave 12, 3rd floor",
        41.2856,
        69.2034,
    )

@Composable
internal fun ContactPreview() =
    PreviewTheme {
        ContactContent(
            ContactUiState(
                saved = contact,
                form = contact.copy(phone = "12345"),
                timeZone = "Asia/Tashkent",
                phoneError = Res.string.invalidPhone,
            ),
            ContactActions(),
        )
    }

@Composable
internal fun BillingPreview() =
    PreviewTheme {
        val saved = InvoicingForm("Dr Aziza Karimova", "billing@shifa.dev", "", "")
        BillingContent(
            BillingUiState(
                tier = shifa.doctor.data.subscription.DoctorTier.Basic,
                plans =
                    listOf(
                        shifa.doctor.data.subscription.SubscriptionPlan("BASIC", "Basic", 9900, "EUR"),
                        shifa.doctor.data.subscription.SubscriptionPlan("PRO", "Pro", 19900, "EUR"),
                    ),
                billing = shifa.doctor.data.model.DoctorBilling(stripeConnectAccountId = "acct_1Qx9ZzA2b3C41234"),
                saved = saved,
                form = saved.copy(iban = "DE89 37"),
                ibanError = Res.string.invalidIban,
            ),
            BillingActions(),
        )
    }

@Composable
internal fun SecurityPreview() =
    PreviewTheme {
        SecurityContent(
            SecurityUiState(
                current = "",
                new = "Abcdef1",
                confirm = "",
                currentError = Res.string.currentPasswordIncorrect,
            ),
            SecurityActions(),
        )
    }

@Composable
internal fun ExtendedProfilePreview() =
    PreviewTheme {
        val saved = ExtendedForm("Twenty years of painless root canals and implants.", "@drkarimova", "")
        ExtendedProfileContent(
            ExtendedUiState(
                saved = saved,
                form = saved.copy(instagram = "dr.karimova"),
                tags = listOf("Braces", "Root canal", "Implants", "Whitening"),
                tagInput = "braces",
                tagError = Res.string.serviceTagDuplicate,
                certificates =
                    listOf(
                        "https://x/certificates/karimova-12-1714000000.pdf",
                        "https://x/certificates/diploma.jpg",
                    ),
            ),
            ExtendedActions(),
        )
    }

private val previewServices =
    listOf(
        shifa.doctor.data.model.DoctorService(1, "Free first consultation", null, true, true),
        shifa.doctor.data.model.DoctorService(
            2,
            "Consultation",
            "First visit with an examination and a treatment plan",
            prices =
                listOf(
                    shifa.doctor.data.model.ServicePrice(15000000, "UZS"),
                    shifa.doctor.data.model.ServicePrice(2000, "USD", 1),
                ),
        ),
        shifa.doctor.data.model.DoctorService(
            3,
            "Braces",
            groupName = "Orthodontics",
            prices = listOf(shifa.doctor.data.model.ServicePrice(900000000, "UZS")),
        ),
        shifa.doctor.data.model.DoctorService(4, "Cleaning", isActive = false, clinicManaged = true),
    )

private val previewLocations =
    listOf(shifa.doctor.data.model.DoctorLocation(1, "Shifa Clinic", locationCity = "Tashkent"))

@Composable
internal fun ServicesListPreview() =
    PreviewTheme {
        ServicesContent(
            ServicesUiState(loading = false, services = previewServices, locations = previewLocations),
            ServicesActions(),
        )
    }

@Composable
internal fun ServiceEditorPreview() =
    PreviewTheme {
        val draft =
            ServiceDraft(
                2,
                "Consultation",
                "First visit",
                prices = listOf(PriceRow("150000", "UZS"), PriceRow("20", "USD", 1)),
            )
        ServicesContent(
            ServicesUiState(
                loading = false,
                services = previewServices,
                locations = previewLocations,
                original = draft,
                draft = draft.copy(prices = draft.prices + PriceRow("12.5", "UZS")),
                rowErrors = mapOf(2 to Res.string.servicePriceInvalid),
            ),
            ServicesActions(),
        )
    }

@Composable
internal fun ServiceGroupsPreview() =
    PreviewTheme {
        val groups =
            listOf(
                shifa.doctor.data.model.ServiceGroup(1, "Consultations", 1),
                shifa.doctor.data.model.ServiceGroup(2, "Orthodontics", 2),
            )
        ServicesContent(
            ServicesUiState(
                loading = false,
                groups = groups,
                groupsDraft = groups + shifa.doctor.data.model.ServiceGroup(name = ""),
                groupErrors = setOf(2),
            ),
            ServicesActions(),
        )
    }

@Composable
internal fun SettingsPreview() =
    PreviewTheme {
        SettingsContent(
            SettingsUiState(
                settings =
                    shifa.doctor.data.model.DoctorSettings(
                        "Uzbekistan",
                        "uz",
                        twoFA = false,
                        encryptedDocs = true,
                        defaultStartTab = "calendar",
                    ),
                tasksAllowed = true,
            ),
            SettingsActions(debug = {}),
            "O'zbekcha",
        )
    }

@Composable
internal fun LanguagePreview() = PreviewTheme { LanguageContent(shifa.doctor.data.storage.AppLanguage.Uzbek, {}, {}) }

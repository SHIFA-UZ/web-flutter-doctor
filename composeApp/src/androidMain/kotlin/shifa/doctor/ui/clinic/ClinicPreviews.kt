package shifa.doctor.ui.clinic

import androidx.compose.runtime.Composable
import shifa.doctor.data.clinics.ClinicsState
import shifa.doctor.data.model.ClinicOverviewStats
import shifa.doctor.data.model.MyClinic
import shifa.doctor.ui.theme.PreviewTheme

internal val previewClinics =
    ClinicsState(
        listOf(
            MyClinic(
                2,
                "Shifa Dev Clinic",
                timeZone = "Asia/Tashkent",
                phone = "+998712000000",
                email = "clinic@shifa.dev",
                address = "Amir Temur 1, Tashkent",
                membershipRole = "OWNER",
                isPracticeClinic = true,
                defaultDoctorRevenueSharePercent = 60,
                canManageFinanceSettings = true,
            ),
            MyClinic(5, "Smile Clinic Samarkand", timeZone = "Asia/Samarkand", membershipRole = "DOCTOR"),
        ),
        selectedId = 2,
        known = true,
    )

@Composable
private fun Workspace(
    state: ClinicUiState,
    section: @Composable androidx.compose.foundation.layout.ColumnScope.(ClinicSection, MyClinic) -> Unit = { _, _ -> },
) = PreviewTheme { ClinicWorkspaceContent(state, false, {}, {}, {}, {}, {}, ClinicActions(), section) }

// CLINIC-02/04 (board `DaClinic`, flows 09/01, 09/20): the header with two clinics, the Overview, Settings.
@Composable
internal fun ClinicOverviewPreview() = Workspace(ClinicUiState(previewClinics, stats = ClinicOverviewStats(4, 3, 12)))

@Composable
internal fun ClinicSettingsPreview() = Workspace(ClinicUiState(previewClinics, section = ClinicSection.Settings))

@Composable
internal fun ClinicEmptyPreview() = Workspace(ClinicUiState(ClinicsState(known = true)))

private val previewPeople =
    ClinicPeopleUiState(
        members =
            listOf(
                shifa.doctor.data.model.ClinicMember(21, 101, "Aziza Karimova", "OWNER", null, 60),
                shifa.doctor.data.model.ClinicMember(22, 102, "Bobur Aliyev", "DOCTOR", 70, 70),
                shifa.doctor.data.model.ClinicMember(null, 103, "Dilfuza Yusupova", "RECEPTIONIST"),
            ),
        invitations =
            listOf(
                shifa.doctor.data.model.ClinicInvitation(5, "anna@clinic.uz", "2999-01-10T13:00:00Z", pending = true),
                shifa.doctor.data.model.ClinicInvitation(6, "olim@clinic.uz", "2026-10-01T13:00:00Z", consumed = true),
            ),
    )

// CLINIC-05 (flow 09/02): the owner's hint and Default share, chips from the roles, shares (a receptionist has none).
@Composable
internal fun ClinicDoctorsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Doctors)) { _, clinic ->
        DoctorsContent(clinic, previewPeople, PeopleActions()) {}
    }

// CLINIC-08 (flow 09/19): Invite receptionist, a pending invitation with Revoke, a used one.
@Composable
internal fun ClinicInvitationsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Invitations)) { _, clinic ->
        InvitationsContent(clinic, previewPeople, PeopleActions())
    }

// CLINIC-06 (board `DaClinic` Patients): search, the total, patients with phone and email (a dash without a phone), Load more.
@Composable
internal fun ClinicPatientsPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Patients)) { _, _ ->
        ClinicPatientsContent(
            ClinicPatientsUiState(
                rows =
                    listOf(
                        shifa.doctor.data.model.ClinicPatient(
                            15,
                            "Gulnora Rashidova",
                            "+998 90 123 45 67",
                            "gulnora@mail.uz",
                        ),
                        shifa.doctor.data.model.ClinicPatient(16, "Jasur Karimov", "+998 93 555 12 34"),
                        shifa.doctor.data.model.ClinicPatient(17, "Malika Tursunova", null, "malika@mail.uz"),
                    ),
                total = 64,
                last = false,
            ),
            ClinicPatientsActions(),
        )
    }

// CLINIC-06: nothing matches the search.
@Composable
internal fun ClinicPatientsEmptyPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Patients)) { _, _ ->
        ClinicPatientsContent(ClinicPatientsUiState(typed = "zzz", rows = emptyList()), ClinicPatientsActions())
    }

private val previewServices =
    ClinicServicesUiState(
        services =
            listOf(
                shifa.doctor.data.model.PlanServiceOption(
                    "catalog:1",
                    "CLINIC_CATALOG",
                    1,
                    title = "Root canal treatment",
                    code = "D3310",
                    defaultPriceMinor = 80_000_000,
                    offeredByDoctorNames = listOf("Aziza Karimova", "Bobur Aliyev"),
                ),
                shifa.doctor.data.model.PlanServiceOption(
                    "catalog:2",
                    "CLINIC_CATALOG",
                    2,
                    title = "Ceramic crown",
                    defaultPriceMinor = 150_000_000,
                    active = false,
                    offeredByDoctorNames = listOf("Aziza Karimova"),
                ),
                shifa.doctor.data.model.PlanServiceOption(
                    "doctor:22:service:3",
                    "DOCTOR_SERVICE",
                    doctorServiceId = 3,
                    title = "Cleaning",
                    defaultPriceMinor = 2_550,
                    currency = "USD",
                    offeredByDoctorNames = listOf("Bobur Aliyev"),
                ),
            ),
        catalog =
            listOf(
                shifa.doctor.data.model.CatalogItem(1, 1, "D3310", "Root canal treatment", 80_000_000),
                shifa.doctor.data.model.CatalogItem(
                    2,
                    1,
                    null,
                    "Ceramic crown",
                    150_000_000,
                    active = false,
                    appliesToAllDoctors = false,
                    assignedDoctorProfileIds = listOf(21),
                ),
            ),
    )

// CLINIC-07 (board `DaClinic` Services, flow 09/04): search and Add, status chips, a clinic row for all doctors, an
// inactive one (grey italic), a doctor's own service (USD, info button).
@Composable
internal fun ClinicServicesPreview() =
    Workspace(ClinicUiState(previewClinics, section = ClinicSection.Services)) { _, _ ->
        ServicesContent(previewServices, ServicesActions())
    }

// CLINIC-07: the editor for a service of chosen doctors, tried with a missing price and a code already used.
@Composable
internal fun ClinicServiceEditorPreview() =
    PreviewTheme {
        ServiceEditorForm(
            ServiceEditor(
                title = "Implant",
                code = "D3310",
                price = "",
                all = false,
                doctorIds = setOf(22L),
                tried = true,
            ),
            previewServices.copy(
                doctors =
                    listOf(
                        shifa.doctor.data.model.ClinicMember(21, 101, "Aziza Karimova", "OWNER"),
                        shifa.doctor.data.model.ClinicMember(22, 102, "Bobur Aliyev", "DOCTOR"),
                    ),
            ),
            ServicesActions(),
        )
    }

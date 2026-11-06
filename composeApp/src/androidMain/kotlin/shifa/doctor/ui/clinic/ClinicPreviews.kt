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

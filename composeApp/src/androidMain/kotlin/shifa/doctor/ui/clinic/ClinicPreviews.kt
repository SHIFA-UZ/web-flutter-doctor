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
private fun Workspace(state: ClinicUiState) =
    PreviewTheme { ClinicWorkspaceContent(state, false, {}, {}, {}, {}, {}, ClinicActions()) { _, _ -> } }

// CLINIC-02/04 (board `DaClinic`, flows 09/01, 09/20): the header with two clinics, the Overview, Settings.
@Composable
internal fun ClinicOverviewPreview() = Workspace(ClinicUiState(previewClinics, stats = ClinicOverviewStats(4, 3, 12)))

@Composable
internal fun ClinicSettingsPreview() = Workspace(ClinicUiState(previewClinics, section = ClinicSection.Settings))

@Composable
internal fun ClinicEmptyPreview() = Workspace(ClinicUiState(ClinicsState(known = true)))

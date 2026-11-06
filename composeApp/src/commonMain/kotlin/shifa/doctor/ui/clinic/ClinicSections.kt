package shifa.doctor.ui.clinic

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import shifa.doctor.data.model.MyClinic
import shifa.doctor.ui.navigation.ShellActions
import shifa.doctor.ui.navigation.ShellRoute
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

/**
 * The workspace's sections other than Overview and Settings (M11): each ticket puts its screen here, with the shell's
 * actions for what opens outside the workspace (a patient, a visit, a doctor's schedule).
 */
@Composable
internal fun ColumnScope.ClinicSectionContent(
    section: ClinicSection,
    clinic: MyClinic,
    staff: Boolean,
    actions: ShellActions,
) {
    when (section) {
        ClinicSection.Doctors -> DoctorsSection(clinic)
        ClinicSection.Invitations -> InvitationsSection(clinic)
        ClinicSection.Patients ->
            ClinicPatientsSection(clinic, onOpen = { actions.push(ShellRoute.Patient(it.patientId, clinic.clinicId)) })
        else -> Text(tr(sectionLabel(section)), style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary)
    }
}

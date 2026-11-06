package shifa.doctor.ui.clinic

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1000dp-xhdpi")
class ClinicScreenshotTest {
    @Test
    fun overview() = captureRoboImage("screenshots/Clinic_overview.png") { ClinicOverviewPreview() }

    @Test
    fun settings() = captureRoboImage("screenshots/Clinic_settings.png") { ClinicSettingsPreview() }

    @Test
    fun empty() = captureRoboImage("screenshots/Clinic_empty.png") { ClinicEmptyPreview() }

    @Test
    fun doctors() = captureRoboImage("screenshots/Clinic_doctors.png") { ClinicDoctorsPreview() }

    @Test
    fun invitations() = captureRoboImage("screenshots/Clinic_invitations.png") { ClinicInvitationsPreview() }

    @Test
    fun patients() = captureRoboImage("screenshots/Clinic_patients.png") { ClinicPatientsPreview() }

    @Test
    fun patientsEmpty() = captureRoboImage("screenshots/Clinic_patients_empty.png") { ClinicPatientsEmptyPreview() }

    @Test
    fun financeDashboard() = captureRoboImage("screenshots/Clinic_finance_dashboard.png") { FinanceDashboardPreview() }

    @Test
    fun financeLedger() = captureRoboImage("screenshots/Clinic_finance_ledger.png") { FinanceLedgerPreview() }

    @Test
    fun financeInstallments() =
        captureRoboImage("screenshots/Clinic_finance_installments.png") {
            FinanceInstallmentsPreview()
        }

    @Test
    fun plans() = captureRoboImage("screenshots/Clinic_plans.png") { ClinicPlansPreview() }

    @Test
    fun planDetail() = captureRoboImage("screenshots/Clinic_plan_detail.png") { PlanDetailPreview() }

    @Test
    fun calendar() = captureRoboImage("screenshots/Clinic_calendar.png") { ClinicCalendarPreview() }

    @Test
    fun services() = captureRoboImage("screenshots/Clinic_services.png") { ClinicServicesPreview() }

    @Test
    fun serviceEditor() = captureRoboImage("screenshots/Clinic_service_editor.png") { ClinicServiceEditorPreview() }
}

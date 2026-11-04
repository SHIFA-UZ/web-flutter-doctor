package shifa.doctor.ui.patients

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class PatientsScreenshotTest {
    @Test
    @Config(qualifiers = "w402dp-h1100dp-xhdpi")
    fun list() = captureRoboImage("screenshots/Patients_list.png") { PatientsPreview() }

    @Test
    fun empty() = captureRoboImage("screenshots/Patients_empty.png") { PatientsEmptyPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun create() = captureRoboImage("screenshots/Patients_create.png") { CreatePatientPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun detail() = captureRoboImage("screenshots/PatientDetail_overview.png") { PatientDetailPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun detailSparse() = captureRoboImage("screenshots/PatientDetail_sparse.png") { PatientDetailSparsePreview() }

    @Test
    fun detailNotFound() = captureRoboImage("screenshots/PatientDetail_notFound.png") { PatientDetailNotFoundPreview() }

    @Test
    @Config(qualifiers = "w402dp-h2000dp-xhdpi")
    fun medical() = captureRoboImage("screenshots/PatientDetail_medical.png") { PatientMedicalPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun edit() = captureRoboImage("screenshots/Patients_edit.png") { EditPatientPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun appointments() = captureRoboImage("screenshots/PatientDetail_appointments.png") { PatientAppointmentsPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun activity() = captureRoboImage("screenshots/PatientDetail_activity.png") { PatientActivityPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun slotPick() =
        captureRoboImage(
            "screenshots/Patients_slotPick.png",
        ) { shifa.doctor.ui.calendar.SlotPickPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1600dp-xhdpi")
    fun documents() = captureRoboImage("screenshots/PatientDetail_documents.png") { PatientDocumentsPreview() }

    @Test
    fun forms() = captureRoboImage("screenshots/PatientDetail_forms.png") { PatientFormsPreview() }

    @Test
    fun formsEmpty() = captureRoboImage("screenshots/PatientDetail_formsEmpty.png") { PatientFormsEmptyPreview() }

    @Test
    fun formTemplate() = captureRoboImage("screenshots/FormTemplate.png") { FormTemplatePreview() }
}

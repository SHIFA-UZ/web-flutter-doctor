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
}

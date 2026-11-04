package shifa.doctor.ui.register

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class RegisterScreenshotTest {
    // The form scrolls; a tall screen shows all of it.
    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun form() = captureRoboImage("screenshots/Register_form.png") { RegisterFormPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1300dp-xhdpi")
    fun errors() = captureRoboImage("screenshots/Register_errors.png") { RegisterErrorsPreview() }

    @Test
    fun existingPatient() =
        captureRoboImage("screenshots/Register_existingPatient.png") {
            RegisterExistingPatientPreview()
        }

    @Test
    @Config(qualifiers = "w402dp-h1100dp-xhdpi")
    fun details() = captureRoboImage("screenshots/Register_details.png") { RegisterDetailsPreview() }

    @Test
    fun clinicPicker() = captureRoboImage("screenshots/Register_clinicPicker.png") { ClinicPickerPreview() }

    @Test
    fun clinicPickerFailed() =
        captureRoboImage("screenshots/Register_clinicPickerFailed.png") {
            ClinicPickerFailedPreview()
        }

    @Test
    fun professionPicker() = captureRoboImage("screenshots/Register_professionPicker.png") { ProfessionPickerPreview() }

    @Test
    fun noTimeZone() = captureRoboImage("screenshots/Register_noTimeZone.png") { RegisterNoTimeZonePreview() }

    @Test
    fun timeZonePicker() = captureRoboImage("screenshots/Register_timeZonePicker.png") { TimeZonePickerPreview() }
}

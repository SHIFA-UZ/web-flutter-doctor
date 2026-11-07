package shifa.doctor.ui.emailcode

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ResetPasswordScreenshotTest {
    @Test
    fun checklist() = captureRoboImage("screenshots/ResetPassword_checklist.png") { ResetChecklistPreview() }

    @Test
    fun mismatch() = captureRoboImage("screenshots/ResetPassword_mismatch.png") { ResetMismatchPreview() }

    @Test
    fun `wrong code`() = captureRoboImage("screenshots/ResetPassword_wrong_code.png") { ResetWrongCodePreview() }
}

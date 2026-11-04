package shifa.doctor.ui.profile

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ProfileScreenshotTest {
    @Test
    @Config(qualifiers = "w402dp-h1200dp-xhdpi")
    fun root() = captureRoboImage("screenshots/Profile_root.png") { ProfileRootPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1200dp-xhdpi")
    fun incomplete() = captureRoboImage("screenshots/Profile_incomplete.png") { ProfileIncompletePreview() }

    @Test
    fun staff() = captureRoboImage("screenshots/Profile_staff.png") { ProfileStaffPreview() }

    @Test
    fun error() = captureRoboImage("screenshots/Profile_error.png") { ProfileErrorPreview() }

    @Test
    fun personalInfo() = captureRoboImage("screenshots/PersonalInfo_edited.png") { PersonalInfoPreview() }

    @Test
    fun personalInfoErrors() = captureRoboImage("screenshots/PersonalInfo_errors.png") { PersonalInfoErrorsPreview() }
}

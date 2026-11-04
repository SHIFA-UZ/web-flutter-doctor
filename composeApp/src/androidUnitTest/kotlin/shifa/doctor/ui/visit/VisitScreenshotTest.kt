package shifa.doctor.ui.visit

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class VisitScreenshotTest {
    @Test
    @Config(qualifiers = "w402dp-h1600dp-xhdpi")
    fun workspace() = captureRoboImage("screenshots/Visit_workspace.png") { VisitWorkspacePreview() }

    @Test
    fun form0252() = captureRoboImage("screenshots/Visit_form0252.png") { VisitForm0252Preview() }

    @Test
    fun staff() = captureRoboImage("screenshots/Visit_staff.png") { VisitStaffPreview() }
}

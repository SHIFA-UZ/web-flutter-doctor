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

    @Test
    fun briefingReady() = captureRoboImage("screenshots/Briefing_ready.png") { BriefingReadyPreview() }

    @Test
    fun briefingFailed() = captureRoboImage("screenshots/Briefing_failed.png") { BriefingFailedPreview() }

    @Test
    fun endConfirm() = captureRoboImage("screenshots/End_confirm.png") { EndConfirmPreview() }

    @Test
    fun endProgress() = captureRoboImage("screenshots/End_progress.png") { EndProgressPreview() }

    @Test
    fun toothMap() = captureRoboImage("screenshots/ToothMap.png") { ToothMapPreview() }

    @Test
    @Config(qualifiers = "w720dp-h874dp-xhdpi")
    fun toothMapWide() = captureRoboImage("screenshots/ToothMap_wide.png") { ToothMapPreview() }
}

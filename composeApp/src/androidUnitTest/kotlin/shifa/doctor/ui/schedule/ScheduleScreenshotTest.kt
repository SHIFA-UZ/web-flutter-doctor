package shifa.doctor.ui.schedule

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ScheduleScreenshotTest {
    @Test
    fun locations() = captureRoboImage("screenshots/Schedule_locations.png") { LocationsPreview() }

    @Test
    fun locationEditor() = captureRoboImage("screenshots/Schedule_locationEditor.png") { LocationEditorPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun schedule() = captureRoboImage("screenshots/Schedule_week.png") { SchedulePreview() }
}

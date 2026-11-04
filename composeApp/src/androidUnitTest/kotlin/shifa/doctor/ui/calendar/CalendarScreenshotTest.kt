package shifa.doctor.ui.calendar

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class CalendarScreenshotTest {
    @Test
    fun monthPicker() = captureRoboImage("screenshots/Calendar_monthPicker.png") { MonthPickerPreview() }
}

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

    @Test
    @Config(qualifiers = "w402dp-h1400dp-xhdpi")
    fun day() = captureRoboImage("screenshots/Calendar_day.png") { CalendarDayPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1500dp-xhdpi")
    fun details() = captureRoboImage("screenshots/Calendar_details.png") { CalendarDetailsPreview() }

    @Test
    fun appointmentPanel() = captureRoboImage("screenshots/Calendar_appointmentPanel.png") { AppointmentPanelPreview() }

    @Test
    fun appointmentPanelCompleted() =
        captureRoboImage("screenshots/Calendar_appointmentPanelCompleted.png") { AppointmentPanelCompletedPreview() }

    @Test
    fun booking() = captureRoboImage("screenshots/Calendar_booking.png") { BookingPreview() }

    @Test
    fun bookingPast() = captureRoboImage("screenshots/Calendar_bookingPast.png") { BookingPastPreview() }
}

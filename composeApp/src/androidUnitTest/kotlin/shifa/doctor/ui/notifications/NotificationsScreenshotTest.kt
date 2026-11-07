package shifa.doctor.ui.notifications

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class NotificationsScreenshotTest {
    @Test
    fun inbox() = captureRoboImage("screenshots/Notifications_inbox.png") { NotificationsPreview() }

    @Test
    fun loading() = captureRoboImage("screenshots/Notifications_loading.png") { NotificationsLoadingPreview() }

    @Test
    fun empty() = captureRoboImage("screenshots/Notifications_empty.png") { NotificationsEmptyPreview() }

    @Test
    fun filterEmpty() =
        captureRoboImage(
            "screenshots/Notifications_filterEmpty.png",
        ) { NotificationsFilterEmptyPreview() }

    @Test
    fun staff() = captureRoboImage("screenshots/Notifications_staff.png") { NotificationsStaffPreview() }
}

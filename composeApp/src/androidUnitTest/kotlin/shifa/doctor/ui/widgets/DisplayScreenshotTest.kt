package shifa.doctor.ui.widgets

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// One image per preview in DisplayPreviews.kt; record with :composeApp:recordRoborazziDebug.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class DisplayScreenshotTest {
    @Test
    fun badges() = captureRoboImage("screenshots/Display_badges.png") { BadgesPreview() }

    @Test
    fun cards() = captureRoboImage("screenshots/Display_cards.png") { CardsPreview() }

    @Test
    fun kpis() = captureRoboImage("screenshots/Display_kpis.png") { KpisPreview() }

    @Test
    fun sections_and_headers() =
        captureRoboImage("screenshots/Display_sections_and_headers.png") {
            SectionsAndHeadersPreview()
        }

    @Test
    fun tabs() = captureRoboImage("screenshots/Display_tabs.png") { TabsPreview() }

    @Test
    fun notifications() = captureRoboImage("screenshots/Display_notifications.png") { NotificationsPreview() }

    @Test
    fun chat() = captureRoboImage("screenshots/Display_chat.png") { ChatPreview() }

    @Test
    fun nav_bar() = captureRoboImage("screenshots/Display_nav_bar.png") { NavBarPreview() }
}

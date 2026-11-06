package shifa.doctor.ui.care

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1000dp-xhdpi")
class CareScreenshotTest {
    @Test
    fun find() = captureRoboImage("screenshots/Care_find.png") { FindPartnerPreview() }

    @Test
    fun findEmpty() = captureRoboImage("screenshots/Care_find_empty.png") { FindPartnerEmptyPreview() }

    @Test
    fun list() = captureRoboImage("screenshots/Care_list.png") { PartnershipsPreview() }

    @Test
    fun listEmpty() = captureRoboImage("screenshots/Care_list_empty.png") { PartnershipsEmptyPreview() }

    @Test
    fun invite() = captureRoboImage("screenshots/Care_invite.png") { PartnershipInvitePreview() }

    @Test
    fun active() = captureRoboImage("screenshots/Care_active.png") { PartnershipActivePreview() }
}

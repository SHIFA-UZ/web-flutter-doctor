package shifa.doctor.ui.navigation

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class ShellScreenshotTest {
    @Test
    fun home() = captureRoboImage("screenshots/Shell_home.png") { ShellHomePreview() }

    @Test
    fun moreTab() = captureRoboImage("screenshots/Shell_moreTab.png") { ShellMoreTabPreview() }

    @Test
    @Config(qualifiers = "w402dp-h640dp-xhdpi")
    fun morePremium() = captureRoboImage("screenshots/Shell_morePremium.png") { MoreSheetPremiumPreview() }

    @Test
    @Config(qualifiers = "w402dp-h640dp-xhdpi")
    fun moreBasic() = captureRoboImage("screenshots/Shell_moreBasic.png") { MoreSheetBasicPreview() }

    @Test
    fun staff() = captureRoboImage("screenshots/Shell_staff.png") { ShellStaffPreview() }
}

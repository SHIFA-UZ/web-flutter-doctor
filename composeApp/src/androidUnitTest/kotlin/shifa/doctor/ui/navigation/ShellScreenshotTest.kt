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

    @Test
    fun intelligence() = captureRoboImage("screenshots/Shell_intelligence.png") { ShellIntelligencePreview() }

    @Test
    @Config(qualifiers = "w1440dp-h960dp-mdpi")
    fun sidebar() = captureRoboImage("screenshots/Shell_sidebar.png") { ShellSidebarPreview() }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-mdpi")
    fun rail() = captureRoboImage("screenshots/Shell_rail.png") { ShellRailPreview() }

    @Test
    @Config(qualifiers = "w1440dp-h960dp-mdpi")
    fun staffSidebar() = captureRoboImage("screenshots/Shell_staffSidebar.png") { ShellStaffSidebarPreview() }
}

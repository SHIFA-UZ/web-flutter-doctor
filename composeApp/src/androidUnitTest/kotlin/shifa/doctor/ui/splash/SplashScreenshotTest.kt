package shifa.doctor.ui.splash

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class SplashScreenshotTest {
    @Test
    fun loading() = captureRoboImage("screenshots/Splash_loading.png") { SplashLoadingPreview() }

    @Test
    fun update() = captureRoboImage("screenshots/Splash_update.png") { SplashUpdatePreview() }

    @Test
    fun maintenance() = captureRoboImage("screenshots/Splash_maintenance.png") { SplashMaintenancePreview() }

    @Test
    fun `maintenance retrying`() =
        captureRoboImage("screenshots/Splash_maintenance_retrying.png") {
            SplashMaintenanceRetryingPreview()
        }
}

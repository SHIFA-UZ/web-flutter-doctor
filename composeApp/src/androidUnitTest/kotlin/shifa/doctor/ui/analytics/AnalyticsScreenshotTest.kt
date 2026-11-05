package shifa.doctor.ui.analytics

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1100dp-xhdpi")
class AnalyticsScreenshotTest {
    @Test
    fun loaded() = captureRoboImage("screenshots/Analytics_loaded.png") { AnalyticsLoadedPreview() }

    @Test
    fun loading() = captureRoboImage("screenshots/Analytics_loading.png") { AnalyticsLoadingPreview() }

    @Test
    fun teaser() = captureRoboImage("screenshots/Analytics_teaser.png") { AnalyticsTeaserPreview() }
}

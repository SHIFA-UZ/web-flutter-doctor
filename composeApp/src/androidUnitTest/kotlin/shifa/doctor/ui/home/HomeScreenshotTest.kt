package shifa.doctor.ui.home

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class HomeScreenshotTest {
    @Test
    fun loading() = captureRoboImage("screenshots/Home_loading.png") { HomeLoadingPreview() }

    @Test
    fun loaded() = captureRoboImage("screenshots/Home_loaded.png") { HomeLoadedPreview() }

    @Test
    fun largeText() = captureRoboImage("screenshots/Home_largeText.png") { HomeLargeTextPreview() }
}

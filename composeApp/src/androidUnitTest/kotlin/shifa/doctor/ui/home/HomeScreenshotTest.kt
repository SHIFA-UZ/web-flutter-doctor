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

    @Test
    fun heroLater() = captureRoboImage("screenshots/Home_heroLater.png") { HomeHeroLaterPreview() }

    @Test
    fun done() = captureRoboImage("screenshots/Home_done.png") { HomeDonePreview() }

    @Test
    fun noZone() = captureRoboImage("screenshots/Home_noZone.png") { HomeNoZonePreview() }

    @Test
    fun search() = captureRoboImage("screenshots/Home_search.png") { PatientSearchPreview() }

    @Test
    fun searchEmpty() = captureRoboImage("screenshots/Home_searchEmpty.png") { PatientSearchEmptyPreview() }
}

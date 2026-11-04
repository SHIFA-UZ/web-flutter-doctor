package shifa.doctor.ui.forms

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class FormsScreenshotTest {
    @Test
    fun clinicalEmpty() = captureRoboImage("screenshots/Clinical_empty.png") { ClinicalPanelEmptyPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1400dp-xhdpi")
    fun clinicalDisease() = captureRoboImage("screenshots/Clinical_disease.png") { ClinicalPanelDiseasePreview() }
}

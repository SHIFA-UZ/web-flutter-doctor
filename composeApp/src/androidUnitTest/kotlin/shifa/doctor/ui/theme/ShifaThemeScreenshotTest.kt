package shifa.doctor.ui.theme

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class ShifaThemeScreenshotTest {
    @Test
    fun colors() = captureRoboImage("screenshots/Theme_colors.png") { ColorsPreview() }

    @Test
    fun `alpha and raw`() = captureRoboImage("screenshots/Theme_alpha_raw.png") { AlphaAndRawPreview() }

    @Test
    fun typography() = captureRoboImage("screenshots/Theme_typography.png") { TypographyPreview() }

    @Test
    fun shapes() = captureRoboImage("screenshots/Theme_shapes.png") { ShapesPreview() }
}

package shifa.doctor.ui.register

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class VerifyKeyScreenshotTest {
    @Test
    fun empty() = captureRoboImage("screenshots/VerifyKey_empty.png") { VerifyKeyEmptyPreview() }

    @Test
    fun invalid() = captureRoboImage("screenshots/VerifyKey_invalid.png") { VerifyKeyInvalidPreview() }

    @Test
    fun failed() = captureRoboImage("screenshots/VerifyKey_failed.png") { VerifyKeyFailedPreview() }
}

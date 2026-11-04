package shifa.doctor.ui.auth

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h480dp-xhdpi")
class SignOutScreenshotTest {
    @Test
    fun question() = captureRoboImage("screenshots/SignOut_question.png") { SignOutSheetPreview() }
}

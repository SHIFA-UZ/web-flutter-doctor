package shifa.doctor.ui.login

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class LoginScreenshotTest {
    @Test
    fun empty() = captureRoboImage("screenshots/Login_empty.png") { LoginEmptyPreview() }

    @Test
    fun errors() = captureRoboImage("screenshots/Login_errors.png") { LoginErrorsPreview() }

    @Test
    fun loading() = captureRoboImage("screenshots/Login_loading.png") { LoginLoadingPreview() }

    @Test
    fun failed() = captureRoboImage("screenshots/Login_failed.png") { LoginFailedPreview() }

    @Test
    fun `session expired`() = captureRoboImage("screenshots/Login_session_expired.png") { LoginSessionExpiredPreview() }
}

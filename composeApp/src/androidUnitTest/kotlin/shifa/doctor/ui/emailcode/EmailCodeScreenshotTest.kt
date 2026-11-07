package shifa.doctor.ui.emailcode

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class EmailCodeScreenshotTest {
    @Test
    fun email() = captureRoboImage("screenshots/EmailCode_email.png") { EmailEntryPreview() }

    @Test
    fun `email reset error`() =
        captureRoboImage("screenshots/EmailCode_email_reset_error.png") {
            EmailEntryResetErrorPreview()
        }

    @Test
    fun `code countdown`() = captureRoboImage("screenshots/EmailCode_code_countdown.png") { CodeCountdownPreview() }

    @Test
    fun `code wrong`() = captureRoboImage("screenshots/EmailCode_code_wrong.png") { CodeWrongPreview() }

    @Test
    fun `code resent`() = captureRoboImage("screenshots/EmailCode_code_resent.png") { CodeResentPreview() }

    @Test
    fun phone() = captureRoboImage("screenshots/EmailCode_phone.png") { PhoneEntryPreview() }

    @Test
    fun `phone code`() = captureRoboImage("screenshots/EmailCode_phone_code.png") { PhoneCodePreview() }
}

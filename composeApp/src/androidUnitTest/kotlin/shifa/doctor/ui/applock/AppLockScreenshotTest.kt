package shifa.doctor.ui.applock

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h874dp-xhdpi")
class AppLockScreenshotTest {
    @Test
    fun lock() = captureRoboImage("screenshots/AppLock_lock.png") { LockScreenPreview() }

    @Test
    fun lockWrong() = captureRoboImage("screenshots/AppLock_lockWrong.png") { LockScreenWrongPreview() }

    @Test
    @Config(qualifiers = "w402dp-h420dp-xhdpi")
    fun section() = captureRoboImage("screenshots/AppLock_section.png") { AppLockSectionPreview() }

    @Test
    @Config(qualifiers = "w402dp-h420dp-xhdpi")
    fun sectionNoBiometrics() =
        captureRoboImage("screenshots/AppLock_sectionNoBiometrics.png") {
            AppLockSectionNoBiometricsPreview()
        }

    @Test
    @Config(qualifiers = "w402dp-h480dp-xhdpi")
    fun pinSheet() = captureRoboImage("screenshots/AppLock_pinSheet.png") { PinSheetPreview() }
}

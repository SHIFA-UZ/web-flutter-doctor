package shifa.doctor.ui.widgets

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// One image per preview in ControlsPreviews.kt; record with :composeApp:recordRoborazziDebug.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class ControlsScreenshotTest {
    @Test
    fun buttons() = captureRoboImage("screenshots/Controls_buttons.png") { ButtonsPreview() }

    @Test
    fun action_and_icon_buttons() =
        captureRoboImage("screenshots/Controls_action_and_icon_buttons.png") {
            ActionAndIconButtonsPreview()
        }

    @Test
    fun link_fab_spinner() = captureRoboImage("screenshots/Controls_link_fab_spinner.png") { LinkFabSpinnerPreview() }

    @Test
    fun inputs() = captureRoboImage("screenshots/Controls_inputs.png") { InputsPreview() }

    @Test
    fun choices() = captureRoboImage("screenshots/Controls_choices.png") { ChoicesPreview() }

    @Test
    fun otp_and_pin() = captureRoboImage("screenshots/Controls_otp_and_pin.png") { OtpAndPinPreview() }

    @Test
    fun slider_and_stepper() =
        captureRoboImage(
            "screenshots/Controls_slider_and_stepper.png",
        ) { SliderAndStepperPreview() }

    @Test
    fun icons() = captureRoboImage("screenshots/Controls_icons.png") { IconsPreview() }
}

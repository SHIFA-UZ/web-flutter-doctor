package shifa.doctor.ui.widgets

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// One image per preview in FeedbackPreviews.kt; record with :composeApp:recordRoborazziDebug.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class FeedbackScreenshotTest {
    @Test
    fun banners() = captureRoboImage("screenshots/Feedback_banners.png") { BannersPreview() }

    @Test
    fun toasts() = captureRoboImage("screenshots/Feedback_toasts.png") { ToastsPreview() }

    @Test
    fun empty_and_skeletons() =
        captureRoboImage("screenshots/Feedback_empty_and_skeletons.png") {
            EmptyAndSkeletonsPreview()
        }

    @Test
    fun dialog_destructive() =
        captureRoboImage("screenshots/Feedback_dialog_destructive.png") {
            DialogDestructivePreview()
        }

    @Test
    fun dialog_form() = captureRoboImage("screenshots/Feedback_dialog_form.png") { DialogFormPreview() }

    @Test
    fun dialog_briefing() = captureRoboImage("screenshots/Feedback_dialog_briefing.png") { DialogBriefingPreview() }

    @Test
    fun trays() = captureRoboImage("screenshots/Feedback_trays.png") { TraysPreview() }
}

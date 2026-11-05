package shifa.doctor.ui.intelligence

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1000dp-xhdpi")
class IntelligenceScreenshotTest {
    @Test
    fun history() = captureRoboImage("screenshots/Intelligence_history.png") { IntelligenceHistoryPreview() }

    @Test
    fun historyEmpty() =
        captureRoboImage(
            "screenshots/Intelligence_historyEmpty.png",
        ) { IntelligenceHistoryEmptyPreview() }

    @Test
    fun threadStreaming() =
        captureRoboImage(
            "screenshots/Intelligence_threadStreaming.png",
        ) { ThreadStreamingPreview() }

    @Test
    fun threadError() = captureRoboImage("screenshots/Intelligence_threadError.png") { ThreadErrorPreview() }

    @Test
    fun welcome() = captureRoboImage("screenshots/Intelligence_welcome.png") { WelcomePreview() }

    @Test
    fun welcomeFirst() = captureRoboImage("screenshots/Intelligence_welcomeFirst.png") { WelcomeFirstPreview() }

    @Test
    @Config(qualifiers = "w402dp-h2400dp-xhdpi")
    fun cards() = captureRoboImage("screenshots/Intelligence_cards.png") { CardsPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1400dp-xhdpi")
    fun bookingCards() = captureRoboImage("screenshots/Intelligence_bookingCards.png") { BookingCardsPreview() }

    @Test
    @Config(qualifiers = "w402dp-h1400dp-xhdpi")
    fun clinicalCards() = captureRoboImage("screenshots/Intelligence_clinicalCards.png") { ClinicalCardsPreview() }

    @Test
    fun cancelCards() = captureRoboImage("screenshots/Intelligence_cancelCards.png") { CancelCardsPreview() }
}

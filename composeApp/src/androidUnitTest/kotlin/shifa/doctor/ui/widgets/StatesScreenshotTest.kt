package shifa.doctor.ui.widgets

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// One image per preview in StatesPreviews.kt; record with :composeApp:recordRoborazziDebug.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w402dp-h1600dp-xhdpi")
class StatesScreenshotTest {
    @Test
    fun hero_idle() = captureRoboImage("screenshots/States_hero_idle.png") { HeroIdlePreview() }

    @Test
    fun hero_loading() = captureRoboImage("screenshots/States_hero_loading.png") { HeroLoadingPreview() }

    @Test
    fun hero_ready() = captureRoboImage("screenshots/States_hero_ready.png") { HeroReadyPreview() }

    @Test
    fun hero_started() = captureRoboImage("screenshots/States_hero_started.png") { HeroStartedPreview() }

    @Test
    fun hero_error() = captureRoboImage("screenshots/States_hero_error.png") { HeroErrorPreview() }

    @Test
    fun hero_empty() = captureRoboImage("screenshots/States_hero_empty.png") { HeroEmptyPreview() }

    @Test
    fun insight_suggestion() =
        captureRoboImage(
            "screenshots/States_insight_suggestion.png",
        ) { InsightSuggestionPreview() }

    @Test
    fun insight_risk() = captureRoboImage("screenshots/States_insight_risk.png") { InsightRiskPreview() }

    @Test
    fun insight_briefing() = captureRoboImage("screenshots/States_insight_briefing.png") { InsightBriefingPreview() }

    @Test
    fun insight_loading() = captureRoboImage("screenshots/States_insight_loading.png") { InsightLoadingPreview() }

    @Test
    fun insight_voice() = captureRoboImage("screenshots/States_insight_voice.png") { InsightVoicePreview() }

    @Test
    fun insight_outcome() = captureRoboImage("screenshots/States_insight_outcome.png") { InsightOutcomePreview() }

    @Test
    fun copilot_empty() = captureRoboImage("screenshots/States_copilot_empty.png") { CopilotEmptyPreview() }

    @Test
    fun copilot_streaming() = captureRoboImage("screenshots/States_copilot_streaming.png") { CopilotStreamingPreview() }

    @Test
    fun copilot_answered() = captureRoboImage("screenshots/States_copilot_answered.png") { CopilotAnsweredPreview() }

    @Test
    fun copilot_phone_tray() =
        captureRoboImage(
            "screenshots/States_copilot_phone_tray.png",
        ) { CopilotPhoneTrayPreview() }

    @Test
    fun copilot_minimised() = captureRoboImage("screenshots/States_copilot_minimised.png") { CopilotMinimisedPreview() }

    @Test
    fun timeline_now() = captureRoboImage("screenshots/States_timeline_now.png") { TimelineNowPreview() }

    @Test
    fun timeline_started() = captureRoboImage("screenshots/States_timeline_started.png") { TimelineStartedPreview() }

    @Test
    fun timeline_empty() = captureRoboImage("screenshots/States_timeline_empty.png") { TimelineEmptyPreview() }
}

package shifa.doctor.ui.widgets

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Flutter's test/shifa_ai_orb_test.dart (the intensity rule); the panel's look is in VoiceScreenshotTest.
class VoiceOrbTest {
    @Test
    fun `listening energy stays quiet under the noise floor`() {
        assertThat(orbIntensity(VoicePhase.Listening, 0.1f, reduceMotion = false)).isEqualTo(0.08f)
        assertThat(orbIntensity(VoicePhase.Listening, Float.NaN, reduceMotion = false)).isEqualTo(0.08f)
    }

    @Test
    fun `listening energy scales above the noise floor and clamps`() {
        val mid = orbIntensity(VoicePhase.Listening, 0.58f, reduceMotion = false)
        assertThat(mid).isGreaterThan(0.08f)
        assertThat(mid).isLessThan(1f)
        assertThat(orbIntensity(VoicePhase.Listening, 4f, reduceMotion = false)).isEqualTo(1f)
    }

    @Test
    fun `speaking and thinking ignore microphone level`() {
        assertThat(orbIntensity(VoicePhase.Speaking, 0.9f, reduceMotion = false)).isEqualTo(0.46f)
        assertThat(orbIntensity(VoicePhase.Thinking, 0.9f, reduceMotion = false)).isEqualTo(0.16f)
        assertThat(orbIntensity(VoicePhase.Idle, 0.9f, reduceMotion = false)).isEqualTo(0.06f)
    }

    @Test
    fun `reduced motion keeps low fixed energy`() {
        assertThat(orbIntensity(VoicePhase.Listening, 1f, reduceMotion = true)).isEqualTo(0.2f)
        assertThat(orbIntensity(VoicePhase.Speaking, 1f, reduceMotion = true)).isEqualTo(0.28f)
        assertThat(orbIntensity(VoicePhase.Thinking, 1f, reduceMotion = true)).isEqualTo(0.12f)
        assertThat(orbIntensity(VoicePhase.Idle, 1f, reduceMotion = true)).isEqualTo(0.05f)
    }
}

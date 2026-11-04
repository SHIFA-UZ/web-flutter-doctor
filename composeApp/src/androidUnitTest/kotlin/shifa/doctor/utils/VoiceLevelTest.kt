package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceLevelTest {
    @Test
    fun `the level is Flutter's bar height, from 0_08 at -55 dB to 1 at -8 dB`() {
        assertThat(levelFromDb(-160.0)).isWithin(0.0001f).of(0.08f)
        assertThat(levelFromDb(-55.0)).isWithin(0.0001f).of(0.08f)
        assertThat(levelFromDb(-31.5)).isWithin(0.001f).of(0.54f)
        assertThat(levelFromDb(-8.0)).isEqualTo(1f)
        assertThat(levelFromDb(0.0)).isEqualTo(1f)
        assertThat(levelFromDb(Double.NaN)).isEqualTo(0.1f)
    }

    @Test
    fun `recording ids are new each time`() {
        val a = newRecordingId()
        assertThat(a).matches("rec-[0-9a-f]{16}")
        assertThat(newRecordingId()).isNotEqualTo(a)
    }
}

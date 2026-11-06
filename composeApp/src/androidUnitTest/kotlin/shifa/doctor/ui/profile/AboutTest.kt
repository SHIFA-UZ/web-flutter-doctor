package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AboutTest {
    @Test
    fun `diagnostics carry the build and environment only`() {
        assertThat(diagnostics("1.0.1 (12)", "android release", "production", "uz-Cyrl", "Asia/Tashkent"))
            .isEqualTo("Shifa Doctor 1.0.1 (12) · android release · production · uz-Cyrl · Asia/Tashkent")
        assertThat(diagnostics("1.0.1 (4)", "ios debug", "demo", "en", null))
            .isEqualTo("Shifa Doctor 1.0.1 (4) · ios debug · demo · en · -")
        assertThat(PRIVACY_URL).isEqualTo("https://shifa.uz/#privacy")
    }
}

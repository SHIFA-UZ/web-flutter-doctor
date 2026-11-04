package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.api.jwt

class JwtTest {
    @Test
    fun `reads exp and role, upper-cased`() {
        val token = jwt(1_000, role = "clinic_staff")
        assertThat(Jwt.expiresAtEpochSeconds(token)).isEqualTo(1_000)
        assertThat(Jwt.role(token)).isEqualTo("CLINIC_STAFF")
    }

    @Test
    fun `expired once exp plus the grace has passed, and unreadable tokens count as expired`() {
        assertThat(Jwt.isExpired(jwt(1_000), nowEpochSeconds = 1_030, graceSeconds = 30)).isFalse()
        assertThat(Jwt.isExpired(jwt(1_000), nowEpochSeconds = 1_031, graceSeconds = 30)).isTrue()
        assertThat(Jwt.isExpired("garbage", nowEpochSeconds = 1_000, graceSeconds = 30)).isTrue()
        assertThat(Jwt.role(null)).isNull()
    }

    @Test
    fun `claims of the wrong type read as missing instead of throwing`() {
        val token = "e30.eyJleHAiOnt9LCJyb2xlIjpbXX0.sig" // {"exp":{},"role":[]}
        assertThat(Jwt.expiresAtEpochSeconds(token)).isNull()
        assertThat(Jwt.role(token)).isNull()
        assertThat(Jwt.isExpired(token, 0, 30)).isTrue()
    }
}

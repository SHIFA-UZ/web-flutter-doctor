package shifa.doctor.data.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.model.ApiException

class TrackPropertiesTest {
    @Test
    fun `values keep their type, nulls and other objects are left out`() {
        val properties =
            trackProperties(
                listOf(
                    "s" to "x",
                    "i" to 1,
                    "l" to 2L,
                    "d" to 1.5,
                    "b" to true,
                    "e" to SessionEndReason.SignedOut,
                    "list" to listOf("a", SessionEndReason.Expired, null, Any()),
                    "none" to null,
                    "object" to Any(),
                    "float" to 1f,
                ),
            )

        assertThat(properties)
            .containsExactly(
                "s", "x", "i", 1, "l", 2L, "d", 1.5, "b", true, "e", "signed_out", "list",
                listOf(
                    "a",
                    "expired",
                ),
            )
    }

    @Test
    fun `an enum constant becomes a snake_case word`() {
        assertThat(SessionEndReason.entries.map { it.trackName() })
            .containsExactly(
                "expired",
                "unauthorized",
                "account_disabled",
                "signed_out",
                "pin_lockout",
                "inactivity",
                "account_deleted",
            )
        assertThat(Sample.CARD_EXTERNAL.trackName()).isEqualTo("card_external")
        assertThat(Sample.Form0252.trackName()).isEqualTo("form0252")
    }

    @Test
    fun `counts become ranges`() {
        assertThat(listOf(-1, 0, 1, 2, 5, 6, 20, 21, 100, 101).map(::countBucket))
            .containsExactly("0", "0", "1", "2-5", "2-5", "6-20", "6-20", "21-100", "21-100", ">100")
            .inOrder()
    }

    @Test
    fun `money becomes a range in sums, never the amount`() {
        fun sums(amount: Long) = amountBucket(amount * 100, "UZS")

        assertThat(listOf(0L, 99_999, 100_000, 499_999, 500_000, 1_000_000, 5_000_000, 20_000_000).map(::sums))
            .containsExactly("0", "<100k", "100k-500k", "100k-500k", "500k-1m", "1m-5m", "5m-20m", ">20m")
            .inOrder()
        assertThat(amountBucket(-25_000_000, "uzs")).isEqualTo("100k-500k")
        assertThat(amountBucket(1_000, "USD")).isEqualTo("foreign")
        assertThat(amountBucket(null, "UZS")).isNull()
    }

    @Test
    fun `a failure becomes one of a few fixed words`() {
        fun status(
            status: Int,
            code: String? = null,
        ) = ApiException.of(status, code, "req-1").toFailureReason()

        assertThat(ApiException.NetworkError(timeout = false).toFailureReason()).isEqualTo("network")
        assertThat(ApiException.NetworkError(timeout = true).toFailureReason()).isEqualTo("timeout")
        assertThat(listOf(400, 422, 401, 403, 404, 409, 429, 500, 503).map { status(it) })
            .containsExactly(
                "validation",
                "validation",
                "unauthorized",
                "forbidden",
                "not_found",
                "conflict",
                "rate_limited",
                "server",
                "server",
            ).inOrder()
        assertThat(IllegalStateException("Aziz Karimov").toFailureReason()).isEqualTo("client")
    }

    @Test
    fun `a code that means the same everywhere wins over the status, any other code is not sent`() {
        fun code(
            code: String,
            status: Int = 400,
        ) = ApiException.of(status, code, null).toFailureReason()

        assertThat(code("RATE_LIMITED")).isEqualTo("rate_limited")
        assertThat(code("ACCOUNT_DISABLED", 403)).isEqualTo("account_disabled")
        assertThat(listOf("SESSION_EXPIRED", "SESSION_INVALID", "TOKEN_INVALID").map { code(it, 401) }.distinct())
            .containsExactly("session_expired")
        assertThat(code("UNAVAILABLE", 503)).isEqualTo("maintenance")
        assertThat(code("NO_SHOW_NOT_ALLOWED", 409)).isEqualTo("conflict")
    }

    @Suppress("ktlint:standard:enum-entry-name-case")
    private enum class Sample { CARD_EXTERNAL, Form0252 }
}

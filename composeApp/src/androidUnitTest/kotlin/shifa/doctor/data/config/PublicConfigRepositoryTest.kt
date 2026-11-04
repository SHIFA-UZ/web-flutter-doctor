@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.config

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient

class PublicConfigRepositoryTest {
    private fun repo(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        version: String = "1.0.1",
        platform: String = "android",
        wait: Long = 0,
    ) = PublicConfigRepository(
        {
            createHttpClient(
                { null },
                MockEngine {
                    delay(wait)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        },
        version,
        platform,
        timeoutMs = 200,
    )

    private val today = """{"googleMapsApiKey":"x","transcriptionFeedbackEnabled":"false"}""" // dev, 2026-11-04

    @Test
    fun `today's config has no gate`() =
        runTest {
            assertThat(repo(today).bootGate("en").getOrNull()).isEqualTo(BootGate.None)
        }

    @Test
    fun `an older build is sent to its store`() =
        runTest {
            val body = """{"doctorApp":{"minVersion":{"android":"1.0.2","ios":"1.0.0"},"storeUrl":{"android":"market://x","ios":"itms://y"}}}"""
            assertThat(repo(body).bootGate("en").getOrNull()).isEqualTo(BootGate.Update("market://x"))
            assertThat(repo(body, platform = "ios").bootGate("en").getOrNull()).isEqualTo(BootGate.None)
            assertThat(repo(body, version = "1.0.2-debug").bootGate("en").getOrNull()).isEqualTo(BootGate.None)
        }

    @Test
    fun `maintenance wins and speaks the app language, else the app's own copy`() =
        runTest {
            val body = """{"doctorApp":{"maintenance":{"active":true,"message":{"en":"Back at 10","ru":"Вернёмся в 10"}}}}"""
            assertThat(repo(body).bootGate("ru").getOrNull()).isEqualTo(BootGate.Maintenance("Вернёмся в 10"))
            assertThat(repo(body).bootGate("uz").getOrNull()).isEqualTo(BootGate.Maintenance("Back at 10"))
            val bare = """{"doctorApp":{"maintenance":{"active":true}}}"""
            assertThat(repo(bare).bootGate("en").getOrNull()).isEqualTo(BootGate.Maintenance(null))
        }

    @Test
    fun `failures and slowness come back as failures (the splash ignores them)`() =
        runTest {
            assertThat(repo("<html>", HttpStatusCode.BadGateway).bootGate("en").isFailure).isTrue()
            assertThat(repo(today, wait = 1_000).bootGate("en").isFailure).isTrue()
        }

    @Test
    fun `version order`() {
        assertThat(Versions.isOlder("1.0.1", "1.0.10")).isTrue()
        assertThat(Versions.isOlder("1.2", "1.1.9")).isFalse()
        assertThat(Versions.isOlder("1.0.1-debug", "1.0.1")).isFalse()
        assertThat(Versions.isOlder("1.0", "1.0.0")).isFalse()
    }
}

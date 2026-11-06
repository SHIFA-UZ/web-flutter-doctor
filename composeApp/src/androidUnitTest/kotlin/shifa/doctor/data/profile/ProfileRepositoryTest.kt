@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.profile

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.DoctorProfile
import shifa.doctor.data.model.DoctorSettings
import shifa.doctor.data.model.ProfileUpdateRequest
import java.io.File

class ProfileRepositoryTest {
    // The scrubbed dev answer of GET /doctors/me (same keys and types as dev, 2026-11-04).
    private val me = File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.OK
    private val classToTest =
        ProfileRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    val body = req.body.toByteArray().decodeToString()
                    sent += "${req.method.value} ${req.url.encodedPath} $body".trim()
                    val reply = if (req.method == HttpMethod.Get) me else body
                    respond(reply, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Test
    fun `the profile is read into typed blocks and shared`() =
        runTest {
            val read = classToTest.refresh().getOrThrow()
            assertThat(read.profile.firstName).isEqualTo("Aziza")
            assertThat(read.profile.timeZone).isEqualTo("Asia/Tashkent")
            assertThat(read.settings.defaultStartTab).isEqualTo("home")
            assertThat(read.subscription?.tier).isEqualTo("PREMIUM")
            assertThat(classToTest.me.value).isEqualTo(read)
        }

    @Test
    fun `settings go whole and update the shared profile, a failure leaves it alone`() =
        runTest {
            classToTest.refresh()
            val settings =
                DoctorSettings(language = "ru", twoFA = true, encryptedDocs = true, defaultStartTab = "calendar")
            classToTest.updateSettings(settings)
            assertThat(sent.last()).isEqualTo(
                """PATCH /api/doctors/me/settings {"language":"ru","twoFA":true,"encryptedDocs":true,"defaultStartTab":"calendar"}""",
            )
            assertThat(classToTest.me.value?.settings).isEqualTo(settings)
            status = HttpStatusCode.InternalServerError
            assertThat(classToTest.updateSettings(settings.copy(defaultStartTab = "chat")).isFailure).isTrue()
            assertThat(classToTest.me.value?.settings?.defaultStartTab).isEqualTo("calendar")
            classToTest.clear()
            assertThat(classToTest.me.value).isNull()
        }

    @Test
    fun `a profile PATCH answers with the saved profile, which replaces the stored one`() =
        runTest {
            classToTest.refresh()
            val saved =
                classToTest.update(
                    ProfileUpdateRequest(firstName = "Malika", timeZone = "Europe/Berlin"),
                ).getOrThrow()
            assertThat(
                sent.last(),
            ).isEqualTo("""PATCH /api/doctors/me/profile {"firstName":"Malika","timeZone":"Europe/Berlin"}""")
            assertThat(saved.firstName).isEqualTo("Malika")
            assertThat(classToTest.me.value?.profile).isEqualTo(saved)
        }

    @Test
    fun `an answer that arrives after sign-out is dropped`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val slow =
                ProfileRepository {
                    createHttpClient(
                        { null },
                        MockEngine {
                            gate.await()
                            respond(me, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        },
                    )
                }
            val pending = async { slow.refresh() }
            yield()
            slow.clear() // the session ended while the request was out
            gate.complete(Unit)
            pending.await()
            assertThat(slow.me.value).isNull()
        }

    @Test
    fun `embedded JSON lists are decoded, a malformed one reads as empty`() {
        assertThat(
            DoctorProfile(services = """["Implants","Whitening"]""").serviceTags,
        ).containsExactly("Implants", "Whitening")
        assertThat(DoctorProfile(certificates = "not json").certificateUrls).isEmpty()
        assertThat(DoctorProfile().serviceTags).isEmpty()
    }

    @Test
    fun `an older read that answers last can't overwrite a newer one`() =
        runTest {
            val first = CompletableDeferred<Unit>()
            var calls = 0
            val repo =
                ProfileRepository {
                    createHttpClient(
                        { null },
                        MockEngine {
                            val n = ++calls
                            if (n == 1) first.await() // the first read is slow
                            val name = if (n == 1) "Old" else "New"
                            respond(
                                """{"profile":{"firstName":"$name"}}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        },
                    )
                }
            val older = async { repo.refresh() }
            yield()
            repo.refresh()
            first.complete(Unit)
            older.await()
            assertThat(repo.me.value?.profile?.firstName).isEqualTo("New")
        }
}

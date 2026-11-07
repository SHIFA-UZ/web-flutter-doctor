package shifa.doctor.ui.profile

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.extendedProfileSaved
import shifa.doctor.generated.resources.serviceTagDuplicate
import shifa.doctor.generated.resources.serviceTagTooLong
import shifa.doctor.stopAll
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.DocumentPickerResult
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExtendedProfileViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val me =
        File("src/commonMain/composeResources/files/demo/doctors-me.json").readText()
            .replace(Regex(""""services"\s*:\s*(null|"[^"]*(\\"[^"]*)*")"""), """"services":"[\\"Braces\\"]"""")
            .replace(
                Regex(""""certificates"\s*:\s*(null|"[^"]*(\\"[^"]*)*")"""),
                """"certificates":"[\\"https://x/certificates/a.pdf\\"]"""",
            )
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failing = false

    // While closed, writes wait: lets a test start several before any answers.
    private val gate = kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
    private var hold: kotlinx.coroutines.CompletableDeferred<Unit> = gate
    private val engine =
        MockEngine { req ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (req.method == HttpMethod.Get) return@MockEngine respond(me, HttpStatusCode.OK, json)
            val path = req.url.encodedPath
            hold.await()
            if (failing) {
                return@MockEngine respond(
                    """{"status":500,"code":"INTERNAL","requestId":"req-8"}""",
                    HttpStatusCode.InternalServerError,
                    json,
                )
            }
            if (path.endsWith("certificate")) {
                sent += "upload"
                return@MockEngine respond("""{"photoUrl":"https://x/certificates/b.pdf"}""", HttpStatusCode.OK, json)
            }
            val body = req.body.toByteArray().decodeToString()
            sent += body
            respond(body, HttpStatusCode.OK, json)
        }
    private val repo = ProfileRepository { createHttpClient({ null }, engine) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { repo.refresh() }
    }

    @After fun tearDown() {
        ShellNotice.pending.value = null
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(200) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() = ExtendedProfileViewModel(repo).also { created += it }.apply { open() }

    @Test
    fun `tags save at once as the backend's JSON string, with the rules checked first`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.tags).containsExactly("Braces")
        classToTest.ui { it.copy(tagInput = "  braces ") }
        classToTest.addTag()
        assertThat(classToTest.state.value.tagError).isEqualTo(Res.string.serviceTagDuplicate)
        classToTest.ui { it.copy(tagInput = "x".repeat(41)) }
        classToTest.addTag()
        assertThat(classToTest.state.value.tagError).isEqualTo(Res.string.serviceTagTooLong)
        classToTest.ui { it.copy(tagInput = "Root  canal") }
        classToTest.addTag()
        settle { sent.isNotEmpty() }
        assertThat(sent.single()).isEqualTo("""{"services":"[\"Braces\",\"Root canal\"]"}""")
        assertThat(classToTest.state.value.tagInput).isEmpty()
    }

    @Test
    fun `a failed tag write puts the list back and says so`() {
        val classToTest = opened()
        failing = true
        classToTest.removeTag("Braces")
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.tags).containsExactly("Braces")
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("req-8")
    }

    @Test
    fun `a certificate uploads and joins the list, a delete sends the rest`() {
        val classToTest = opened()
        classToTest.uploadPdf(DocumentPickerResult("content://x", "diploma.pdf", byteArrayOf(1)))
        settle { !classToTest.state.value.uploading }
        assertThat(
            classToTest.state.value.certificates,
        ).containsExactly("https://x/certificates/a.pdf", "https://x/certificates/b.pdf")
        assertThat(repo.me.value?.profile?.certificateUrls).hasSize(2)
        classToTest.ui { it.copy(askDelete = "https://x/certificates/a.pdf") }
        classToTest.deleteCertificate()
        settle { sent.size == 2 }
        assertThat(sent.last()).isEqualTo("""{"certificates":"[\"https://x/certificates/b.pdf\"]"}""")
        assertThat(certificateName("https://x/certificates/b.pdf?exp=1&sig=2")).isEqualTo("b.pdf")
    }

    @Test
    fun `Save sends only the changed text fields and closes`() {
        val classToTest = opened()
        classToTest.edit { it.copy(biography = " Twenty years of root canals. ", telegram = "@drkarimova") }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(sent.single()).isEqualTo("""{"biography":"Twenty years of root canals.","telegram":"@drkarimova"}""")
        assertThat(ShellNotice.pending.value?.text).isEqualTo(Res.string.extendedProfileSaved)
    }

    @Test
    fun `rapid tag edits go one at a time, each with the latest list`() {
        val classToTest = opened()
        hold = kotlinx.coroutines.CompletableDeferred()
        listOf("Implants", "Whitening", "Veneers").forEach {
            classToTest.ui { s -> s.copy(tagInput = it) }
            classToTest.addTag()
        }
        hold.complete(Unit)
        settle { classToTest.state.value.tags.size == 4 && sent.isNotEmpty() && sent.last().contains("Veneers") }
        Thread.sleep(50)
        // The first write went with one tag; the next one took all the rest at once, and nothing older came after.
        assertThat(sent.last()).isEqualTo("""{"services":"[\"Braces\",\"Implants\",\"Whitening\",\"Veneers\"]"}""")
        assertThat(sent.size).isAtMost(3)
    }

    @Test
    fun `a delete waits for an upload in flight and keeps it`() {
        val classToTest = opened()
        hold = kotlinx.coroutines.CompletableDeferred()
        classToTest.uploadPdf(DocumentPickerResult("content://x", "b.pdf", byteArrayOf(1)))
        classToTest.ui { it.copy(askDelete = "https://x/certificates/a.pdf") }
        classToTest.deleteCertificate()
        hold.complete(Unit)
        settle { sent.size == 2 }
        assertThat(
            sent,
        ).containsExactly("upload", """{"certificates":"[\"https://x/certificates/b.pdf\"]"}""").inOrder()
    }

    @Test
    fun `handles are checked, a too large file never leaves the phone`() {
        val classToTest = opened()
        classToTest.edit { it.copy(telegram = "@ab", instagram = "https://instagram.com/dr.karimova/") }
        classToTest.save()
        assertThat(classToTest.state.value.telegramError).isNotNull()
        assertThat(classToTest.state.value.instagramError).isNull()
        assertThat(
            ExtendedProfileViewModel.isHandle("https://t.me/drkarimova", ExtendedProfileViewModel.TELEGRAM),
        ).isTrue()
        assertThat(ExtendedProfileViewModel.isHandle("dr karimova", ExtendedProfileViewModel.INSTAGRAM)).isFalse()
        classToTest.uploadPdf(DocumentPickerResult("content://x", "big.pdf", ByteArray(26 * 1024 * 1024)))
        assertThat(classToTest.state.value.uploading).isFalse()
        assertThat(sent).isEmpty()
    }
}

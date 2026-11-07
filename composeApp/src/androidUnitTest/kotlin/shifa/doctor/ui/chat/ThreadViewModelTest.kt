@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.chat

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.chatRecordingTooShort
import shifa.doctor.generated.resources.errorRecordingVoice
import shifa.doctor.generated.resources.errorUploadingFile
import shifa.doctor.stopAll
import kotlin.time.Duration.Companion.milliseconds

class ThreadViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failSend = false
    private var failUpload = false
    private val uploads = java.util.concurrent.CopyOnWriteArrayList<Int>()
    private val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var failRead = false
    private var holdThread: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    // BE-14: the `after` each thread read asked with (null: a full read).
    private val cursors = java.util.concurrent.CopyOnWriteArrayList<Long?>()
    private var nextId = 100
    private var thread =
        mutableListOf(
            """{"id":1,"chatId":7,"senderRole":"patient","type":"text","content":{"text":"Hi"},"status":"sent","createdAt":"2026-11-05T05:00:00Z"}""",
        )
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.substringAfter("/api/")
            asked += req.method.value + " " + path
            when (path) {
                "messages/upload-attachment" -> {
                    uploads += req.body.toByteArray().size
                    if (failUpload) {
                        return@MockEngine respond(
                            """{"status":500}""",
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    respond(
                        """{"url":"https://api.shifa.uz/chat-attachments/p.jpg?sig=1"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "messages/send" -> {
                    if (failSend) {
                        return@MockEngine respond(
                            """{"status":500}""",
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                    val body = req.body.toByteArray().decodeToString()
                    bodies += body
                    val text = Regex("\"text\":\"([^\"]*)\"").find(body)?.groupValues?.get(1) ?: ""
                    sent += text
                    val m = """{"id":${nextId++},"chatId":7,"senderRole":"doctor","type":"text","content":{"text":"$text"},"status":"sent","isMine":true,"createdAt":"2026-11-05T06:00:00Z"}"""
                    thread += m
                    respond(m, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "messages/conversations/7/read" ->
                    if (failRead) {
                        respond(
                            "{}",
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                "messages/conversations/7" -> {
                    // The thread as it was when asked: a slow answer can miss a send that finishes meanwhile. With
                    // `after`, only newer messages (BE-14).
                    val after = req.url.parameters["after"]?.toLong()
                    cursors += after
                    val snapshot =
                        thread.toList().filter { m ->
                            after == null || Regex("\"id\":(\\d+)").find(m)!!.groupValues[1].toLong() > after
                        }.let { if (after != null) it.take(100) else it } // the backend's default page
                    holdThread?.await()
                    respond(
                        """{"conversation":{"id":7,"participantId":11,"participantName":"Dilnoza"},"messages":[${snapshot.joinToString(
                            ",",
                        )}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }

    // Real time: polling waits with delay().
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened() =
        ThreadViewModel(
            7,
            ChatRepository({
                createHttpClient({ null }, engine)
            }, { "https://api.shifa.uz/api/" }),
            every = 40.milliseconds,
        ).also { created += it }

    @Test
    fun `read on open, and again only when something new comes from the other side`() {
        val classToTest = opened()
        classToTest.foreground(true)
        settle { asked.count { it == "GET messages/conversations/7" } >= 3 }
        assertThat(asked.count { it == "POST messages/conversations/7/read" }).isEqualTo(1)
        thread += """{"id":2,"chatId":7,"senderRole":"patient","type":"text","content":{"text":"Are you there?"},"status":"sent","createdAt":"2026-11-05T05:05:00Z"}"""
        settle { asked.count { it == "POST messages/conversations/7/read" } == 2 }
        assertThat(classToTest.state.value.newestIncoming).isEqualTo(2L)
        classToTest.foreground(false)
        assertThat(classToTest.state.value.conversation?.name).isEqualTo("Dilnoza")
    }

    @Test
    fun `a text shows at once, the server's message takes its place, texts go in order`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        classToTest.draft("  ")
        classToTest.send()
        assertThat(asked.none { it.endsWith("messages/send") }).isTrue()
        classToTest.draft("one")
        classToTest.send()
        // The field clears at once, so a second tap has nothing to send.
        assertThat(classToTest.state.value.draft).isEmpty()
        classToTest.draft("two")
        classToTest.send()
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(sent).containsExactly("one", "two").inOrder()
        assertThat(classToTest.state.value.messages!!.map { it.text }).containsExactly("Hi", "one", "two").inOrder()
    }

    @Test
    fun `a failed text stays with Tap to retry, and goes on retry`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        failSend = true
        classToTest.draft("lost?")
        classToTest.send()
        settle { classToTest.state.value.pending.singleOrNull()?.failed == true }
        val p = classToTest.state.value.pending.single()
        assertThat(p.text).isEqualTo("lost?")
        failSend = false
        classToTest.retry(p.localId)
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(sent).containsExactly("lost?")
    }

    @Test
    fun `a failed read acknowledgement is tried again at the next answer`() {
        failRead = true
        val classToTest = opened()
        classToTest.foreground(true)
        settle { asked.count { it == "POST messages/conversations/7/read" } >= 1 }
        failRead = false
        settle { asked.count { it == "POST messages/conversations/7/read" } >= 2 }
        val after = asked.count { it == "POST messages/conversations/7/read" }
        Thread.sleep(150)
        // Once it went through, nothing new from the patient: no more acknowledgements.
        assertThat(asked.count { it == "POST messages/conversations/7/read" }).isEqualTo(after)
        classToTest.foreground(false)
    }

    @Test
    fun `a read that started before a send answered never takes the sent message away`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        holdThread = kotlinx.coroutines.CompletableDeferred()
        classToTest.refresh()
        settle { asked.count { it == "GET messages/conversations/7" } == 2 }
        classToTest.draft("quick")
        classToTest.send()
        settle { classToTest.state.value.pending.isEmpty() }
        holdThread!!.complete(Unit)
        Thread.sleep(100)
        assertThat(classToTest.state.value.messages!!.map { it.text }).containsExactly("Hi", "quick").inOrder()
    }

    @Test
    fun `a photo shows at once, is uploaded, then sent as an image with its URL, name and size`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        classToTest.sendImage(shifa.doctor.utils.PickedImage("xray.jpg", ByteArray(1_000)))
        assertThat(classToTest.state.value.pending.single().image).isNotNull()
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(uploads).hasSize(1)
        val body = bodies.last()
        assertThat(body).contains("\"type\":\"image\"")
        assertThat(body).contains("\"attachmentUrl\":\"https://api.shifa.uz/chat-attachments/p.jpg?sig=1\"")
        assertThat(body).contains("\"attachmentName\":\"xray.jpg\"")
        assertThat(body).contains("\"fileSize\":1000")
    }

    @Test
    fun `a failed upload keeps the photo for retry, a too large one isn't sent`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        failUpload = true
        classToTest.sendImage(shifa.doctor.utils.PickedImage("a.jpg", ByteArray(10)))
        settle { classToTest.state.value.pending.singleOrNull()?.failed == true }
        assertThat(asked.none { it.endsWith("messages/send") }).isTrue()
        failUpload = false
        classToTest.retry(classToTest.state.value.pending.single().localId)
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(uploads).hasSize(2)
        classToTest.sendImage(shifa.doctor.utils.PickedImage("huge.jpg", ByteArray(21 * 1024 * 1024)))
        assertThat(classToTest.state.value.pending).isEmpty()
        assertThat(shifa.doctor.ui.navigation.ShellNotice.pending.value).isNotNull()
        shifa.doctor.ui.navigation.ShellNotice.pending.value = null
    }

    @Test
    fun `a photo whose send failed after the upload is only sent again on retry`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        failSend = true
        classToTest.sendImage(shifa.doctor.utils.PickedImage("a.jpg", ByteArray(10)))
        settle { classToTest.state.value.pending.singleOrNull()?.failed == true }
        assertThat(uploads).hasSize(1)
        failSend = false
        classToTest.retry(classToTest.state.value.pending.single().localId)
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(uploads).hasSize(1)
        assertThat(bodies.last()).contains("\"type\":\"image\"")
    }

    @Test
    fun `a PDF goes as a document, a PNG as an image, anything else isn't sent`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        classToTest.sendDocument(shifa.doctor.utils.DocumentPickerResult("u", "Referral.PDF", ByteArray(2_048)))
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(bodies.last()).contains("\"type\":\"document\"")
        assertThat(bodies.last()).contains("\"attachmentName\":\"Referral.PDF\"")
        assertThat(bodies.last()).contains("\"fileSize\":2048")
        classToTest.sendDocument(shifa.doctor.utils.DocumentPickerResult("u", "scan.png", ByteArray(10)))
        settle { classToTest.state.value.pending.isEmpty() }
        assertThat(bodies.last()).contains("\"type\":\"image\"")
        val before = uploads.size
        classToTest.sendDocument(shifa.doctor.utils.DocumentPickerResult("u", "setup.exe", ByteArray(10)))
        assertThat(classToTest.state.value.pending).isEmpty()
        assertThat(uploads).hasSize(before)
        assertThat(shifa.doctor.ui.navigation.ShellNotice.pending.value).isNotNull()
        shifa.doctor.ui.navigation.ShellNotice.pending.value = null
        assertThat(
            mimeOf("a.docx"),
        ).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }

    @Test
    fun `a picked file that can't be read says so and sends nothing`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        classToTest.sendDocument(
            shifa.doctor.utils.DocumentPickerResult("u", "Referral.pdf", ByteArray(0), readFailed = true),
        )
        assertThat(classToTest.state.value.pending).isEmpty()
        assertThat(uploads).isEmpty()
        assertThat(
            shifa.doctor.ui.navigation.ShellNotice.pending.value?.text,
        ).isEqualTo(Res.string.errorUploadingFile)
        shifa.doctor.ui.navigation.ShellNotice.pending.value = null
    }

    @Test
    fun `a voice note is uploaded as an m4a and sent with its length, under a second it isn't sent`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        classToTest.sendVoice(ByteArray(4_000), 14)
        assertThat(classToTest.state.value.pending.single().durationSeconds).isEqualTo(14)
        settle { classToTest.state.value.pending.isEmpty() }
        val body = bodies.last()
        assertThat(body).contains("\"type\":\"voice\"")
        assertThat(body).contains("\"duration\":14")
        assertThat(body).containsMatch("\"attachmentName\":\"voice_\\d+\\.m4a\"")
        assertThat(mimeOf("voice_1.m4a")).isEqualTo("audio/mp4")
        val before = uploads.size
        classToTest.sendVoice(ByteArray(100), 0)
        assertThat(uploads).hasSize(before)
        assertThat(
            shifa.doctor.ui.navigation.ShellNotice.pending.value
                ?.text,
        ).isEqualTo(Res.string.chatRecordingTooShort)
        // A recorder that couldn't finish the file: said, not dropped.
        classToTest.sendVoice(null, 5)
        assertThat(uploads).hasSize(before)
        assertThat(
            shifa.doctor.ui.navigation.ShellNotice.pending.value
                ?.text,
        ).isEqualTo(Res.string.errorRecordingVoice)
        shifa.doctor.ui.navigation.ShellNotice.pending.value = null
    }

    @Test
    fun `polls ask only for newer messages and merge them, with a full read every sixth time`() {
        val classToTest = opened()
        classToTest.foreground(true)
        settle { cursors.size >= 3 }
        // The first read is full; the next ones start after the newest message held.
        assertThat(cursors.first()).isNull()
        assertThat(cursors[1]).isEqualTo(1L)
        thread += """{"id":2,"chatId":7,"senderRole":"patient","type":"text","content":{"text":"Are you there?"},"status":"sent","createdAt":"2026-11-05T05:05:00Z"}"""
        settle { classToTest.state.value.messages?.size == 2 }
        assertThat(classToTest.state.value.messages!!.map { it.id }).containsExactly(1L, 2L).inOrder()
        settle { cursors.size >= 9 }
        classToTest.foreground(false)
        // Every sixth poll after the first reads everything again (read ticks on older messages).
        assertThat(cursors.count { it == null }).isAtLeast(2)
    }

    @Test
    fun `the cursor follows what the server sent, never a send, and a full page is read on`() {
        val classToTest = opened()
        classToTest.refresh()
        settle { classToTest.state.value.messages != null }
        // 150 messages from the patient arrive, then the doctor sends one (it gets the next id).
        (2..151).forEach { n ->
            thread += """{"id":$n,"chatId":7,"senderRole":"patient","type":"text","content":{"text":"m$n"},"status":"sent","createdAt":"2026-11-05T05:${(n % 60).toString().padStart(
                2,
                '0',
            )}:00Z"}"""
        }
        nextId = 152
        classToTest.draft("reply")
        classToTest.send()
        settle { classToTest.state.value.pending.isEmpty() }
        cursors.clear()
        classToTest.refresh()
        settle { (classToTest.state.value.messages?.size ?: 0) >= 152 }
        // Started after message 1 (not after the send), and read on after the full first page.
        assertThat(cursors.first()).isEqualTo(1L)
        assertThat(cursors.size).isAtLeast(2)
        assertThat(classToTest.state.value.messages!!.map { it.id }).containsAtLeastElementsIn((1L..152L).toList())
    }
}

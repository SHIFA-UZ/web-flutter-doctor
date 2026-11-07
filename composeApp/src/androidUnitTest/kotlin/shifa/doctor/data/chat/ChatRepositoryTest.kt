@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.chat

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.api.absoluteFileUrl
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.MessageStatus
import shifa.doctor.data.model.MessageType
import shifa.doctor.data.model.SendMessageRequest

class ChatRepositoryTest {
    private var lastBody = ""
    private val engine =
        MockEngine { req ->
            lastBody = req.body.toByteArray().decodeToString()
            val path = req.url.encodedPath.substringAfter("/api/")
            val body =
                when {
                    path == "messages/conversations" ->
                        """[{"id":7,"participantId":11,"participantName":"Dilnoza","participantPhotoUrl":"/uploads/p.jpg","lastMessage":"Hi","lastMessageAt":"2026-11-05T05:00:00Z","unreadCount":2},{"participantName":"no id"}]"""
                    path == "messages/conversations/7" ->
                        """{"conversation":{"id":7,"participantId":11},"messages":[
                            {"id":2,"chatId":7,"senderRole":"patient","type":"voice","content":{"fileUrl":"chat-attachments/v.m4a","duration":14},"status":"sent","createdAt":"2026-11-05T05:01:00Z"},
                            {"id":1,"chatId":7,"senderRole":"doctor","type":"TEXT","content":{"text":"Hello"},"status":"READ","isMine":true,"createdAt":"2026-11-05T05:00:00Z"},
                            {"id":3,"chatId":7,"type":"sticker","content":{"fileUrl":"https://cdn/x.bin"},"createdAt":"2026-11-05T05:02:00Z"},
                            {"id":4,"chatId":7,"type":"text","content":{"text":"no time"}}]}"""
                    path == "messages/search" -> """[{"id":11,"name":"Dilnoza","isDoctor":false,"photoUrl":"/uploads/p.jpg"}]"""
                    path == "messages/upload-attachment" -> """{"url":"https://api-dev.shifa.uz/chat-attachments/a.jpg?sig=1"}"""
                    path == "messages/send" -> """{"id":9,"chatId":7,"type":"image","content":{"fileUrl":"https://x/a.jpg"},"status":"sent","isMine":true,"createdAt":"2026-11-05T05:03:00Z"}"""
                    else -> "[]"
                }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    private val classToTest =
        ChatRepository({ createHttpClient({ null }, engine) }, { "https://api-dev.shifa.uz/api/" })

    @Test
    fun `relative file URLs resolve against the backend host, absolute ones stay`() {
        assertThat(
            absoluteFileUrl("/uploads/p.jpg", "https://api-dev.shifa.uz/api/"),
        ).isEqualTo("https://api-dev.shifa.uz/uploads/p.jpg")
        assertThat(
            absoluteFileUrl("chat-attachments/v.m4a", "https://api.shifa.uz/api/"),
        ).isEqualTo("https://api.shifa.uz/chat-attachments/v.m4a")
        assertThat(absoluteFileUrl("HTTPS://cdn/x", "https://api.shifa.uz/api/")).isEqualTo("HTTPS://cdn/x")
        assertThat(absoluteFileUrl("  ", "https://api.shifa.uz/api/")).isNull()
    }

    @Test
    fun `conversations skip a row without an id, photos come back absolute`() =
        runTest {
            val list = classToTest.conversations().getOrThrow()
            assertThat(list.map { it.id }).containsExactly(7L)
            assertThat(list.single().photoUrl).endsWith("/uploads/p.jpg")
            assertThat(list.single().photoUrl).startsWith("https://")
            assertThat(list.single().unread).isEqualTo(2)
        }

    @Test
    fun `a thread is oldest first, types and statuses in any case, every attachment URL absolute`() =
        runTest {
            val t = classToTest.thread(7).getOrThrow()
            assertThat(t.messages.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
            val (text, voice, unknown) = t.messages
            assertThat(text.type).isEqualTo(MessageType.Text)
            assertThat(text.status).isEqualTo(MessageStatus.Read)
            assertThat(voice.type).isEqualTo(MessageType.Voice)
            assertThat(voice.fromPatient).isTrue()
            assertThat(voice.fileUrl).endsWith("/chat-attachments/v.m4a")
            assertThat(voice.fileUrl).startsWith("https://")
            assertThat(voice.durationSeconds).isEqualTo(14)
            // An unknown type with a file is a document, as the backend's own fallback.
            assertThat(unknown.type).isEqualTo(MessageType.Document)
            assertThat(unknown.sentAt).isEqualTo(Instant.parse("2026-11-05T05:02:00Z"))
        }

    @Test
    fun `search photos come back absolute too`() =
        runTest {
            assertThat(
                classToTest.search("Dil").getOrThrow().single().photoUrl,
            ).isEqualTo("https://api-dev.shifa.uz/uploads/p.jpg")
        }

    @Test
    fun `upload sends one file part and returns its URL, send posts the request`() =
        runTest {
            val url = classToTest.upload(byteArrayOf(1, 2, 3), "x/ray.jpg", "image/jpeg").getOrThrow()
            assertThat(url).isEqualTo("https://api-dev.shifa.uz/chat-attachments/a.jpg?sig=1")
            assertThat(lastBody).contains("name=file")
            assertThat(lastBody).contains("filename=\"x_ray.jpg\"")
            val sent =
                classToTest.send(
                    SendMessageRequest(7, "image", attachmentUrl = url, attachmentName = "ray.jpg", fileSize = 3),
                ).getOrThrow()
            assertThat(sent.type).isEqualTo(MessageType.Image)
            assertThat(lastBody).contains("\"conversationId\":7")
            assertThat(lastBody).contains("\"type\":\"image\"")
        }
}

package shifa.doctor.data.chat

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.absoluteFileUrl
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.ConversationDto
import shifa.doctor.data.model.Message
import shifa.doctor.data.model.MessageDto
import shifa.doctor.data.model.SendMessageRequest
import shifa.doctor.data.model.StartConversationRequest
import shifa.doctor.data.model.UserSearchResult
import shifa.doctor.utils.safeFileName

/** A conversation and all its messages, oldest first (the backend has no paging yet, BE-14). */
data class ConversationThread(
    val conversation: Conversation?,
    val messages: List<Message>,
)

@Serializable
private data class ThreadDto(
    val conversation: ConversationDto? = null,
    val messages: List<JsonElement> = emptyList(),
)

@Serializable
private data class UploadDto(
    val url: String? = null,
)

/**
 * Chat (CHAT-01, 07 §1, 15 §2.8): conversations, one conversation, start, read, send, user search and attachment
 * upload. Lists skip a row they can't read, never the whole list; URLs come back absolute (07 §1.5).
 */
class ChatRepository(
    private val client: () -> HttpClient = { httpClient },
    // The backend the client talks to: relative file URLs resolve against its host.
    private val apiBase: () -> String = { BackendChoice.current.value.baseUrl },
) {
    /** `GET /messages/conversations`. */
    suspend fun conversations(): Result<List<Conversation>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.CONVERSATIONS) }
            .map {
                    rows ->
                rows.rows(ConversationDto.serializer(), "conversations") { Conversation.from(it, apiBase()) }
            }

    /** `GET /messages/conversations/{id}`: the conversation and every message, oldest first. */
    suspend fun thread(id: Long): Result<ConversationThread> =
        apiCall<ThreadDto> { client().get(ApiConfig.Endpoints.conversation(id)) }.map { t ->
            ConversationThread(
                t.conversation?.let { Conversation.from(it, apiBase()) },
                t.messages.rows(
                    MessageDto.serializer(),
                    "messages",
                ) { Message.from(it, apiBase()) }.sortedBy { it.sentAt },
            )
        }

    /** `POST /messages/conversations/start` with a patient or a doctor; no message is sent. */
    suspend fun start(
        patientId: Long? = null,
        doctorId: Long? = null,
    ): Result<Conversation> =
        apiCall<ConversationDto> {
            client().post(ApiConfig.Endpoints.CONVERSATION_START) {
                contentType(ContentType.Application.Json)
                setBody(StartConversationRequest(patientId, doctorId))
            }
        }.mapCatching { Conversation.from(it, apiBase()) ?: error("conversation without an id") }

    /** `POST /messages/conversations/{id}/read`. */
    suspend fun markRead(id: Long): Result<Unit> = apiCall { client().post(ApiConfig.Endpoints.conversationRead(id)) }

    /** `POST /messages/send`; the sent message as the backend stored it. */
    suspend fun send(request: SendMessageRequest): Result<Message> =
        apiCall<MessageDto> {
            client().post(ApiConfig.Endpoints.MESSAGE_SEND) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.mapCatching { Message.from(it, apiBase()) ?: error("message without an id") }

    /** `GET /messages/search?q=`: patients and colleagues to write to. */
    suspend fun search(query: String): Result<List<UserSearchResult>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.CHAT_SEARCH) { parameter("q", query) } }
            .map { rows ->
                rows.rows(
                    UserSearchResult.serializer(),
                    "chat search",
                ) { it.copy(photoUrl = absoluteFileUrl(it.photoUrl, apiBase())) }
            }

    /**
     * `POST /messages/upload-attachment`, multipart `file` (15 §2.8): the stored file's URL, sent next in
     * [send]. The backend takes images, pdf/doc/docx and audio up to 25 MB.
     */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<String> =
        apiCall<UploadDto> {
            client().submitFormWithBinaryData(
                ApiConfig.Endpoints.CHAT_UPLOAD,
                formData {
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${safeFileName(fileName)}\"")
                        },
                    )
                },
            )
        }.mapCatching { it.url?.takeIf(String::isNotBlank) ?: error("upload without a url") }
}

val chatRepository = ChatRepository()

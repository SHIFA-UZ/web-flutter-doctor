package shifa.doctor.data.debug

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration.Companion.minutes

/**
 * The demo chat (CHAT-01), in memory: three conversations with demo patients (text, a document, a voice note), start,
 * read, send, search and upload, answered as the backend does (lower-case types, absolute URLs). Uploaded files never
 * leave the phone: the URL points at the demo host and doesn't load.
 */
internal class DemoChat(
    private val now: () -> Instant = { currentInstant() },
) {
    private data class Chat(
        val id: Long,
        val patientId: Long,
        val name: String,
        var unread: Int,
        val messages: MutableList<JsonObject>,
        val doctor: Boolean = false,
    )

    private var lastId = 900L
    private val chats: MutableList<Chat> = seed()

    /** The answer to a chat request, or null when [path] isn't chat. */
    fun handle(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
    ): JsonElement? =
        when {
            method == "GET" && path == Endpoints.CONVERSATIONS -> JsonArray(chats.map(::row))
            method == "GET" && path.matches(Regex("messages/conversations/\\d+")) ->
                chat(path)?.let {
                        c ->
                    buildJsonObject {
                        put("conversation", row(c))
                        put("messages", JsonArray(c.messages))
                    }
                }
            method == "POST" && path.matches(Regex("messages/conversations/\\d+/read")) -> {
                chat(path.removeSuffix("/read"))?.unread = 0
                JsonObject(emptyMap())
            }
            method == "POST" && path == Endpoints.CONVERSATION_START -> {
                // A patient or a colleague; one conversation per person and role.
                val doctorId = field(body, "recipientDoctorId")
                val id = doctorId ?: field(body, "recipientPatientId")
                val doctor = doctorId != null
                if (id == null) {
                    null
                } else {
                    val c =
                        chats.firstOrNull { it.patientId == id && it.doctor == doctor }
                            ?: Chat(
                                ++lastId,
                                id,
                                (if (doctor) DEMO_DOCTORS[id] else DEMO_NAMES[id]) ?: "#$id",
                                0,
                                mutableListOf(),
                                doctor,
                            ).also { chats.add(0, it) }
                    row(c)
                }
            }
            method == "POST" && path == Endpoints.MESSAGE_SEND -> send(body)
            method == "GET" && path == Endpoints.CHAT_SEARCH -> {
                val q = query["q"].orEmpty().trim().lowercase()
                JsonArray(
                    (
                        DEMO_NAMES.map {
                            Triple(
                                it.key,
                                it.value,
                                false,
                            )
                        } + DEMO_DOCTORS.map { Triple(it.key, it.value, true) }
                    )
                        .filter { (_, n, _) -> n.lowercase().contains(q) }
                        .map { (id, n, doctor) ->
                            buildJsonObject {
                                put("id", id)
                                put("name", n)
                                put("isDoctor", doctor)
                                put("phone", "+99890000" + id.toString().padStart(4, '0'))
                            }
                        },
                )
            }
            method == "POST" && path == Endpoints.CHAT_UPLOAD ->
                buildJsonObject { put("url", "https://api.shifa.uz/demo/chat-attachments/${++lastId}") }
            method == "GET" && path == Endpoints.CHAT_UNREAD ->
                buildJsonObject {
                    put(
                        "count",
                        chats.sumOf { it.unread },
                    )
                }
            else -> null
        }

    private fun chat(path: String) =
        path.substringAfterLast('/').toLongOrNull()?.let { id ->
            chats.firstOrNull {
                it.id == id
            }
        }

    private fun field(
        body: String,
        name: String,
    ) = runCatching { apiJson.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.longOrNull }.getOrNull()

    private fun send(body: String): JsonElement? {
        val req = runCatching { apiJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val c = chats.firstOrNull { it.id == req["conversationId"]?.jsonPrimitive?.longOrNull } ?: return null

        fun s(key: String) = req[key]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
        val message =
            message(
                ++lastId,
                c.id,
                mine = true,
                type = s("type") ?: "text",
                at = now(),
                text = s("text"),
                url = s("attachmentUrl"),
                name = s("attachmentName"),
                size = s("fileSize")?.toLongOrNull(),
                duration = s("duration")?.toIntOrNull(),
            )
        c.messages += message
        // The conversation moves to the top, as the backend's last-activity order does.
        chats.remove(c)
        chats.add(0, c)
        return message
    }

    private fun row(c: Chat): JsonObject {
        val last = c.messages.lastOrNull()
        val content = last?.get("content")?.jsonObject
        return buildJsonObject {
            put("id", c.id)
            put("isDoctorParticipant", c.doctor)
            put("participantId", c.patientId)
            put("participantName", c.name)
            put("unreadCount", c.unread)
            last?.let {
                put(
                    "lastMessage",
                    content?.get(
                        "text",
                    )?.jsonPrimitive?.content ?: content?.get("fileName")?.jsonPrimitive?.content ?: "",
                )
                put("lastMessageAt", it["createdAt"]!!)
            }
        }
    }

    private fun seed(): MutableList<Chat> {
        val t = now()

        fun m(
            chat: Long,
            mine: Boolean,
            minutesAgo: Int,
            text: String? = null,
            type: String = "text",
            name: String? = null,
            size: Long? = null,
            duration: Int? = null,
        ) = message(
            ++lastId,
            chat,
            mine,
            type,
            t - minutesAgo.minutes,
            text,
            if (type == "text") null else "https://api.shifa.uz/demo/chat-attachments/$lastId",
            name,
            size,
            duration,
        )
        return mutableListOf(
            Chat(
                801,
                11,
                DEMO_NAMES.getValue(11),
                2,
                mutableListOf(
                    m(801, true, 180, "Good morning! How are you feeling after the new dose?"),
                    m(801, false, 30, "Much better, thank you. The headaches are gone."),
                    m(801, false, 28, type = "document", name = "blood_test_results.pdf", size = 248_000),
                ),
            ),
            Chat(
                802,
                12,
                DEMO_NAMES.getValue(12),
                1,
                mutableListOf(
                    m(802, false, 300, "Doctor, can I move my appointment to Friday?"),
                    m(802, true, 290, "Yes, Friday at 10:00 is free. I'll book it."),
                    m(802, false, 120, type = "voice", name = "voice_1.m4a", size = 42_000, duration = 14),
                ),
            ),
            Chat(
                803,
                14,
                DEMO_NAMES.getValue(14),
                0,
                mutableListOf(m(803, true, 2_880, "Please send the X-ray when you have it.")),
            ),
        )
    }

    private fun message(
        id: Long,
        chat: Long,
        mine: Boolean,
        type: String,
        at: Instant,
        text: String?,
        url: String?,
        name: String?,
        size: Long?,
        duration: Int?,
    ) = buildJsonObject {
        put("id", id)
        put("chatId", chat)
        put("senderId", if (mine) 1 else 2)
        put("senderRole", if (mine) "doctor" else "patient")
        put("type", type)
        put(
            "content",
            JsonObject(
                listOfNotNull(
                    text?.let { "text" to JsonPrimitive(it) },
                    url?.let { "fileUrl" to JsonPrimitive(it) },
                    name?.let { "fileName" to JsonPrimitive(it) },
                    size?.let { "fileSize" to JsonPrimitive(it) },
                    duration?.let { "duration" to JsonPrimitive(it) },
                ).toMap(),
            ),
        )
        put("status", if (mine) "read" else "sent")
        put("isMine", mine)
        put("isRead", true)
        put("createdAt", at.toString())
    }

    private companion object {
        val DEMO_NAMES =
            mapOf(11L to "Dilnoza Rahimova", 12L to "Jasur Karimov", 13L to "Malika Tursunova", 14L to "Bekzod Aliyev")

        // A colleague to write to (doctor-to-doctor chat).
        val DEMO_DOCTORS = mapOf(21L to "Dr. med. Sardor Nazarov")
    }
}

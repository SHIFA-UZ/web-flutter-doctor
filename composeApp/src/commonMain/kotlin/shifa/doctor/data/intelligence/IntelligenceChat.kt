package shifa.doctor.data.intelligence

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.rows
import shifa.doctor.utils.DART_WS

/** One turn of a local Intelligence chat: `user` or `assistant`, with the tool card an assistant turn showed. */
data class ChatMessage(
    val role: String,
    val content: String,
    val at: Instant,
    val toolName: String? = null,
    val cardPayload: JsonObject? = null,
) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/**
 * A local Intelligence chat (02 §4.3). [id] doubles as the stream's `conversationId`, so a new chat always gets a new
 * one. [titleCustom]: the doctor renamed it, and [title] is kept as typed.
 */
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<ChatMessage>,
    val titleCustom: Boolean = false,
) {
    val displayTitle: String
        get() {
            if (titleCustom && title.isNotBlank()) return title.trim()
            messages.firstOrNull { it.isUser && ChatTitles.shortTitle(it.content).isNotEmpty() }?.let {
                return ChatTitles.shortTitle(it.content)
            }
            val t = title.trim()
            return if (t.isEmpty() || t == UNTITLED) UNTITLED else ChatTitles.shortTitle(t)
        }

    /** The latest message, shortened; independent of [displayTitle]. */
    val preview: String
        get() =
            messages.lastOrNull { it.content.isNotBlank() }?.let {
                ChatTitles.previewLine(
                    it.content.trim(),
                )
            }.orEmpty()

    val patientMark: PatientMark get() = ChatContext.summarize(messages.map { it.cardPayload to it.toolName })

    val patientLinked: Boolean get() = patientMark.linked

    fun matchesQuery(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return displayTitle.lowercase().contains(q) ||
            preview.lowercase().contains(q) ||
            messages.any { it.content.lowercase().contains(q) }
    }

    companion object {
        // Flutter's stored marker for "no title yet"; the screen shows the localised "New chat".
        const val UNTITLED = "New chat"

        fun encodeList(sessions: List<ChatSession>): String =
            apiJson.encodeToString(ListSerializer(SessionWire.serializer()), sessions.map(SessionWire::of))

        /**
         * Newest first. Unreadable JSON is an empty history, and one bad chat or message is skipped, not the rest
         * (Flutter's list or nothing). Older chats without `titleCustom` read as not renamed.
         */
        fun decodeList(
            raw: String,
            now: Instant,
        ): List<ChatSession> =
            runCatching { apiJson.parseToJsonElement(raw) as? JsonArray }
                .getOrNull()
                .orEmpty()
                .let { rows ->
                    // A chat without an id gets its own (one each, so the cap and delete act on one chat).
                    var n = 0
                    rows.rows(SessionWire.serializer(), "intelligence chats") { it.toSession(now, n++) }
                }.distinctBy { it.id }
                .sortedByDescending { it.updatedAt }
    }
}

@Serializable
private data class MessageWire(
    val role: String? = null,
    val content: String? = null,
    val at: String? = null,
    val toolName: String? = null,
    val cardPayload: JsonElement? = null,
) {
    fun toMessage(now: Instant) =
        ChatMessage(
            role ?: ChatMessage.ROLE_USER,
            content.orEmpty(),
            at.toInstant() ?: now,
            toolName,
            cardPayload as? JsonObject,
        )

    companion object {
        fun of(m: ChatMessage) = MessageWire(m.role, m.content, m.at.toString(), m.toolName, m.cardPayload)
    }
}

@Serializable
private data class SessionWire(
    val id: String? = null,
    val title: String? = null,
    val titleCustom: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val messages: List<JsonElement> = emptyList(),
) {
    fun toSession(
        now: Instant,
        index: Int,
    ) = ChatSession(
        id?.takeIf { it.isNotBlank() } ?: "chat_${now.toEpochMilliseconds()}_r$index",
        title ?: ChatSession.UNTITLED,
        createdAt.toInstant() ?: now,
        updatedAt.toInstant() ?: now,
        messages.rows(MessageWire.serializer(), "intelligence messages") { it.toMessage(now) },
        titleCustom,
    )

    companion object {
        fun of(s: ChatSession) =
            SessionWire(
                s.id,
                s.title,
                s.titleCustom,
                s.createdAt.toString(),
                s.updatedAt.toString(),
                s.messages.map { apiJson.encodeToJsonElement(MessageWire.serializer(), MessageWire.of(it)) },
            )
    }
}

private fun String?.toInstant(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** Title, preview, wake-phrase and reply-language helpers (Flutter's `IntelligenceChatTitles`, ported with its tests). */
object ChatTitles {
    const val MAX_TITLE_LENGTH = 48
    const val MAX_PREVIEW_LENGTH = 90

    /** Starter prompt (punctuation ignored) → short title in its language; in step with the `intelPrompt*` strings. */
    val starterTitles: Map<String, String> =
        mapOf(
            "summarize my schedule for today" to "Today's schedule",
            "what is my schedule today" to "Today's schedule",
            "help me find a patient" to "Find a patient",
            "help me manage my appointments" to "Manage appointments",
            "help me manage an appointment — find a patient and propose free slots" to "Manage appointments",
            "help me manage an appointment - find a patient and propose free slots" to "Manage appointments",
            "help me review my clinical notes" to "Clinical notes",
            "list draft visit facts for my current appointment so i can document them" to "Clinical notes",
            "bugungi jadvalimni qisqacha ayting" to "Bugungi jadval",
            "bugungi jadvalim qanday" to "Bugungi jadval",
            "bemorni topishga yordam bering" to "Bemorni topish",
            "uchrashuvlarimni boshqarishga yordam bering" to "Uchrashuvlarni boshqarish",
            "uchrashuvni boshqarishga yordam bering — bemorni toping va bo'sh slotlarni taklif qiling" to
                "Uchrashuvlarni boshqarish",
            "klinik qaydlarimni ko'rib chiqishga yordam bering" to "Klinik qaydlar",
            "joriy uchrashuvim uchun qoralama faktlarni ro'yxatlang, hujjatlashtirish uchun" to "Klinik qaydlar",
            "кратко изложите моё расписание на сегодня" to "Расписание на сегодня",
            "кратко изложите мое расписание на сегодня" to "Расписание на сегодня",
            "какое у меня расписание сегодня" to "Расписание на сегодня",
            "помогите найти пациента" to "Найти пациента",
            "помогите управлять моими приёмами" to "Управление приёмами",
            "помогите управлять моими приемами" to "Управление приёмами",
            "помогите с приёмом — найдите пациента и предложите свободные слоты" to "Управление приёмами",
            "помогите с приемом — найдите пациента и предложите свободные слоты" to "Управление приёмами",
            "помогите просмотреть мои клинические заметки" to "Клинические заметки",
            "покажите черновики фактов текущего приёма для документирования" to "Клинические заметки",
            "покажите черновики фактов текущего приема для документирования" to "Клинические заметки",
        )

    private val nameTokens = setOf("shifa", "shifah", "shefa", "shefah", "shiva", "sheva", "шифа")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("$DART_WS+")
    private val greeting =
        Regex(
            "^(?:hey|hi|hello|ok|okay|salom|salaam|salam|salem|" +
                "assalomu?(?:$DART_WS+alaykum)?|assalamu?(?:$DART_WS+alaykum)?|" +
                "эй|привет|здравствуйте?|салом|ассалому?(?:$DART_WS+алайкум)?)$DART_WS+",
            RegexOption.IGNORE_CASE,
        )
    private val name =
        Regex(
            "^(?:shifa+|shifah|shefa+|shefah|shiva+|sheva+|шифа|she$DART_WS+(?:fa|fah|for|va))" +
                "$DART_WS*[,!.:]?$DART_WS*",
            RegexOption.IGNORE_CASE,
        )

    private fun wakeWords(text: String): List<String> {
        val t = text.lowercase().replace(nonWord, " ").trim()
        return if (t.isEmpty()) emptyList() else t.split(spaces)
    }

    /** "Hey Shifa", "Salom Shifa", "Assalomu alaykum Shifa", "Привет Шифа", and common mishears of the name. */
    fun containsWakePhrase(text: String): Boolean {
        val words = wakeWords(text)
        if (words.any { it in nameTokens }) return true
        return words.zipWithNext().any { (a, b) -> a == "she" && b in setOf("fa", "fah", "for", "va") }
    }

    fun stripWakePhrase(text: String): String = text.trim().replaceFirst(greeting, "").replaceFirst(name, "").trim()

    private fun lookupKey(text: String): String =
        text
            .trim()
            .lowercase()
            .replace(spaces, " ")
            .replace(Regex("[‘’ʻʼ`']"), "'")
            .replace(Regex("[.!?…]+$"), "")

    fun shortTitle(raw: String): String {
        val stripped = stripWakePhrase(raw).replace(spaces, " ").trim()
        if (stripped.isEmpty()) return ""
        starterTitles[lookupKey(stripped)]?.let { return it }
        return shorten(stripped, MAX_TITLE_LENGTH, 24)
    }

    fun previewLine(raw: String): String {
        val stripped = stripWakePhrase(raw).replace(spaces, " ").trim()
        return if (stripped.isEmpty()) "" else shorten(stripped, MAX_PREVIEW_LENGTH, 40)
    }

    // Cut at [max], back to the last space when it is past [minSpace], with an ellipsis.
    private fun shorten(
        text: String,
        max: Int,
        minSpace: Int,
    ): String {
        if (text.length <= max) return text
        val cut = text.substring(0, max)
        val space = cut.lastIndexOf(' ')
        return (if (space > minSpace) cut.substring(0, space) else cut.trimEnd()) + "…"
    }

    private val uzLetters = Regex("[ўқғҳЎҚҒҲ]")
    private val cyrillic = Regex("[А-Яа-яЁё]")
    private val uzMarkers =
        listOf(
            "qabul", "nechta", "bugun", "kecha", "bemor", "shifokor", "hozir", "iltimos", "salom", "rahmat",
            "o'", "gʻ", "qʻ", "soat", "oktabr", "oktyabr", "ko'chir", "kochir", "dagi", "kungi", "to'g'ri",
            "keladi", "hafta", "vaqt", "bo'sh", "ertaga", "qara", "uchun",
        )
    private val enWords =
        setOf(
            "the", "please", "which", "should", "what", "schedule", "appointment", "reschedule", "today",
            "tomorrow", "show", "list", "move", "how", "many",
        )
    private val tokenSplit = Regex("[^\\p{L}']+")

    /**
     * The language of one user turn (`EN`, `RU`, `UZ`), or null to keep the chat's (02 §0): Uzbek Cyrillic letters,
     * other Cyrillic, Uzbek marker words, two or more English function words. Short Uzbek date and time fragments
     * ("24-oktabr soat 8:00 ga") stay Uzbek.
     */
    fun detectReplyLanguage(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (uzLetters.containsMatchIn(t)) return "UZ"
        if (cyrillic.containsMatchIn(t)) return "RU"
        val lower = t.lowercase()
        if (uzMarkers.any { lower.contains(it) }) return "UZ"
        return if (lower.split(tokenSplit).count { it in enWords } >= 2) "EN" else null
    }
}

/** Whether a chat is about a patient, and who, when one name is clear. */
data class PatientMark(
    val linked: Boolean,
    val name: String? = null,
)

/**
 * The patient a stored tool card is about (Flutter's `IntelligenceChatContext`): clinic-wide cards (schedule, free
 * slots, patterns, most visited) never link one, even when their rows name patients; a search links when it found
 * someone and names them only when it found exactly one; the placeholder name "Patient" doesn't count.
 */
object ChatContext {
    private val clinicWideCards = setOf("schedule", "free_slots", "clinic_patterns", "most_visited")
    private val clinicWideTools = setOf("get_today_schedule", "list_free_slots")

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    private fun JsonObject.has(key: String): Boolean = get(key).let { it != null && it !is JsonNull }

    private fun cardType(p: JsonObject) = p.str("cardType").orEmpty()

    private fun isSearch(
        p: JsonObject,
        tool: String?,
    ) = cardType(p) == "patient_search" || tool == "search_patients"

    private fun excluded(
        p: JsonObject,
        tool: String?,
    ): Boolean {
        val type = cardType(p)
        return type in clinicWideCards || (tool in clinicWideTools && type.isEmpty())
    }

    private fun matches(p: JsonObject): List<JsonObject> =
        (p["matches"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun cleanName(raw: JsonElement?): String? {
        val name =
            (raw as? JsonPrimitive)?.takeUnless {
                it is JsonNull
            }?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        return name?.trim()?.takeIf { it.isNotEmpty() && !it.equals("patient", ignoreCase = true) }
    }

    fun isPatientLinked(
        payload: JsonObject?,
        toolName: String?,
    ): Boolean {
        if (payload == null || payload.isEmpty() || excluded(payload, toolName)) return false
        if (isSearch(payload, toolName)) {
            return matches(payload).any { it.has("patientId") || cleanName(it["patientName"]) != null }
        }
        return payload.has("patientId") || cleanName(payload["patientName"]) != null
    }

    fun patientName(
        payload: JsonObject?,
        toolName: String?,
    ): String? {
        if (payload == null || !isPatientLinked(payload, toolName)) return null
        if (isSearch(payload, toolName)) return matches(payload).singleOrNull()?.let { cleanName(it["patientName"]) }
        return cleanName(payload["patientName"])
    }

    /** Over a chat's cards, newest first: linked if any card is; the newest clear name. */
    fun summarize(items: List<Pair<JsonObject?, String?>>): PatientMark {
        var linked = false
        var name: String? = null
        for ((payload, tool) in items.asReversed()) {
            if (!isPatientLinked(payload, tool)) continue
            linked = true
            if (name == null) name = patientName(payload, tool)
        }
        return PatientMark(linked, name)
    }
}

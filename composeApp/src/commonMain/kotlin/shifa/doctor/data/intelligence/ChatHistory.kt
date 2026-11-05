package shifa.doctor.data.intelligence

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import shifa.doctor.data.model.AiMessage

/**
 * What the morning stream is sent (02 §3.3, Flutter `_askMorningAi` / `_assistantContentForApi`): every user message,
 * and every assistant message with hidden tags from its card that the backend's prompt reads. **Backend prompt
 * contract: the tags are kept byte for byte.** The tags are never shown.
 */
object ChatHistory {
    /** The conversation so far as the backend gets it, then the new user message. */
    fun messages(
        stored: List<ChatMessage>,
        newUserText: String,
    ): List<AiMessage> =
        stored.map { m ->
            if (m.isUser) {
                AiMessage(
                    ChatMessage.ROLE_USER,
                    m.content,
                )
            } else {
                AiMessage(ChatMessage.ROLE_ASSISTANT, assistantContent(m))
            }
        } + AiMessage(ChatMessage.ROLE_USER, newUserText)

    /**
     * The reply plus `<appointment_query>` (the card's `queryContext` as JSON), `<appointment_list>` (the appointment
     * ids it showed), `<appointment_action>` (a cancellation's token) or `<appointment_focus>` (a focused visit).
     */
    fun assistantContent(m: ChatMessage): String {
        val out = StringBuilder(m.content.trim())
        val card = m.cardPayload
        card?.get("queryContext")?.takeUnless {
            it is JsonNull
        }?.let { out.append("\n<appointment_query>").append(dartJson(it)).append("</appointment_query>") }
        appointmentIds(card).takeIf { it.isNotEmpty() }?.let {
            out.append("\n<appointment_list>").append(it.joinToString(",")).append("</appointment_list>")
        }
        (card?.get("actionContext") as? JsonObject)?.let { action ->
            val type = action.text("type")
            val token = action["token"]?.takeUnless { it is JsonNull }
            val focus = action["appointmentId"]?.takeUnless { it is JsonNull }
            if (type == "CANCEL" && token != null) {
                out.append(
                    "\n<appointment_action>",
                ).append(plain(token)).append("</appointment_action>")
            }
            if (type == "FOCUS" && focus != null) {
                out.append(
                    "\n<appointment_focus>",
                ).append(plain(focus)).append("</appointment_focus>")
            }
        }
        return out.toString().trim()
    }

    /** `appointmentId`, `appointment.appointmentId` and each `appointments[].appointmentId`, numbers only. */
    fun appointmentIds(card: JsonObject?): List<Long> {
        if (card == null) return emptyList()
        val ids = mutableListOf<Long>()

        fun add(e: JsonElement?) =
            (e as? JsonPrimitive)
                ?.takeUnless { it.isString }
                // Dart's `num.toInt()`: 101.0 is 101.
                ?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
                ?.let { ids += it }
        add(card["appointmentId"])
        (card["appointment"] as? JsonObject)?.let { add(it["appointmentId"]) }
        (card["appointments"] as? JsonArray)?.forEach { (it as? JsonObject)?.let { row -> add(row["appointmentId"]) } }
        return ids
    }

    /**
     * The patient the conversation is about, sent as `patientId` so a booking sticks to the right person (02 §3.7):
     * the newest search card with one match, or with several the match whose name words (over two letters) all appear
     * in the last four user messages; else the newest patient card (propose, appointments, documents, viewer, dental,
     * brief). Search wins over propose, so a wrong proposal can't stick.
     */
    fun focusPatientId(stored: List<ChatMessage>): Long? {
        val cards = stored.filter { !it.isUser && it.cardPayload != null }.asReversed()
        val recentUser =
            stored.filter { it.isUser }.takeLast(
                4,
            ).asReversed().joinToString(" ") { it.content }.lowercase()
        for (m in cards) {
            val card = m.cardPayload ?: continue
            val type = card.text("cardType") ?: m.toolName.orEmpty()
            if (type != "patient_search" && m.toolName != "search_patients") continue
            val matches = (card["matches"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
            if (matches.isEmpty()) continue
            if (matches.size == 1) return matches.first().long("patientId") ?: continue
            matches.firstOrNull { row ->
                val tokens = row.text("patientName").orEmpty().lowercase().split(Regex("\\s+")).filter { it.length > 2 }
                tokens.isNotEmpty() && tokens.all { recentUser.contains(it) }
            }?.long("patientId")?.let { return it }
        }
        for (m in cards) {
            val card = m.cardPayload ?: continue
            val type = card.text("cardType") ?: m.toolName.orEmpty()
            if (type in PATIENT_CARDS || m.toolName in PATIENT_TOOLS) card.long("patientId")?.let { return it }
        }
        return null
    }

    private val PATIENT_CARDS =
        setOf(
            "propose_appointment",
            "propose_reschedule",
            "patient_appointments",
            "patient_documents",
            "document_viewer",
            "dental_chart",
            "patient_brief",
        )
    private val PATIENT_TOOLS =
        setOf(
            "propose_appointment",
            "propose_reschedule",
            "list_patient_documents",
            "open_patient_document",
            "open_dental_chart",
            "brief_patient",
        )

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeUnless {
            it is JsonNull
        }?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.let {
                p ->
            p.longOrNull ?: p.contentOrNull?.toDoubleOrNull()?.toLong()
        }

    // Dart's `'$value'` of a JSON scalar: strings bare, numbers and booleans as written.
    private fun plain(e: JsonElement): String = (e as? JsonPrimitive)?.contentOrNull ?: dartJson(e)

    // Dart's `jsonEncode`: compact, the keys in the order they came.
    private fun dartJson(e: JsonElement): String = e.toString()
}

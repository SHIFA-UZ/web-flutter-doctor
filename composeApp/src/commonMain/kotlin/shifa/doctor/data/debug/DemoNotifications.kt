package shifa.doctor.data.debug

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The demo inbox (NOTIF-01), in memory: one of each kind the doctor gets, newest first, paged, read one or all, and the
 * unread count, answered as the backend does (English `title`/`message`, upper-case types, Spring's `Page`).
 */
internal class DemoNotifications(
    private val now: () -> Instant = { currentInstant() },
) {
    private class Row(
        val id: Long,
        val type: String,
        val ago: Duration,
        val title: String,
        val message: String,
        val fields: Map<String, Any> = emptyMap(),
        var read: Boolean = false,
    )

    private val rows: MutableList<Row> = seed()

    /** The answer to a notifications request, or null when [path] isn't one. */
    fun handle(
        method: String,
        path: String,
        query: Map<String, String>,
    ): JsonElement? =
        when {
            method == "GET" && path == Endpoints.NOTIFICATIONS_UNREAD ->
                buildJsonObject { put("count", rows.count { !it.read }) }
            method == "GET" && path == Endpoints.NOTIFICATIONS_PAGE -> {
                val page = query["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val size = query["size"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                val all = rows.sortedBy { it.ago }
                val from = (page * size).coerceAtMost(all.size)
                val to = (from + size).coerceAtMost(all.size)
                buildJsonObject {
                    put("content", JsonArray(all.subList(from, to).map(::json)))
                    put("number", page)
                    put("size", size)
                    put("totalElements", all.size)
                    put("last", to >= all.size)
                    put("first", page == 0)
                }
            }
            method == "PUT" && path == Endpoints.NOTIFICATIONS_READ_ALL -> {
                rows.forEach { it.read = true }
                JsonObject(emptyMap())
            }
            method == "PUT" && path.matches(Regex("notifications/\\d+/read")) -> {
                val id = path.removePrefix("notifications/").removeSuffix("/read").toLong()
                rows.firstOrNull { it.id == id }?.read = true
                JsonObject(emptyMap())
            }
            else -> null
        }

    private fun json(r: Row): JsonObject {
        val at = now() - r.ago
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(r.id),
                "type" to JsonPrimitive(r.type),
                "title" to JsonPrimitive(r.title),
                "message" to JsonPrimitive(r.message),
                "createdAt" to JsonPrimitive(at.toString()),
                "readAt" to
                    if (r.read) {
                        JsonPrimitive(
                            at.plus(1.minutes).toString(),
                        )
                    } else {
                        kotlinx.serialization.json.JsonNull
                    },
            ) +
                r.fields.mapValues { (_, v) ->
                    when (v) {
                        is Number -> JsonPrimitive(v)
                        is Duration -> JsonPrimitive((now() + v).toString())
                        else -> JsonPrimitive(v.toString())
                    }
                },
        )
    }

    private fun seed(): MutableList<Row> {
        val dilnoza = DemoChat.DEMO_NAMES.getValue(11)
        val jasur = DemoChat.DEMO_NAMES.getValue(12)
        val malika = DemoChat.DEMO_NAMES.getValue(13)
        val bekzod = DemoChat.DEMO_NAMES.getValue(14)
        return mutableListOf(
            Row(
                501,
                "APPOINTMENT_BOOKED_BY_PATIENT",
                4.minutes,
                "New Appointment Booked",
                "Patient $malika booked an appointment.",
                mapOf(
                    "appointmentId" to 101,
                    "patientId" to 13,
                    "patientName" to malika,
                    "appointmentStartAt" to 1.days,
                ),
            ),
            Row(
                502,
                "CHAT_MESSAGE",
                40.minutes,
                "New message",
                "You have a new patient message.",
                mapOf("patientId" to 11),
            ),
            Row(
                503,
                "APPOINTMENT_CANCELLED_BY_PATIENT",
                2.hours,
                "Appointment Cancelled",
                "Patient $jasur cancelled their appointment.",
                mapOf(
                    "appointmentId" to 102,
                    "patientId" to 12,
                    "patientName" to jasur,
                    "appointmentStartAt" to 2.days,
                ),
            ),
            Row(
                504,
                "TASK_COMPLETED",
                3.hours,
                "Task completed",
                "$bekzod completed \"Blood pressure diary\".",
                mapOf("taskId" to 501, "patientId" to 14, "patientName" to bekzod),
            ),
            Row(
                505,
                "DOCUMENT_ACCESS_REQUEST",
                5.hours,
                "Document access request",
                "A doctor requested access to a document.",
                mapOf(
                    "patientId" to 11,
                    "patientName" to dilnoza,
                    "documentId" to 7,
                    "documentTitle" to "Blood test results",
                    "documentAccessRequestId" to 3,
                    "requestingDoctorName" to DemoChat.DEMO_DOCTORS.getValue(21),
                ),
            ),
            Row(
                506,
                "AI_SCRIBE_READY",
                26.hours,
                "AI notes ready",
                "AI scribe notes are ready for review.",
                mapOf("appointmentId" to 101, "patientId" to 11, "patientName" to dilnoza),
                read = true,
            ),
            Row(
                507,
                "APPOINTMENT_PAID_BY_PATIENT",
                27.hours,
                "Payment received",
                "Patient $dilnoza paid for their appointment.",
                mapOf("appointmentId" to 101, "patientId" to 11, "patientName" to dilnoza),
                read = true,
            ),
            Row(
                508,
                "CARE_PARTNERSHIP_INVITE",
                3.days,
                "Therapy partner invite",
                "A doctor invited you to co-treat a patient.",
                mapOf("patientId" to 12, "patientName" to jasur),
                read = true,
            ),
            Row(
                509,
                "GENERAL",
                4.days,
                "Welcome to Shifa",
                "Your clinic workspace is ready.",
                read = true,
            ),
        )
    }
}

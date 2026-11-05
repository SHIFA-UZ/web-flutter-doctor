package shifa.doctor.data.intelligence

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import shifa.doctor.data.model.ClinicPattern

/** One appointment row of a schedule or patient-appointments card. */
data class CardVisit(
    val time: String,
    val patientName: String?,
    val status: String?,
    val date: LocalDate? = null,
    val dateLabel: String? = null,
    val appointmentId: Long? = null,
)

/** A free slot a booking card offers: [time] is the practice-local "HH:mm" shown, [startAt] the UTC instant sent. */
data class CardSlot(
    val startAt: String?,
    val time: String,
    val minutes: Int,
    val locationId: Long? = null,
    val locationLabel: String? = null,
)

/** One visit of a cancellation card: the backend's English "Thursday 30 October at 10:30" ([whenLabel]). */
data class CancelRow(
    val patientName: String?,
    val whenLabel: String?,
    val status: String?,
)

data class CardPatient(
    val name: String?,
    val phone: String? = null,
    val patientId: Long? = null,
)

/**
 * A tool card as the app reads it (02 §4.4, Flutter `IntelligenceActionCard`): the type from `cardType`, else the
 * tool's name (`get_today_schedule` → schedule…). Rows are capped as Flutter's cards were; [more] counts the rest
 * (flow 03/07–08 target "+N more"). INTEL-06 adds the booking cards; INTEL-07…08 the clinical and document cards,
 * until then they are [Other].
 */
sealed interface IntelCard {
    data class Schedule(
        val count: Int,
        val visits: List<CardVisit>,
        val more: Int,
        val date: LocalDate?,
    ) : IntelCard

    data class PatientAppointments(
        val patientName: String?,
        val rangeLabel: String?,
        val count: Int,
        val visits: List<CardVisit>,
        val more: Int,
    ) : IntelCard

    data class FreeSlots(
        val date: LocalDate?,
        val dateLabel: String?,
        val slots: List<String>,
        val more: Int,
        val hint: String?,
    ) : IntelCard

    data class PatientSearch(
        val matches: List<CardPatient>,
        val fuzzy: Boolean,
        val query: String?,
        val hint: String?,
    ) : IntelCard

    data class MostVisited(
        val patients: List<Pair<String, Long?>>,
        val hint: String?,
    ) : IntelCard

    data class Patterns(
        val patterns: List<ClinicPattern>,
        val hint: String?,
    ) : IntelCard

    /**
     * A booking ([move] false, `propose_appointment`, flow 03/14) or reschedule (`propose_reschedule`, flow 03/15)
     * proposal. With [workflow] the backend books when the doctor says "yes" and the card only shows; without it the
     * app books from the card's buttons. [done] once booked or moved (the backend's follow-up payload, or the app's own
     * booking saved into the card); [status] is the backend's line for it.
     */
    data class Proposal(
        val move: Boolean,
        val patientId: Long?,
        val patientName: String?,
        val appointmentId: Long?,
        val date: LocalDate?,
        val dateLabel: String?,
        val current: String?,
        val currentDate: LocalDate?,
        val slots: List<CardSlot>,
        val preferred: CardSlot?,
        val time: String?,
        val workflow: Boolean,
        val done: Boolean,
        val status: String?,
        val error: String?,
        val hint: String?,
        val isVideo: Boolean,
        val reason: String?,
        val dismissed: Boolean,
    ) : IntelCard

    /**
     * The visits a cancellation would touch (`cancel_preview`, flow 03/16 target card) or did touch (`cancel_result`,
     * [result]). [awaiting]: the backend holds a cancellation for this conversation until the doctor says "yes".
     */
    data class Cancel(
        val rows: List<CancelRow>,
        val skipped: Int,
        val count: Int,
        val awaiting: Boolean,
        val result: Boolean,
        val hint: String?,
    ) : IntelCard

    data class Brief(
        val text: String,
    ) : IntelCard

    /**
     * A known card that came back with an `error` (an unknown patient, no briefing): its spoken line or the error,
     * under the card's own title, never an empty list (Codex review; `DoctorMorningToolService` sends these).
     */
    data class Failed(
        val type: String,
        val line: String,
    ) : IntelCard

    /** A card the app doesn't draw yet (or doesn't know): its spoken line, error or status. */
    data class Other(
        val type: String,
        val line: String?,
    ) : IntelCard

    companion object {
        const val SCHEDULE_ROWS = 8
        const val PATIENT_ROWS = 12
        const val SLOT_CHIPS = 24

        // The cards this ticket draws, by card type and tool name.
        private val READ_ONLY =
            setOf(
                "schedule", "get_today_schedule", "patient_appointments", "list_patient_appointments", "free_slots",
                "list_free_slots", "patient_search", "search_patients", "most_visited", "get_most_visited_patients",
                "clinic_patterns", "get_clinic_patterns", "patient_brief", "brief_patient",
            )

        val PROPOSALS = setOf("propose_appointment", "propose_reschedule")

        // Keys the app adds to a stored proposal: its own booking, and the doctor's Cancel.
        const val KEY_BOOKED = "booked"
        const val KEY_MOVED = "moved"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_BOOKED_TIME = "bookedTime"

        fun typeOf(
            toolName: String?,
            payload: JsonObject,
        ): String = payload.text("cardType") ?: toolName.orEmpty()

        fun of(
            toolName: String?,
            payload: JsonObject,
        ): IntelCard {
            val type = typeOf(toolName, payload)
            val hint = payload.text("spokenHint")
            payload.text("error")?.let { error -> if (type in READ_ONLY) return Failed(type, hint ?: error) }
            return when (type) {
                in PROPOSALS -> proposal(type, payload, hint)
                "cancel_preview", "cancel_result" -> {
                    val result = type == "cancel_result"
                    val rows =
                        if (result) payload.rows("cancelled") + payload.rows("failed") else payload.rows("appointments")
                    Cancel(
                        rows.map { CancelRow(it.text("patientName"), it.text("whenLabel"), it.text("status")) },
                        payload.rows("skipped").size,
                        payload.int("count") ?: rows.size,
                        !result && payload.text(
                            "status",
                        ) == "AWAITING_CONFIRMATION" && payload["actionContext"] is JsonObject,
                        result,
                        hint,
                    )
                }
                "schedule", "get_today_schedule" -> {
                    val visits = payload.rows("appointments").map(::visit)
                    Schedule(
                        payload.int("count") ?: visits.size,
                        visits.take(SCHEDULE_ROWS),
                        (visits.size - SCHEDULE_ROWS).coerceAtLeast(0),
                        payload.date("date"),
                    )
                }
                "patient_appointments", "list_patient_appointments" -> {
                    val visits = payload.rows("appointments").map(::visit)
                    PatientAppointments(
                        payload.text("patientName"),
                        payload.text("rangeLabel"),
                        payload.int("count") ?: visits.size,
                        visits.take(PATIENT_ROWS),
                        (visits.size - PATIENT_ROWS).coerceAtLeast(0),
                    )
                }
                "free_slots", "list_free_slots" -> {
                    val slots = payload.rows("slots").mapNotNull { it.text("startLocal") }
                    FreeSlots(
                        payload.date("date"),
                        payload.text("dateLabel") ?: payload.text("date"),
                        slots.take(SLOT_CHIPS),
                        (slots.size - SLOT_CHIPS).coerceAtLeast(0),
                        hint,
                    )
                }
                "patient_search", "search_patients" ->
                    PatientSearch(
                        payload.rows(
                            "matches",
                        ).map { CardPatient(it.text("patientName"), it.text("phone"), it.long("patientId")) },
                        payload.flag("fuzzy"),
                        payload.text("query"),
                        hint,
                    )
                "most_visited", "get_most_visited_patients" ->
                    MostVisited(
                        payload.rows("patients").map { r ->
                            (
                                r.text(
                                    "patientName",
                                ) ?: r.text("label")
                            ).orEmpty() to (r.long("visitCount") ?: r.long("count"))
                        },
                        hint,
                    )
                "clinic_patterns", "get_clinic_patterns" ->
                    Patterns(
                        payload.rows("patterns").map { r ->
                            ClinicPattern(
                                r.text("key").orEmpty(),
                                r.text("label") ?: r.text("key").orEmpty(),
                                r.text("factType").orEmpty(),
                                r.int("count") ?: 0,
                            )
                        },
                        hint,
                    )
                // Fix (ticket): rendered as a briefing (headings, bullets, bold), not plain text.
                "patient_brief", "brief_patient" -> Brief(payload.text("briefing") ?: hint.orEmpty())
                else -> Other(type, hint ?: payload.text("error") ?: payload.text("statusMessage"))
            }
        }

        private fun proposal(
            type: String,
            p: JsonObject,
            hint: String?,
        ): IntelCard {
            val done = p.flag(KEY_BOOKED) || p.flag(KEY_MOVED)
            // A turn of the booking conversation without a proposal (no booking waiting, "which patient?"): its line.
            if (!done && p["slots"] !is JsonArray) return Failed(type, hint ?: p.text("error").orEmpty())
            val slots = p.rows("slots").mapNotNull(::slot)
            val preferred = (p["preferredSlot"] as? JsonObject)?.let(::slot)
            val current = listOfNotNull(p.text("currentDateLabel"), p.text("currentStartLocal")).joinToString(" ")
            return Proposal(
                move = type == "propose_reschedule",
                patientId = p.long("patientId"),
                patientName = p.text("patientName"),
                appointmentId = p.long("appointmentId"),
                date = p.date("date"),
                dateLabel = p.text("dateLabel") ?: p.text("date"),
                current = current.ifEmpty { null },
                currentDate = CardText.parseEnglishDate(p.text("currentDateLabel")),
                slots = slots,
                preferred = preferred,
                // The slot the app booked may be one of the other times.
                time = p.text(KEY_BOOKED_TIME) ?: preferred?.time ?: p.text("requestedTime")?.take(5),
                workflow = p.text("workflowId") != null,
                done = done,
                // The backend's line for a finished booking: its status, or the hint of the completed workflow.
                // "Moved the appointment to…" (no name) says no more than the app's own line, and reads half English.
                status =
                    p.text("statusMessage")
                        ?: hint.takeIf {
                            done && p.text(
                                "workflowStatus",
                            ) == "COMPLETED" && it?.startsWith("Moved the appointment") == false
                        },
                error = p.text("error"),
                hint = hint,
                isVideo = p.flag("isVideo"),
                reason = p.text("reason"),
                dismissed = p.flag(KEY_DISMISSED),
            )
        }

        private fun slot(r: JsonObject): CardSlot? =
            r.text("startLocal")?.let {
                CardSlot(
                    r.text("startAt"),
                    it,
                    r.int("slotMinutes") ?: 30,
                    r.long("locationId"),
                    r.text("locationLabel"),
                )
            }

        private fun visit(r: JsonObject) =
            CardVisit(
                r.text("startLocal").orEmpty(),
                r.text("patientName"),
                r.text("status"),
                r.date("date"),
                r.text("dateLabel"),
                r.long("appointmentId"),
            )
    }
}

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.takeUnless {
        it is JsonNull || it.isString
    }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

private fun JsonObject.date(key: String): LocalDate? =
    text(key)?.let {
        runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
    }

private fun JsonObject.rows(key: String): List<JsonObject> =
    (
        get(
            key,
        ) as? JsonArray
    ).orEmpty().filterIsInstance<JsonObject>()

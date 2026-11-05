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

data class CardPatient(
    val name: String?,
    val phone: String? = null,
    val patientId: Long? = null,
)

/**
 * A tool card as the app reads it (02 §4.4, Flutter `IntelligenceActionCard`): the type from `cardType`, else the
 * tool's name (`get_today_schedule` → schedule…). Rows are capped as Flutter's cards were; [more] counts the rest
 * (flow 03/07–08 target "+N more"). INTEL-06…08 add the booking, clinical and document cards; until then they are
 * [Other].
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

        fun of(
            toolName: String?,
            payload: JsonObject,
        ): IntelCard {
            val type = payload.text("cardType") ?: toolName.orEmpty()
            val hint = payload.text("spokenHint")
            payload.text("error")?.let { error -> if (type in READ_ONLY) return Failed(type, hint ?: error) }
            return when (type) {
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
                        (payload["fuzzy"] as? JsonPrimitive)?.booleanOrNull == true,
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

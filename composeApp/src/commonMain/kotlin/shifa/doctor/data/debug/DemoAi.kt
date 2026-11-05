package shifa.doctor.data.debug

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import shifa.doctor.data.api.apiJson
import shifa.doctor.utils.currentInstant

/**
 * The demo Shifa AI (AI-01), in memory and free: canned streams in the request's language (the morning one with a
 * schedule card, the patient one ending in a draft; a question containing "fail" ends in an `error` event), a short
 * silent clip for speech, a briefing, and three facts per demo visit that documenting commits, as the backend does;
 * booking, moving and cancelling words get those cards (INTEL-06).
 */
internal class DemoAi {
    private val facts = mutableMapOf<Long, List<JsonObject>>()
    private val patterns = mutableMapOf<String, Int>()
    private var lastTaskId = 700L

    /** The SSE text of one answer: a leading space on each word after the first, as the model's tokens come. */
    fun stream(
        body: String,
        morning: Boolean,
        draftId: String?,
        schedule: JsonArray,
    ): String {
        val request = runCatching { apiJson.parseToJsonElement(body).jsonObject }.getOrNull()
        val language = request?.get("language")?.jsonPrimitive?.content?.uppercase() ?: "EN"
        val asked =
            request?.get("messages")?.jsonArray?.lastOrNull()?.jsonObject?.get("content")?.jsonPrimitive?.content
                ?: request?.get("question")?.jsonPrimitive?.content.orEmpty()
        val out = StringBuilder()
        val action = if (morning) action(asked) else null
        if (action != null) {
            out.event("tool", JsonObject(mapOf("name" to JsonPrimitive(action.first), "payload" to action.second)))
        } else if (morning) {
            val card =
                buildJsonObject {
                    put("cardType", "schedule")
                    put("count", schedule.size)
                    put("rangeLabel", "Today")
                    put("appointments", schedule)
                }
            out.event("tool", JsonObject(mapOf("name" to JsonPrimitive("get_today_schedule"), "payload" to card)))
        }
        val words =
            action?.second?.str("spokenHint")
                ?: (if (morning) MORNING else PATIENT).getValue(language).replace("%d", schedule.size.toString())
        words.split(' ').forEachIndexed { i, w -> out.append("data:").append(if (i == 0) w else " $w").append("\n\n") }
        if (asked.contains("fail", ignoreCase = true)) {
            out.event("error", JsonObject(mapOf("code" to JsonPrimitive("AI_UNAVAILABLE"))))
        } else if (draftId != null) {
            out.event(
                "draft",
                JsonObject(
                    mapOf(
                        "draftId" to JsonPrimitive(draftId),
                        "draftLabel" to JsonPrimitive("AI draft"),
                        "canSave" to JsonPrimitive(true),
                    ),
                ),
            )
        }
        return out.toString()
    }

    fun briefing(): JsonObject =
        JsonObject(
            mapOf(
                "briefing" to JsonPrimitive(BRIEFING),
                "documentCount" to JsonPrimitive(2),
                "appointmentCount" to JsonPrimitive(3),
            ),
        )

    /** Visit [appointment]'s facts for patient [patient]: two kept drafts and one the model dropped as not said. */
    fun visitFacts(
        appointment: Long,
        patient: Long,
    ): JsonObject = JsonObject(mapOf("facts" to JsonArray(factsOf(appointment, patient)), "patterns" to patterns()))

    fun timeline(patient: Long): JsonObject =
        JsonObject(
            mapOf(
                "timeline" to
                    JsonArray(
                        facts.values.flatten().filter {
                            it.long("patientId") == patient && it.str("status") == "COMMITTED"
                        },
                    ),
                "patterns" to patterns(),
            ),
        )

    /** The backend's commit: rejected ids out, kept drafts (or only the accepted ones) in, medication → a task id. */
    fun commit(
        body: String,
        patient: (Long) -> Long?,
    ): JsonObject? {
        val request = runCatching { apiJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val visit = request["appointmentId"]?.jsonPrimitive?.longOrNull ?: return null
        val accept = request["acceptFactIds"]?.ids()
        val reject = request["rejectFactIds"]?.ids().orEmpty()
        val all = factsOf(visit, patient(visit) ?: return null)
        val committed = mutableListOf<JsonObject>()
        val tasks = mutableListOf<Long>()
        facts[visit] =
            all.map { f ->
                val id = f.long("id")
                when {
                    f.str("status") != "DRAFT" -> f
                    id in reject -> f.with("status" to JsonPrimitive("REJECTED"))
                    f.bool("reflectionKept") && (accept == null || id in accept) -> {
                        val task = if (f.str("factType") == "MEDICATION") ++lastTaskId else null
                        task?.let { tasks += it }
                        val type = f.str("factType").orEmpty()
                        patterns[type] = (patterns[type] ?: 0) + 1
                        f
                            .with(
                                "status" to JsonPrimitive("COMMITTED"),
                                "remoteTaskId" to JsonPrimitive(task),
                                "committedAt" to JsonPrimitive(currentInstant().toString()),
                            ).also { committed.add(it) }
                    }
                    accept != null -> f.with("status" to JsonPrimitive("REJECTED"))
                    else -> f
                }
            }
        return JsonObject(
            mapOf(
                "committedFacts" to JsonArray(committed),
                "createdTaskIds" to JsonArray(tasks.map { JsonPrimitive(it) }),
                "patterns" to patterns(),
            ),
        )
    }

    private fun factsOf(
        visit: Long,
        patient: Long,
    ) = facts.getOrPut(visit) {
        listOf(
            fact(
                visit * 10 + 1,
                visit,
                patient,
                "MEDICATION",
                "Ibuprofen 400 mg after meals",
                "3 times a day for 5 days",
            ),
            fact(visit * 10 + 2, visit, patient, "SYMPTOM", "Pain in the lower left molar for three days", null),
            fact(visit * 10 + 3, visit, patient, "DIAGNOSIS", "Pulpitis", null, kept = false),
        )
    }

    private fun fact(
        id: Long,
        visit: Long,
        patient: Long,
        type: String,
        text: String,
        hint: String?,
        kept: Boolean = true,
    ) = buildJsonObject {
        put("id", id)
        put("appointmentId", visit)
        put("patientId", patient)
        put("factType", type)
        put("text", text)
        put("quote", text.lowercase())
        put("scheduleHint", hint)
        put("status", "DRAFT")
        put("reflectionKept", kept)
        put("dropReason", if (kept) null else "Not said in the visit recording")
        put("remoteTaskId", JsonNull)
        put("createdAt", currentInstant().toString())
        put("committedAt", JsonNull)
    }

    private fun patterns() =
        JsonArray(
            patterns.entries.sortedByDescending { it.value }.map { (type, n) ->
                buildJsonObject {
                    put("key", type.lowercase())
                    put("label", type.lowercase().replaceFirstChar(Char::uppercase))
                    put("factType", type)
                    put("count", n)
                }
            },
        )

    /**
     * INTEL-06: booking words get a proposal the app books from its buttons (tomorrow, Jasur Karimov), moving words a
     * move of visit 102, cancelling words a cancellation waiting for "yes", and a confirmation its result, in English
     * as the backend sends them.
     */
    private fun action(asked: String): Pair<String, JsonObject>? {
        val text = asked.trim().lowercase()
        val zone = TimeZone.of("Asia/Tashkent")
        val tomorrow = currentInstant().plus(1, DateTimeUnit.DAY, zone).toLocalDateTime(zone).date

        fun slot(
            hour: Int,
            minute: Int = 0,
        ) = buildJsonObject {
            put("startAt", LocalDateTime(tomorrow, LocalTime(hour, minute)).toInstant(zone).toString())
            put("startLocal", LocalTime(hour, minute).toString())
            put("slotMinutes", 30)
            put("locationId", 1)
        }
        val slots = JsonArray(listOf(slot(10), slot(10, 30), slot(11), slot(14)))
        val visits =
            JsonArray(
                listOf("Jasur Karimov" to "10:30", "Malika Tursunova" to "14:00").map { (name, time) ->
                    buildJsonObject {
                        put("patientName", name)
                        put("whenLabel", "Friday 6 November at $time")
                    }
                },
            )
        return when {
            text in setOf("yes", "ha", "да") ->
                "cancel_appointments" to
                    buildJsonObject {
                        put("cardType", "cancel_result")
                        put("status", "SUCCESS")
                        put("cancelled", visits)
                        put("failed", JsonArray(emptyList()))
                        put("spokenHint", "Cancelled 2. The patients were notified in the app.")
                    }
            listOf("cancel", "отмен", "bekor").any(text::contains) ->
                "cancel_appointments" to
                    buildJsonObject {
                        put("cardType", "cancel_preview")
                        put("status", "AWAITING_CONFIRMATION")
                        put(
                            "actionContext",
                            buildJsonObject {
                                put("type", "CANCEL")
                                put("token", "00000000-0000-0000-0000-000000000001")
                            },
                        )
                        put("appointments", visits)
                        put("skipped", JsonArray(emptyList()))
                        put("count", 2)
                        put(
                            "spokenHint",
                            "I can cancel 2 upcoming visits: Jasur Karimov Friday 6 November at 10:30; " +
                                "Malika Tursunova Friday 6 November at 14:00. Say yes to cancel them. " +
                                "Nothing has been cancelled yet.",
                        )
                    }
            // INTEL-07: visit 102's drafted facts, the patient's viewable document (opened at once, as the backend does
            // for a single match) and a dental diagram.
            listOf("facts", "document everything", "clinical notes").any(text::contains) ->
                "list_draft_visit_facts" to
                    buildJsonObject {
                        val drafts =
                            factsOf(
                                DEMO_VISIT,
                                DEMO_PATIENT,
                            ).filter { it.str("status") == "DRAFT" && it.bool("reflectionKept") }
                        put("cardType", "draft_visit_facts")
                        put("appointmentId", DEMO_VISIT)
                        put("patientId", DEMO_PATIENT)
                        put("patientName", "Jasur Karimov")
                        put(
                            "facts",
                            JsonArray(
                                drafts.map {
                                    buildJsonObject {
                                        put("factId", it.long("id"))
                                        put("factType", it.str("factType"))
                                        put("text", it.str("text"))
                                    }
                                },
                            ),
                        )
                        put(
                            "spokenHint",
                            if (drafts.isEmpty()) {
                                "There are no draft visit facts ready to document for this visit."
                            } else {
                                "There are ${drafts.size} draft facts. Confirm to document them."
                            },
                        )
                    }
            listOf("documents", "x-ray", "analysis").any(text::contains) ->
                "list_patient_documents" to
                    buildJsonObject {
                        put("cardType", "patient_documents")
                        put("patientId", DEMO_PATIENT)
                        put("patientName", "Jasur Karimov")
                        put(
                            "documents",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("documentId", 901)
                                        put("title", "Complete blood count")
                                        put("date", "2026-10-22")
                                        put("category", "BLOOD_TEST")
                                    },
                                ),
                            ),
                        )
                        put("autoOpenDocumentId", 901)
                        put(
                            "spokenHint",
                            "I found 1 document for Jasur Karimov: Complete blood count. Opening it in chat.",
                        )
                    }
            listOf("dental", "tooth", "teeth").any(text::contains) ->
                "open_dental_chart" to
                    buildJsonObject {
                        put("cardType", "dental_chart")
                        put("patientId", DEMO_PATIENT)
                        put("patientName", "Jasur Karimov")
                        put("appointmentId", DEMO_VISIT)
                        put("date", "6 Nov 2026")
                        put("dentition", "permanent")
                        put(
                            "documentation",
                            apiJson.parseToJsonElement(
                                """{"version":2,"dentition":"permanent","teeth":{"36":[{"title":"Filling"}],""" +
                                    """"46":[{"title":"Cleaning"},{"title":"X-ray"}]},""" +
                                    """"notes":"Recheck 36 in a month."}""",
                            ),
                        )
                        put(
                            "toothServiceCounts",
                            buildJsonObject {
                                put("36", 1)
                                put("46", 2)
                            },
                        )
                        put("notes", "Recheck 36 in a month.")
                        put(
                            "spokenHint",
                            "Here is the dental diagram for Jasur Karimov from Friday, 6 November — " +
                                "2 teeth have recorded services.",
                        )
                    }
            listOf("move", "перенес", "ko'chir", "ko‘chir").any(text::contains) ->
                "propose_reschedule" to
                    buildJsonObject {
                        put("cardType", "propose_reschedule")
                        put("appointmentId", 102)
                        put("patientId", 12)
                        put("patientName", "Jasur Karimov")
                        put("currentDateLabel", "6 Nov 2026")
                        put("currentStartLocal", "10:30")
                        put("date", tomorrow.toString())
                        put("slots", slots)
                        put(
                            "spokenHint",
                            "I found 4 free slots on Friday, 6 November for Jasur Karimov. Which time works?",
                        )
                    }
            listOf("book", "запиш", "yoz").any(text::contains) ->
                "propose_appointment" to
                    buildJsonObject {
                        put("cardType", "propose_appointment")
                        put("patientId", 12)
                        put("patientName", "Jasur Karimov")
                        put("date", tomorrow.toString())
                        put("isVideo", false)
                        put("preferredSlot", slot(10))
                        put("slots", slots)
                        put("spokenHint", "Booking Jasur Karimov at 10:00.")
                    }
            else -> null
        }
    }

    private fun StringBuilder.event(
        name: String,
        data: JsonElement,
    ) = append("event:").append(name).append("\ndata:").append(data.toString()).append("\n\n")

    private fun JsonElement.ids() =
        runCatching { jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }.toSet() }.getOrNull()

    private fun JsonObject.with(vararg changes: Pair<String, JsonElement>) = JsonObject(this + changes)

    private fun JsonObject.long(key: String) = get(key)?.jsonPrimitive?.longOrNull

    private fun JsonObject.str(key: String) = get(key)?.jsonPrimitive?.content

    private fun JsonObject.bool(key: String) = get(key)?.jsonPrimitive?.content == "true"

    private companion object {
        const val DEMO_VISIT = 102L
        const val DEMO_PATIENT = 12L
        val MORNING =
            mapOf(
                "EN" to "Good morning. You have %d visits today.",
                "RU" to "Доброе утро. Сегодня у вас %d приёмов.",
                "UZ" to "Xayrli tong. Bugun sizda %d ta qabul bor.",
            )
        val PATIENT =
            mapOf(
                "EN" to "Pain in the lower left molar for three days. Suggest an X-ray and a pulp test.",
                "RU" to "Боль в нижнем левом моляре три дня. Рекомендую снимок и тест пульпы.",
                "UZ" to "Pastki chap oziq tishda uch kundan beri og'riq. Rentgen va pulpa testi tavsiya etiladi.",
            )
        const val BRIEFING =
            "**Summary:** follow-up after treatment of the lower left molar.\n" +
                "- Blood count from 22.10: haemoglobin slightly low.\n- Allergy: penicillin."
    }
}

/** Half a second of silence as 16 kHz mono 16-bit WAV (what `/ai/speak` answers for Uzbek). */
internal fun demoSpeech(): ByteArray {
    val samples = 8_000
    val data = samples * 2
    val out = ByteArray(44 + data)

    fun put(
        at: Int,
        value: Int,
        bytes: Int,
    ) = repeat(bytes) { out[at + it] = (value shr (8 * it)).toByte() }
    "RIFF".encodeToByteArray().copyInto(out, 0)
    put(4, 36 + data, 4)
    "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
    put(16, 16, 4) // fmt chunk size
    put(20, 1, 2) // PCM
    put(22, 1, 2) // mono
    put(24, 16_000, 4)
    put(28, 32_000, 4) // byte rate
    put(32, 2, 2) // block align
    put(34, 16, 2) // bits per sample
    "data".encodeToByteArray().copyInto(out, 36)
    put(40, data, 4)
    return out
}

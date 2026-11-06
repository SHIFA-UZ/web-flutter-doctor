package shifa.doctor.data.debug

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.utils.currentInstant

/**
 * The demo care partnerships (CARE-01…03), in memory, the demo doctor (9001) on both sides: an active one with
 * updates, an invite waiting for their answer, a completed one; a small directory to invite from. The backend's rules:
 * only the partner answers a pending invite, only the initiator cancels it, either completes an active one, updates only
 * while active, the same pending invite twice is a 409.
 */
internal class DemoCare(
    private val patientName: (Long) -> String?,
) {
    private data class Doctor(
        val id: Long,
        val first: String,
        val last: String,
        val profession: String,
        val clinic: String,
        val city: String,
    ) {
        val name get() = "$first $last"
    }

    private data class Partnership(
        val id: Long,
        val patientId: Long,
        val initiator: Long,
        val partner: Long,
        var status: String,
        val specialty: String?,
        val message: String?,
        val createdAt: String,
        var updatedAt: String,
    )

    private data class Update(
        val id: Long,
        val partnershipId: Long,
        val author: Long,
        val body: String,
        val at: String,
    )

    private val me = ME
    private val directory =
        listOf(
            Doctor(31, "Madina", "Rasulova", "Cardiologist", "Republican Cardiology Centre", "Tashkent"),
            Doctor(32, "Behzod", "Normatov", "Orthodontist", "Dentmaster", "Bukhara"),
            Doctor(33, "Kamola", "Yuldasheva", "Periodontist", "Smile Clinic", "Samarkand"),
            Doctor(34, "Javlon", "Ismoilov", "Oral surgeon", "MedLine", "Tashkent"),
            Doctor(35, "Zarina", "Hakimova", "Endocrinologist", "Akfa Medline", "Tashkent"),
        )
    private val partnerships =
        mutableListOf(
            Partnership(
                701, 11, me, 31, "ACTIVE", "Cardiologist", "Chest pain on exertion, please assess.",
                "2026-10-20T06:00:00Z", "2026-11-02T09:30:00Z",
            ),
            Partnership(
                702, 12, 32, me, "PENDING", "Orthodontist review", "Crowding in the lower arch; braces assessment.",
                "2026-11-05T13:20:00Z", "2026-11-05T13:20:00Z",
            ),
            Partnership(
                703, 13, me, 33, "COMPLETED", "Periodontist", null, "2026-09-01T07:00:00Z", "2026-10-10T08:00:00Z",
            ),
        )
    private val updates =
        mutableListOf(
            Update(1, 701, 31, "ECG normal; started a stress test plan.", "2026-10-28T08:10:00Z"),
            Update(2, 701, me, "Thanks, I'll pause the extraction until it's done.", "2026-11-02T09:30:00Z"),
        )

    /** The answer (status and body) for a care path, or null for any other path. */
    fun handle(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String,
    ): Pair<HttpStatusCode, JsonElement>? {
        if (!path.startsWith(Endpoints.CARE_PARTNERSHIPS)) return null
        val ok = { json: JsonElement -> HttpStatusCode.OK to json }
        if (method == "GET" && path == Endpoints.CARE_PARTNER_SEARCH) {
            val q = query["q"]?.trim()?.lowercase().orEmpty()
            val profession = query["profession"]?.trim()?.lowercase().orEmpty()
            return ok(
                JsonArray(
                    directory
                        .filter { q.isEmpty() || it.name.lowercase().contains(q) }
                        .filter { profession.isEmpty() || it.profession.lowercase().contains(profession) }
                        .take(query["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT)
                        .map(::hit),
                ),
            )
        }
        if (method == "GET" && path == Endpoints.CARE_PARTNERSHIPS) {
            return ok(JsonArray(partnerships.sortedByDescending { it.updatedAt }.map(::dto)))
        }
        if (method == "POST" && path == Endpoints.CARE_PARTNERSHIPS) return invite(body)
        val match = Regex("care-partnerships/(\\d+)(/[a-z]+)?").matchEntire(path) ?: return notFound()
        val p = partnerships.firstOrNull { it.id == match.groupValues[1].toLong() } ?: return notFound()
        val now = currentInstant().toString()

        fun change(
            allowed: Boolean,
            to: String,
        ): Pair<HttpStatusCode, JsonElement> {
            if (!allowed) return HttpStatusCode.BadRequest to error(400, "INVALID_STATE")
            p.status = to
            p.updatedAt = now
            return ok(dto(p))
        }
        return when (method to match.groupValues[2]) {
            "GET" to "" -> ok(dto(p))
            "POST" to "/accept" -> change(p.status == "PENDING" && p.partner == me, "ACTIVE")
            "POST" to "/decline" -> change(p.status == "PENDING" && p.partner == me, "DECLINED")
            "POST" to "/cancel" -> change(p.status == "PENDING" && p.initiator == me, "CANCELLED")
            "POST" to "/complete" -> change(p.status == "ACTIVE", "COMPLETED")
            "GET" to "/progress" -> ok(JsonArray(updates.filter { it.partnershipId == p.id }.map(::update)))
            "POST" to "/progress" -> {
                val text =
                    runCatching { apiJson.parseToJsonElement(body).jsonObject["body"]?.jsonPrimitive?.content }
                        .getOrNull()?.trim().orEmpty()
                if (p.status != "ACTIVE" || text.isEmpty()) {
                    return HttpStatusCode.BadRequest to
                        error(
                            400,
                            "VALIDATION_ERROR",
                        )
                }
                val u = Update((updates.maxOfOrNull { it.id } ?: 0) + 1, p.id, me, text, now)
                updates += u
                p.updatedAt = now
                ok(update(u))
            }
            else -> notFound()
        }
    }

    private fun invite(body: String): Pair<HttpStatusCode, JsonElement> {
        val o = runCatching { apiJson.parseToJsonElement(body).jsonObject }.getOrNull()
        val patientId = o?.get("patientId")?.jsonPrimitive?.longOrNull
        val partnerId = o?.get("partnerDoctorId")?.jsonPrimitive?.longOrNull
        if (patientId == null || partnerId == null || directory.none { it.id == partnerId }) return notFound()
        if (partnerships.any {
                it.status == "PENDING" && it.initiator == me && it.partner == partnerId && it.patientId == patientId
            }
        ) {
            return HttpStatusCode.Conflict to error(409, "CONFLICT")
        }
        val now = currentInstant().toString()
        val p =
            Partnership(
                (partnerships.maxOf { it.id }) + 1, patientId, me, partnerId, "PENDING",
                o["specialtyRequested"]?.jsonPrimitive?.content, o["message"]?.jsonPrimitive?.content, now, now,
            )
        partnerships += p
        return HttpStatusCode.OK to dto(p)
    }

    private fun nameOf(id: Long) =
        if (id == me) ME_NAME else directory.firstOrNull { it.id == id }?.name ?: "Doctor $id"

    private fun hit(d: Doctor) =
        buildJsonObject {
            put("id", d.id)
            put("firstName", d.first)
            put("lastName", d.last)
            put("profession", d.profession)
            put("clinic", d.clinic)
            put("locationCountry", "Uzbekistan")
            put("locationCity", d.city)
        }

    private fun dto(p: Partnership): JsonObject =
        buildJsonObject {
            put("id", p.id)
            put("status", p.status)
            put("patientId", p.patientId)
            put("patientName", patientName(p.patientId))
            put("initiatingDoctorId", p.initiator)
            put("initiatingDoctorName", nameOf(p.initiator))
            put("partnerDoctorId", p.partner)
            put("partnerDoctorName", nameOf(p.partner))
            p.specialty?.let { put("specialtyRequested", it) }
            p.message?.let { put("message", it) }
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
            put("viewerRole", if (p.initiator == me) "INITIATOR" else "PARTNER")
        }

    private fun update(u: Update) =
        buildJsonObject {
            put("id", u.id)
            put("authorDoctorId", u.author)
            put("authorDoctorName", nameOf(u.author))
            put("body", u.body)
            put("createdAt", u.at)
        }

    private fun error(
        status: Int,
        code: String,
    ) = buildJsonObject {
        put("status", status)
        put("code", code)
    }

    private fun notFound() = HttpStatusCode.NotFound to error(404, "NOT_FOUND")

    companion object {
        // The demo doctor (`doctors-me.json` profile id and name).
        const val ME = 9001L
        const val ME_NAME = "Aziza Karimova"
        private const val DEFAULT_LIMIT = 30
    }
}

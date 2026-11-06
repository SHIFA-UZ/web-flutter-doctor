package shifa.doctor.data.debug

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import shifa.doctor.data.api.apiJson
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration.Companion.days

/**
 * The demo clinic's workspace (M11), in memory: its numbers, members with their revenue shares (the default and each
 * doctor's own, as the backend keeps them), and receptionist invitations. Answers only the paths it knows; writes stay
 * here. The demo doctor owns the clinic, so every action can be tried.
 */
internal class DemoClinic {
    private var defaultShare: Int? = 60
    private val ownShares = mutableMapOf<Long, Int?>(21L to null, 22L to 70)
    private val invitations =
        mutableListOf(
            invitation(901, "anna@clinic.uz", consumed = false, days = 5),
            invitation(902, "olim@clinic.uz", consumed = true, days = -3),
        )
    private var lastInvitation = 902L

    fun handles(path: String): Boolean =
        path == "clinics/1/overview-stats" || path == "clinics/1/members" || path == "clinics/1/finance-settings" ||
            path.startsWith("clinics/1/members/") || path.startsWith("clinics/1/invitations")

    /** The clinic's default share as `/me/clinics` reports it. */
    fun defaultShare(): Int? = defaultShare

    fun answer(
        method: HttpMethod,
        path: String,
        body: String,
    ): Pair<HttpStatusCode, String> =
        when {
            path == "clinics/1/overview-stats" ->
                ok(
                    """{"appointmentsToday":3,"activeDoctors":2,"patientsThisMonth":4}""",
                )
            path == "clinics/1/members" -> ok(JsonArray(members()).toString())
            path == "clinics/1/finance-settings" && method == HttpMethod.Patch -> {
                defaultShare = percent(body, "defaultDoctorRevenueSharePercent")
                ok("""{"defaultDoctorRevenueSharePercent":${defaultShare ?: "null"}}""")
            }
            path.matches(Regex("clinics/1/members/\\d+/revenue-share")) && method == HttpMethod.Patch -> {
                val id = path.split('/')[3].toLong()
                if (id !in ownShares) {
                    HttpStatusCode.NotFound to """{"status":404,"code":"NOT_FOUND"}"""
                } else {
                    ownShares[id] = percent(body, "doctorRevenueSharePercent")
                    ok("{}")
                }
            }
            path == "clinics/1/invitations" && method == HttpMethod.Get -> ok(JsonArray(invitations).toString())
            path == "clinics/1/invitations" && method == HttpMethod.Post -> {
                val email =
                    runCatching {
                        apiJson.parseToJsonElement(
                            body,
                        ).jsonObject["email"]?.jsonPrimitive?.content
                    }.getOrNull()
                invitations.add(0, invitation(++lastInvitation, email.orEmpty(), consumed = false, days = 7))
                HttpStatusCode.Created to """{"id":$lastInvitation,"clinicId":1,"emailSentTo":"$email"}"""
            }
            path.matches(Regex("clinics/1/invitations/\\d+")) && method == HttpMethod.Delete -> {
                val id = path.substringAfterLast('/').toLong()
                invitations.removeAll { it["id"]?.jsonPrimitive?.content?.toLong() == id }
                HttpStatusCode.NoContent to ""
            }
            else -> HttpStatusCode.MethodNotAllowed to """{"status":405,"code":"METHOD_NOT_ALLOWED"}"""
        }

    private fun members(): List<JsonObject> =
        listOf(
            member(21, 101, "Aziza Karimova", "OWNER"),
            member(22, 102, "Bobur Aliyev", "DOCTOR"),
            member(null, 103, "Dilfuza Yusupova", "RECEPTIONIST"),
        )

    private fun member(
        doctorProfileId: Long?,
        userId: Long,
        name: String,
        role: String,
    ) = buildJsonObject {
        put("doctorProfileId", doctorProfileId?.let(::JsonPrimitive) ?: JsonNull)
        put("userId", userId)
        put("displayName", name)
        put("membershipRole", role)
        val own = doctorProfileId?.let { ownShares[it] }
        put("doctorRevenueSharePercent", own?.let(::JsonPrimitive) ?: JsonNull)
        val effective = if (doctorProfileId == null) null else own ?: defaultShare
        put("effectiveRevenueSharePercent", effective?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun invitation(
        id: Long,
        email: String,
        consumed: Boolean,
        days: Int,
    ) = buildJsonObject {
        put("id", id)
        put("emailSentTo", email)
        put("expiresAt", (currentInstant() + days.days).toString())
        put("consumed", consumed)
        put("pending", !consumed)
    }

    private fun percent(
        body: String,
        field: String,
    ): Int? = runCatching { apiJson.parseToJsonElement(body).jsonObject[field]?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun ok(json: String) = HttpStatusCode.OK to json
}

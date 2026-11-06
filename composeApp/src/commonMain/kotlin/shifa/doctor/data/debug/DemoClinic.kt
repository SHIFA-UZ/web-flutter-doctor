package shifa.doctor.data.debug

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.model.CatalogItem
import shifa.doctor.data.model.CatalogItemPatch
import shifa.doctor.data.model.NewCatalogItem
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

    // The clinic's catalogue (CLINIC-07); the plan services list it with the doctors' own services.
    private val catalog =
        mutableListOf(
            CatalogItem(1, 1, "D3310", "Root canal treatment", 80_000_000),
            CatalogItem(
                2,
                1,
                null,
                "Ceramic crown",
                150_000_000,
                appliesToAllDoctors = false,
                assignedDoctorProfileIds = listOf(21),
            ),
        )
    private val groups = mapOf(1L to ("Endodontics" to 1), 2L to ("Prosthetics" to 2))

    fun handles(path: String): Boolean =
        path == "clinics/1/overview-stats" || path == "clinics/1/members" || path == "clinics/1/finance-settings" ||
            path.startsWith("clinics/1/members/") || path.startsWith("clinics/1/invitations") ||
            path == Endpoints.PLAN_SERVICES || path.startsWith(Endpoints.CATALOG_ITEMS)

    /** The clinic's default share as `/me/clinics` reports it. */
    fun defaultShare(): Int? = defaultShare

    fun answer(
        method: HttpMethod,
        path: String,
        body: String,
        parameters: Parameters = Parameters.Empty,
    ): Pair<HttpStatusCode, String> =
        when {
            path == Endpoints.PLAN_SERVICES -> ok(planServices(parameters["doctorIds"]).toString())
            path == Endpoints.CATALOG_ITEMS && method == HttpMethod.Get -> ok(apiJson.encodeToString(catalog.toList()))
            path == Endpoints.CATALOG_ITEMS && method == HttpMethod.Post -> {
                val sent = runCatching { apiJson.decodeFromString<NewCatalogItem>(body) }.getOrNull()
                val error = sent?.let { invalid(it.title, it.appliesToAllDoctors, it.assignedDoctorProfileIds) }
                when {
                    sent == null -> HttpStatusCode.BadRequest to """{"status":400,"code":"VALIDATION"}"""
                    error != null -> error
                    else -> {
                        val item =
                            CatalogItem(
                                (catalog.maxOfOrNull { it.id } ?: 0) + 1, 1, sent.code?.trim()?.ifEmpty { null },
                                sent.title.trim(), sent.defaultPriceMinor, sent.currency, sent.active, 0,
                                sent.appliesToAllDoctors,
                                if (sent.appliesToAllDoctors) emptyList() else sent.assignedDoctorProfileIds,
                            )
                        catalog += item
                        ok(apiJson.encodeToString(item))
                    }
                }
            }
            path.matches(Regex("${Endpoints.CATALOG_ITEMS}/\\d+")) && method == HttpMethod.Patch -> {
                val i = catalog.indexOfFirst { it.id == path.substringAfterLast('/').toLong() }
                val p = runCatching { apiJson.decodeFromString<CatalogItemPatch>(body) }.getOrNull()
                if (i < 0 || p == null) {
                    HttpStatusCode.NotFound to """{"status":404,"code":"NOT_FOUND"}"""
                } else {
                    val old = catalog[i]
                    val all =
                        if (p.appliesToAllDoctors == true) {
                            true
                        } else if (p.assignedDoctorProfileIds != null) {
                            false
                        } else {
                            old.appliesToAllDoctors
                        }
                    val ids = if (all) emptyList() else p.assignedDoctorProfileIds ?: old.assignedDoctorProfileIds
                    invalid(p.title ?: old.title, all, ids) ?: run {
                        catalog[i] =
                            old.copy(
                                // "" clears the code (the backend's rule); no field keeps it.
                                code = if (p.code != null) p.code.trim().ifEmpty { null } else old.code,
                                title = p.title?.trim() ?: old.title,
                                defaultPriceMinor = p.defaultPriceMinor ?: old.defaultPriceMinor,
                                currency = p.currency ?: old.currency,
                                active = p.active ?: old.active,
                                appliesToAllDoctors = all,
                                assignedDoctorProfileIds = ids,
                            )
                        ok(apiJson.encodeToString(catalog[i]))
                    }
                }
            }
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

    /** The backend's checks: a title, and at least one of the clinic's doctors unless it's for all of them. */
    private fun invalid(
        title: String,
        all: Boolean,
        ids: List<Long>,
    ): Pair<HttpStatusCode, String>? =
        if (title.isBlank() || (!all && (ids.isEmpty() || ids.any { it !in DOCTORS }))) {
            HttpStatusCode.BadRequest to """{"status":400,"code":"VALIDATION"}"""
        } else {
            null
        }

    /** `GET /treatment-plans/plan-services`: the catalogue (inactive too) and a doctor's own service, by doctor. */
    private fun planServices(doctorIds: String?): JsonArray {
        val wanted = doctorIds?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }
        val rows =
            catalog.map { c ->
                val offered = if (c.appliesToAllDoctors) DOCTORS.keys.toList() else c.assignedDoctorProfileIds
                offered to
                    buildJsonObject {
                        put("key", "catalog:${c.id}")
                        put("kind", "CLINIC_CATALOG")
                        put("catalogItemId", c.id)
                        put("title", c.title)
                        put("code", c.code?.let(::JsonPrimitive) ?: JsonNull)
                        put("defaultPriceMinor", c.defaultPriceMinor)
                        put("currency", c.currency)
                        put("active", c.active)
                        put("offeredByDoctorIds", JsonArray(offered.map(::JsonPrimitive)))
                        put("offeredByDoctorNames", JsonArray(offered.map { JsonPrimitive(DOCTORS[it]) }))
                        groups[c.id]?.let { (name, order) ->
                            put("groupName", name)
                            put("groupSortOrder", order)
                        }
                    }
            } +
                (
                    listOf(21L) to
                        buildJsonObject {
                            put("key", "doctor:21:service:3")
                            put("kind", "DOCTOR_SERVICE")
                            put("doctorServiceId", 3)
                            put("title", "Cleaning")
                            put("defaultPriceMinor", 40_000_000)
                            put("currency", "UZS")
                            put("active", true)
                            put("offeredByDoctorIds", JsonArray(listOf(JsonPrimitive(21))))
                            put("offeredByDoctorNames", JsonArray(listOf(JsonPrimitive(DOCTORS[21]))))
                        }
                )
        return JsonArray(
            rows.filter {
                    (offered, _) ->
                wanted == null || offered.any { it in wanted }
            }.map { it.second },
        )
    }

    private fun members(): List<JsonObject> =
        listOf(
            member(21, 101, DOCTORS.getValue(21), "OWNER"),
            member(22, 102, DOCTORS.getValue(22), "DOCTOR"),
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

    private companion object {
        // The clinic's doctors by profile id (the members below).
        val DOCTORS = mapOf(21L to "Aziza Karimova", 22L to "Bobur Aliyev")
    }
}

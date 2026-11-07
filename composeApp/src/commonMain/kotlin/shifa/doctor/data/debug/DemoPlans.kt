package shifa.doctor.data.debug

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.apiJson

/**
 * The plans the wizard creates in demo mode (TP-04), in memory: the plan (DRAFT), its lines, desk payments, an
 * installment schedule and its status, read back by the list and the plan detail. Answers only these plans' paths
 * (and the create); the demo's own plan 501 stays in [DemoBackend].
 */
internal class DemoPlans(
    private val patientName: (Long) -> String?,
) {
    private class Plan(
        val id: Long,
        val request: JsonObject,
        var status: String = "DRAFT",
        var lines: List<JsonObject> = emptyList(),
        var paid: Long = 0,
        var schedule: JsonObject? = null,
    )

    private val plans = mutableListOf<Plan>()

    fun handle(
        method: String,
        path: String,
        body: String,
    ): JsonElement? {
        val json = runCatching { apiJson.parseToJsonElement(body) }.getOrNull()
        if (method == "POST" && path == Endpoints.TREATMENT_PLANS) {
            val o = json?.jsonObject ?: return null
            val plan = Plan(NEXT_ID + plans.size, o)
            plans += plan
            return summary(plan)
        }
        if (method == "POST" && (
                path ==
                    Endpoints.clinicPayments(
                        1,
                    ) || path == "clinics/1/finance/installment-plans"
            )
        ) {
            val o = json?.jsonObject ?: return null
            val plan = find(o["treatmentPlanId"]?.jsonPrimitive?.longOrNull) ?: return null
            if (path == Endpoints.clinicPayments(1)) {
                plan.paid += o["amountMinor"]?.jsonPrimitive?.longOrNull ?: 0
            } else {
                plan.schedule = o
            }
            return buildJsonObject { put("id", plan.id) }
        }
        val id = Regex("treatment-plans/(\\d+)(/.*)?").matchEntire(path)?.groupValues ?: return null
        val plan = find(id[1].toLongOrNull()) ?: return null
        return when {
            method == "POST" && id[2] == "/lines" -> {
                plan.lines = json?.jsonArray?.map { it.jsonObject }.orEmpty()
                summary(plan)
            }
            method == "PATCH" && id[2] == "/status" -> {
                json?.jsonObject?.get("status")?.jsonPrimitive?.content?.let { plan.status = it }
                summary(plan)
            }
            method == "GET" && id[2] == "/detail" -> detail(plan)
            method == "GET" && id[2] == "/visits" -> JsonArray(emptyList())
            else -> null
        }
    }

    /** The created plans the list asks for (clinic 1; the patient and status filters). */
    fun list(query: Map<String, String?>): List<JsonObject> =
        plans.filter { p ->
            query["clinicId"] == "1" && query["planKind"] != "VISIT" &&
                query["patientId"].let { it == null || it == "${patientOf(p)}" } &&
                query["status"].let { it == null || it == p.status } &&
                query["q"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }.let { q ->
                    q == null ||
                        "${patientName(
                            patientOf(p),
                        )} ${p.request["title"]?.jsonPrimitive?.content}".lowercase().contains(q)
                }
        }.map(::summary)

    private fun find(id: Long?) = plans.firstOrNull { it.id == id }

    private fun patientOf(p: Plan) = p.request["patientId"]?.jsonPrimitive?.longOrNull ?: 0

    private fun total(p: Plan) = p.lines.sumOf(::lineTotal)

    private fun lineTotal(l: JsonObject): Long =
        (l["unitPriceMinor"]?.jsonPrimitive?.longOrNull ?: 0) - (l["discountMinor"]?.jsonPrimitive?.longOrNull ?: 0)

    // The plan's currency is its lines' (the wizard sends one).
    private fun currency(p: Plan) = p.lines.firstOrNull()?.get("currency")?.jsonPrimitive?.content ?: "UZS"

    private fun summary(p: Plan): JsonObject {
        val total = total(p)
        return buildJsonObject {
            put("id", p.id)
            put("clinicId", 1)
            put("patientId", patientOf(p))
            put("patientName", patientName(patientOf(p)))
            put("title", p.request["title"]?.jsonPrimitive?.content)
            p.request["diagnosis"]?.jsonPrimitive?.content?.let { put("diagnosis", it) }
            put("status", p.status)
            put("planKind", "COMPREHENSIVE")
            put("totalMinor", total)
            put("paidMinor", p.paid)
            put("owedMinor", (total - p.paid).coerceAtLeast(0))
            put("currency", currency(p))
            put("visitCount", 0)
            put("linesCompletedCount", 0)
            put("linesTotalCount", p.lines.size)
        }
    }

    private fun detail(p: Plan): JsonObject =
        buildJsonObject {
            put("summary", summary(p))
            put(
                "lines",
                JsonArray(
                    p.lines.mapIndexed { i, l ->
                        JsonObject(
                            l +
                                mapOf(
                                    "id" to kotlinx.serialization.json.JsonPrimitive(p.id * 10 + i),
                                    "lineTotalMinor" to
                                        kotlinx.serialization.json.JsonPrimitive(lineTotal(l)),
                                ),
                        )
                    },
                ),
            )
            put(
                "installmentPlans",
                JsonArray(
                    listOfNotNull(
                        p.schedule?.let { s ->
                            val rows = s["scheduleItems"]?.jsonArray.orEmpty()
                            buildJsonObject {
                                put("installmentPlanId", p.id)
                                put("status", "ACTIVE")
                                put("totalAmountMinor", s["totalAmountMinor"]?.jsonPrimitive?.longOrNull)
                                put("currency", currency(p))
                                put("numInstallments", rows.size)
                                put(
                                    "scheduleRows",
                                    JsonArray(
                                        rows.mapIndexed { i, r ->
                                            buildJsonObject {
                                                put("installmentItemId", p.id * 10 + i)
                                                put("sequenceNumber", i + 1)
                                                put(
                                                    "amountMinor",
                                                    r.jsonObject["amountMinor"]?.jsonPrimitive?.longOrNull,
                                                )
                                                put("currency", currency(p))
                                                put("dueDate", r.jsonObject["dueDate"]?.jsonPrimitive?.content)
                                                put("status", "PENDING")
                                            }
                                        },
                                    ),
                                )
                            }
                        },
                    ),
                ),
            )
        }

    private companion object {
        const val NEXT_ID = 601L
    }
}

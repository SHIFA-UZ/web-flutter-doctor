package shifa.doctor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.rows

/** A plan's status (09 §4.5); [Unknown] for a value this app doesn't know yet. */
enum class PlanStatus {
    Draft,
    Active,
    InProgress,
    OnHold,
    Completed,
    Cancelled,
    Unknown,
    ;

    companion object {
        fun of(raw: String?): PlanStatus = byWire(raw, entries, Unknown)
    }
}

/** A line's status (09 §4.5). */
enum class LineStatus {
    Planned,
    Scheduled,
    InProgress,
    Completed,
    Cancelled,
    Unknown,
    ;

    companion object {
        fun of(raw: String?): LineStatus = byWire(raw, entries, Unknown)
    }
}

/** `COMPREHENSIVE` plans are the ones doctors make; `VISIT` plans are the backend's per-visit ones. */
enum class PlanKind {
    Comprehensive,
    Visit,
    Unknown,
    ;

    companion object {
        fun of(raw: String?): PlanKind = byWire(raw, entries, Unknown)
    }
}

/** `IN_PROGRESS` → [LineStatus.InProgress]: the backend's names in upper snake case. */
private fun <E : Enum<E>> byWire(
    raw: String?,
    entries: List<E>,
    unknown: E,
): E {
    val key = raw?.trim()?.replace("_", "")?.lowercase() ?: return unknown
    return entries.firstOrNull { it.name.lowercase() == key } ?: unknown
}

@Serializable
data class DoctorRef(
    val id: Long,
    val name: String = "",
)

/** `TreatmentPlanSummaryDto` (dev OpenAPI 2026-11-05). Money in minor units of [currency]. */
@Serializable
data class TreatmentPlanSummary(
    val id: Long,
    val clinicId: Long = 0,
    val patientId: Long? = null,
    val patientName: String? = null,
    val attendingDoctorId: Long? = null,
    val attendingDoctorName: String? = null,
    // Read row by row (one odd doctor is skipped, not the plan): see [attendingDoctors].
    @SerialName("attendingDoctors") val attendingDoctorsJson: List<JsonElement> = emptyList(),
    val title: String? = null,
    val diagnosis: String? = null,
    @SerialName("status") val statusRaw: String? = null,
    val notes: String? = null,
    @SerialName("planKind") val kindRaw: String? = null,
    val symptoms: List<String> = emptyList(),
    val totalMinor: Long = 0,
    val paidMinor: Long = 0,
    val owedMinor: Long = 0,
    val currency: String = "UZS",
    val planPaymentStatus: String? = null,
    val visitCount: Int = 0,
    val linesCompletedCount: Int = 0,
    val linesTotalCount: Int = 0,
) {
    val status: PlanStatus get() = PlanStatus.of(statusRaw)
    val kind: PlanKind get() = PlanKind.of(kindRaw)
    val attendingDoctors: List<DoctorRef> get() = attendingDoctorsJson.rows(DoctorRef.serializer(), "plan doctors")
}

/**
 * `LineDetailDto`. [specialtyMetadata] is a JSON string (`{"fdi":"16","dentition":"permanent","source":"PLAN_WIZARD"}`);
 * [tooth] reads its FDI key, null for a line not tied to a tooth or unreadable metadata.
 */
@Serializable
data class PlanLine(
    val id: Long,
    val catalogItemId: Long? = null,
    val title: String = "",
    val quantity: Int = 1,
    val unitPriceMinor: Long = 0,
    val discountMinor: Long = 0,
    val currency: String = "UZS",
    val sortOrder: Int = 0,
    @SerialName("status") val statusRaw: String? = null,
    val lineTotalMinor: Long = 0,
    val specialtyMetadata: String? = null,
    val assignedDoctorId: Long? = null,
    val notes: String? = null,
) {
    val status: LineStatus get() = LineStatus.of(statusRaw)

    val tooth: String?
        get() =
            specialtyMetadata?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    ((apiJson.parseToJsonElement(raw) as? JsonObject)?.get("fdi") as? JsonPrimitive)?.contentOrNull
                }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            }
}

/**
 * `TreatmentPlanDetailDto`: the summary, the lines (one unreadable line is skipped, not the plan) and the dental plan
 * record as a JSON string.
 */
data class TreatmentPlanDetail(
    val summary: TreatmentPlanSummary,
    val lines: List<PlanLine> = emptyList(),
    val dentalPlanDocumentation: String? = null,
) {
    companion object {
        /** Read leniently; an older backend sent the summary's fields flat beside `lines` (09 §7: "tolerates flat"). */
        fun read(json: JsonElement): TreatmentPlanDetail? {
            val map = json as? JsonObject ?: return null
            val summaryJson = map["summary"] as? JsonObject ?: map
            val summary =
                runCatching { apiJson.decodeFromJsonElement(TreatmentPlanSummary.serializer(), summaryJson) }
                    .getOrNull() ?: return null
            val lines = (map["lines"] as? JsonArray).orEmpty().rows(PlanLine.serializer(), "plan lines")
            val dental = (map["dentalPlanDocumentation"] as? JsonPrimitive)?.contentOrNull
            return TreatmentPlanDetail(summary, lines, dental)
        }
    }
}

/** `FulfillmentCandidateDto`: an open plan line this visit may complete; [fdi] null for a general one. */
@Serializable
data class FulfillmentCandidate(
    val lineId: Long,
    val title: String = "",
    val fdi: String? = null,
    val dentition: String? = null,
    val unitPriceMinor: Long = 0,
    val quantity: Int = 1,
    val discountMinor: Long = 0,
    val currency: String = "UZS",
    val lineTotalMinor: Long = 0,
    @SerialName("status") val statusRaw: String? = null,
    val toothMatch: Boolean = false,
) {
    val status: LineStatus get() = LineStatus.of(statusRaw)
}

/** `POST /treatment-plans/{id}/appointments/{apptId}/fulfill` body. */
@Serializable
data class FulfillRequest(
    val lineIds: List<Long>,
)

/** The dentition the plan's dental record uses (permanent when it has none). */
val TreatmentPlanDetail.dentition: shifa.doctor.data.forms.Dentition
    get() =
        DentalRecord.read(dentalPlanDocumentation?.let { runCatching { apiJson.parseToJsonElement(it) }.getOrNull() })
            ?.dentitionKind ?: shifa.doctor.data.forms.Dentition.Permanent

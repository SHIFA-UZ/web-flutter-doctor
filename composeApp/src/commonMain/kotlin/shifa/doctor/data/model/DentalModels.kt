package shifa.doctor.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.Dentition

/**
 * One service on a tooth (or on the visit, [DentalChartCodec.GENERAL_KEY]) in the dental record (05 §4.1): the doctor's
 * service ([serviceId]), or a clinic catalogue item in a plan ([serviceKey], [catalogItemId]), its title and price.
 */
@Serializable
data class ToothServiceLine(
    val serviceId: Long? = null,
    val serviceKey: String? = null,
    val catalogItemId: Long? = null,
    val title: String = "",
    val amountMinor: Long = 0,
    val currency: String = "UZS",
)

/**
 * The dental record of a visit (`/appointments/{id}/dental-documentation`, payload version 2, 05 §4.1): the teeth's
 * service lines by FDI key (and `__general__`), the dentition, a discount in percent and the clinical notes. Stored by
 * the backend as given.
 */
@Serializable
data class DentalRecord(
    val version: Int = VERSION,
    val dentition: String = "permanent",
    val teeth: Map<String, List<ToothServiceLine>> = emptyMap(),
    val discountPercent: Double = 0.0,
    val notes: String = "",
) {
    val dentitionKind: Dentition get() =
        if (dentition.equals(
                "primary",
                ignoreCase = true,
            )
        ) {
            Dentition.Primary
        } else {
            Dentition.Permanent
        }

    /** How many services each tooth has (the chart's badges). */
    val counts: Map<String, Int> get() = teeth.mapValues { it.value.size }.filterValues { it > 0 }

    /**
     * The priced lines' sum per currency (Flutter summed only the first currency's lines and dropped the others
     * silently, 05 §10): UZS first, then the others by code.
     */
    val subtotals: Map<String, Long>
        get() =
            teeth.values.flatten().filter { it.amountMinor > 0 }
                .groupBy(::currencyOf)
                .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }
                .toList()
                .sortedBy { (currency, _) -> if (currency == "UZS") "" else currency }
                .toMap()

    /**
     * Each currency's total after the discount: the subtotal less its discount, the discount a half rounded up (Dart's
     * `round()`); the plan lines spread exactly this discount, so the total shown is what the lines charge.
     */
    val totals: Map<String, Long>
        get() = subtotals.mapValues { (_, minor) -> minor - discountOf(minor, discountPercent) }

    val lineCount: Int get() = teeth.values.sumOf { it.size }

    fun lines(tooth: String): List<ToothServiceLine> = teeth[tooth].orEmpty()

    /** The record with [tooth]'s lines replaced; a tooth left without lines is dropped. */
    fun withLines(
        tooth: String,
        lines: List<ToothServiceLine>,
    ): DentalRecord = copy(teeth = if (lines.isEmpty()) teeth - tooth else teeth + (tooth to lines))

    /** The body for `PUT …/dental-documentation`: version 2, empty teeth left out (Flutter's payload). */
    fun payload(): DentalRecord =
        copy(
            version = VERSION,
            teeth =
                teeth.filterValues {
                    it.isNotEmpty()
                },
            discountPercent = discountPercent.coerceIn(0.0, 100.0),
            notes = notes.trim(),
        )

    companion object {
        const val VERSION = 2

        /**
         * A saved record as the backend gives it back (any version): teeth keys as written (legacy "UR1" becomes FDI),
         * a bad line skipped, a discount given as text read as a number; an empty or unreadable body (teeth that aren't
         * lists by tooth) is null.
         */
        fun read(json: JsonElement?): DentalRecord? {
            val map = json as? JsonObject ?: return null
            if (map.isEmpty()) return null
            // Teeth of another shape can't be read: null, so nothing is saved over them (a bad line alone is skipped).
            val teethJson = map["teeth"]
            val badTeeth =
                teethJson != null && teethJson !is JsonNull &&
                    (teethJson !is JsonObject || teethJson.values.any { it !is JsonArray && it !is JsonNull })
            if (badTeeth) return null
            val dentition = (map["dentition"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() ?: "permanent"
            val kind = if (dentition == "primary") Dentition.Primary else Dentition.Permanent
            val teeth =
                (map["teeth"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
                    val lines =
                        (value as? JsonArray).orEmpty().mapNotNull {
                                line ->
                            runCatching {
                                apiJson.decodeFromJsonElement(
                                    ToothServiceLine.serializer(),
                                    line,
                                )
                            }.getOrNull()
                        }
                    DentalChartCodec.normalizeToothKey(key, kind) to lines
                    // A legacy key and its FDI twin hold the same tooth: the later one wins (Flutter's), not both.
                }.toMap()
            val discount =
                (map["discountPercent"] as? JsonPrimitive)?.let {
                    it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull()
                } ?: 0.0
            return DentalRecord(
                version = VERSION,
                dentition = if (kind == Dentition.Primary) "primary" else "permanent",
                teeth = teeth,
                discountPercent = discount,
                notes = (map["notes"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
    }
}

/** A percentage as the doctor would type it: "10", "12.5". */
fun percentText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** A typed discount ("12,5") as a number; blank or unreadable is 0 (Flutter's `_discountValue`, before clamping). */
fun parsePercent(text: String): Double = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0

/**
 * A catalogue service as a dental line (Flutter's `_loadServices`): the first positive price, else the first one,
 * a free consultation at 0; no price at all is 0 UZS.
 */
fun DoctorService.toothLine(): ToothServiceLine {
    val price = prices.firstOrNull { it.amountMinor > 0 && it.currency.isNotBlank() } ?: prices.firstOrNull()
    return ToothServiceLine(
        serviceId = id,
        title = title,
        amountMinor = if (isFreeConsultation) 0 else price?.amountMinor ?: 0,
        currency = price?.currency?.uppercase()?.ifBlank { null } ?: "UZS",
    )
}

/** A service the dental picker offers (the doctor's for a visit, the clinic's plan services for a plan). */
data class CatalogEntry(
    val key: String,
    val title: String,
    val groupName: String?,
    val line: ToothServiceLine,
    // The clinic's code for a catalogue item (search finds it too).
    val code: String? = null,
)

/** The doctor's services for the visit picker: active ones with a title, by group (its sort order, then its name). */
fun dentalCatalog(services: List<DoctorService>): List<CatalogEntry> =
    services.filter { it.isActive && it.title.isNotBlank() }
        .sortedWith(compareBy({ it.groupSortOrder ?: Int.MAX_VALUE }, { it.groupName.orEmpty().lowercase() }))
        .map { CatalogEntry("doctor-service:${it.id}", it.title, it.groupName, it.toothLine()) }

/** The plan services for the plan editor (DENTAL-04): active ones, by group; a line keeps its key and catalogue item. */
fun planCatalog(options: List<PlanServiceOption>): List<CatalogEntry> =
    options.filter { it.active && it.title.isNotBlank() }
        .sortedWith(compareBy({ it.groupSortOrder ?: Int.MAX_VALUE }, { it.groupName.orEmpty().lowercase() }))
        .map {
            CatalogEntry(
                it.key,
                it.title,
                it.groupName,
                ToothServiceLine(
                    serviceKey = it.key,
                    catalogItemId = it.catalogItemId,
                    title = it.title,
                    amountMinor = it.defaultPriceMinor,
                    currency = it.currency.uppercase().ifBlank { "UZS" },
                ),
                it.code,
            )
        }

/** `PlanServiceOptionDto` (`GET /treatment-plans/plan-services`): a clinic catalogue item or a doctor's service. */
@Serializable
data class PlanServiceOption(
    val key: String,
    val kind: String? = null,
    val catalogItemId: Long? = null,
    val doctorServiceId: Long? = null,
    val title: String = "",
    val code: String? = null,
    val defaultPriceMinor: Long = 0,
    val currency: String = "UZS",
    val active: Boolean = true,
    // Who offers it: every practice doctor for an "all doctors" catalogue item, else the assigned ones / the owner.
    val offeredByDoctorIds: List<Long> = emptyList(),
    val offeredByDoctorNames: List<String?> = emptyList(),
    val groupId: Long? = null,
    val groupName: String? = null,
    val groupSortOrder: Int? = null,
) {
    /** A clinic catalogue item (editable in the workspace), not a doctor's own service. */
    val isCatalog: Boolean get() = kind == "CLINIC_CATALOG" && catalogItemId != null
}

/** `CatalogItemDto` (`GET/POST/PATCH /treatment-plans/catalog-items`, dev OpenAPI 2026-11-06): a clinic service. */
@Serializable
data class CatalogItem(
    val id: Long = 0,
    val clinicId: Long? = null,
    val code: String? = null,
    val title: String = "",
    val defaultPriceMinor: Long = 0,
    val currency: String = "UZS",
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val appliesToAllDoctors: Boolean = true,
    val assignedDoctorProfileIds: List<Long> = emptyList(),
)

/** `UpsertCatalogItemRequest`: a new clinic service (no ids when it applies to all doctors). */
@Serializable
data class NewCatalogItem(
    val clinicId: Long,
    val code: String?,
    val title: String,
    val defaultPriceMinor: Long,
    val currency: String,
    val active: Boolean,
    val appliesToAllDoctors: Boolean,
    val assignedDoctorProfileIds: List<Long>,
    val sortOrder: Int = 0,
)

/**
 * `PatchCatalogItemRequest`: only the fields set are sent. `code = ""` clears the code; `appliesToAllDoctors = true`
 * switches to every doctor, else [assignedDoctorProfileIds] sets who offers it (the backend's two branches).
 */
@Serializable
data class CatalogItemPatch(
    val code: String? = null,
    val title: String? = null,
    val defaultPriceMinor: Long? = null,
    val currency: String? = null,
    val active: Boolean? = null,
    val appliesToAllDoctors: Boolean? = null,
    val assignedDoctorProfileIds: List<Long>? = null,
)

/** A treatment-plan line to create (`LineReq` of `POST /treatment-plans/{id}/lines`). */
@Serializable
data class PlanLineRequest(
    val catalogItemId: Long? = null,
    val title: String,
    val quantity: Int = 1,
    val unitPriceMinor: Long,
    val discountMinor: Long,
    val currency: String,
    val sortOrder: Int,
    val status: String = "PLANNED",
    // `{"fdi":"16","dentition":"permanent","source":"PLAN_WIZARD"}` as a string; no `fdi` for a general line, none
    // at all for a wizard line without a tooth (Flutter's list mode).
    val specialtyMetadata: String? = null,
)

/**
 * The plan record as plan lines (Flutter's `buildLineRequests`, 05 §4.4): teeth in chart order, general last, each
 * with its tooth in `specialtyMetadata`; the discount spread over the lines in proportion, the rounding left on each
 * currency's last line. Fixes Flutter's: a line without a title is left out before the spreading (Flutter could
 * drop the remainder with it), each currency gets its own share (Flutter mixed sums), and a line never gets more
 * discount than its price. A line without a catalogue item finds it by its key in [catalog].
 */
fun DentalRecord.planLines(
    catalog: List<PlanServiceOption> = emptyList(),
    source: String = "PLAN_WIZARD",
): List<PlanLineRequest> {
    val kind = dentitionKind
    val order = DentalChartCodec.teeth(kind) + DentalChartCodec.GENERAL_KEY
    val rows =
        (order.filter { it in teeth } + teeth.keys.filter { it !in order }).flatMap { key ->
            teeth[key].orEmpty().filter { it.title.isNotBlank() && it.amountMinor >= 0 }.map { key to it }
        }
    val percent = discountPercent.coerceIn(0.0, 100.0)
    val discounts = LongArray(rows.size)
    rows.indices.groupBy { currencyOf(rows[it].second) }.values.forEach { idx ->
        val subtotal = idx.sumOf { rows[it].second.amountMinor }
        var left = discountOf(subtotal, percent)
        idx.forEachIndexed { n, i ->
            val amount = rows[i].second.amountMinor
            val share = if (n == idx.lastIndex) left else discountOf(amount, percent)
            discounts[i] = share.coerceIn(0, minOf(left, amount))
            left -= discounts[i]
        }
        // What a line couldn't take (its price is less than its share) goes to the lines with room, last first.
        idx.reversed().forEach { i ->
            if (left == 0L) return@forEach
            val more = minOf(left, rows[i].second.amountMinor - discounts[i])
            discounts[i] += more
            left -= more
        }
    }
    val dentitionCode = if (kind == Dentition.Primary) "primary" else "permanent"
    return rows.mapIndexed { i, (key, line) ->
        val meta =
            buildJsonObject {
                if (key != DentalChartCodec.GENERAL_KEY) put("fdi", key)
                put("dentition", dentitionCode)
                put("source", source)
            }
        PlanLineRequest(
            catalogItemId = line.catalogItemId ?: catalog.firstOrNull { it.key == line.serviceKey }?.catalogItemId,
            title = line.title,
            unitPriceMinor = line.amountMinor,
            discountMinor = discounts[i],
            currency = currencyOf(line),
            sortOrder = i,
            specialtyMetadata = meta.toString(),
        )
    }
}

/** [percent] (0–100) of [minor], a half rounded up: the one discount rounding for totals and plan lines. */
private fun discountOf(
    minor: Long,
    percent: Double,
): Long = kotlin.math.floor(minor * percent.coerceIn(0.0, 100.0) / 100 + 0.5).toLong()

/** A line's currency as sent: upper case, UZS when blank (so a blank one is counted with UZS). */
private fun currencyOf(line: ToothServiceLine) = line.currency.uppercase().ifBlank { "UZS" }

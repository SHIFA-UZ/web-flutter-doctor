package shifa.doctor.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
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
                .groupBy { it.currency.uppercase().ifBlank { "UZS" } }
                .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }
                .toList()
                .sortedBy { (currency, _) -> if (currency == "UZS") "" else currency }
                .toMap()

    /** Each currency's total after the discount, a half rounded up (Dart's `round()`, Flutter's totals). */
    val totals: Map<String, Long>
        get() =
            subtotals.mapValues {
                    (_, minor) ->
                kotlin.math.floor(minor * (100 - discountPercent.coerceIn(0.0, 100.0)) / 100.0 + 0.5).toLong()
            }

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
            if (teethJson != null && teethJson !is JsonNull) {
                if (teethJson !is JsonObject || teethJson.values.any { it !is JsonArray && it !is JsonNull }) return null
            }
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

/** The services the dental picker offers: active ones with a title, by group (its sort order, then its name). */
fun dentalCatalog(services: List<DoctorService>): List<DoctorService> =
    services.filter { it.isActive && it.title.isNotBlank() }
        .sortedWith(compareBy({ it.groupSortOrder ?: Int.MAX_VALUE }, { it.groupName.orEmpty().lowercase() }))

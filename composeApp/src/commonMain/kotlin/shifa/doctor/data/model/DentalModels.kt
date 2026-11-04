package shifa.doctor.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
     * silently, 05 §10), in the order they appear.
     */
    val subtotals: Map<String, Long>
        get() =
            teeth.values.flatten().filter { it.amountMinor > 0 }
                .groupBy { it.currency.uppercase().ifBlank { "UZS" } }
                .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }

    /** Each currency's total after the discount, a half rounded up (Dart's `round()`, Flutter's totals). */
    val totals: Map<String, Long>
        get() =
            subtotals.mapValues {
                    (_, minor) ->
                kotlin.math.floor(minor * (100 - discountPercent.coerceIn(0.0, 100.0)) / 100.0 + 0.5).toLong()
            }

    val lineCount: Int get() = teeth.values.sumOf { it.size }

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
         * a bad line skipped, a discount given as text read as a number; an empty or unreadable body is null.
         */
        fun read(json: JsonElement?): DentalRecord? {
            val map = json as? JsonObject ?: return null
            if (map.isEmpty()) return null
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

package shifa.doctor.data.forms

import kotlinx.datetime.LocalDate

/**
 * The 025-2 chart's codes (06 §3.4; Flutter's list, spec FORM-01 Fix): what a tooth or a row cell can hold, stored as
 * written ([code]); "" is nothing recorded. A stored value outside the list stays a string in the chart.
 */
enum class DentalCode(
    val code: String,
) {
    O("O"),
    R("R"),
    C("C"),
    P("P"),
    Pt("Pt"),
    PCyrillic("П"),
    A("A"),
    I("I"),
    II("II"),
    III("III"),
    K("K"),
    ICyrillic("И"),
    ;

    companion object {
        fun of(raw: String?): DentalCode? = raw?.trim()?.let { v -> entries.firstOrNull { it.code == v } }
    }
}

/** Adult or child teeth on a dental chart. */
enum class Dentition { Permanent, Primary }

/** A read-only history row of a 025-2 dental chart: its cells, and the visit's date and doctor. */
data class DentalHistoryRow(
    val index: Int,
    val cells: List<String>,
    val date: String? = null,
    val doctor: String? = null,
)

/**
 * The 025-2 dental chart's key grammar (06 §3.2; ported from Flutter's `dental_chart_codec.dart`), on a flat
 * `Map<String, String>` as the backend stores it:
 * - teeth in ISO 3950 / FDI two-digit keys: "11".."48" permanent, "51".."85" primary; legacy "UR1".."LL8" migrated;
 * - editable rows above and below the jaws: `TOP_{row}_{cell}`, `BOTTOM_{row}_{cell}` (16 cells, 10 for primary);
 * - read-only history rows from earlier forms: `TOP_HIST_{i}_{cell}` with `TOP_HIST_{i}_date` / `_doctor` (and BOTTOM);
 * - meta: `__dentition__` = `permanent` | `primary`; `__general__` = visit services not tied to a tooth.
 */
object DentalChartCodec {
    const val DENTITION_KEY = "__dentition__"
    const val GENERAL_KEY = "__general__"
    const val TOP = "TOP"
    const val BOTTOM = "BOTTOM"

    val upperRight = listOf("18", "17", "16", "15", "14", "13", "12", "11")
    val upperLeft = listOf("21", "22", "23", "24", "25", "26", "27", "28")
    val lowerRight = listOf("48", "47", "46", "45", "44", "43", "42", "41")
    val lowerLeft = listOf("31", "32", "33", "34", "35", "36", "37", "38")
    val primaryUpperRight = listOf("55", "54", "53", "52", "51")
    val primaryUpperLeft = listOf("61", "62", "63", "64", "65")
    val primaryLowerRight = listOf("85", "84", "83", "82", "81")
    val primaryLowerLeft = listOf("71", "72", "73", "74", "75")

    private val fdiTooth = Regex("^[1-8][1-8]$")
    private val legacyTooth = Regex("^(UR|UL|LR|LL)([1-8])$")
    private val editableCell = Regex("^(TOP|BOTTOM)_(\\d+)_(\\d+)$")
    private val historyCell = Regex("^(TOP|BOTTOM)_HIST_(\\d+)_(\\d+)$")
    private val historyMeta = Regex("^(TOP|BOTTOM)_HIST_(\\d+)_(date|doctor)$")

    fun cellsPerRow(d: Dentition) = if (d == Dentition.Primary) 10 else 16

    fun teethPerQuadrant(d: Dentition) = if (d == Dentition.Primary) 5 else 8

    fun dentition(chart: Map<String, String>): Dentition =
        if (chart[DENTITION_KEY]?.trim()?.lowercase() == "primary") Dentition.Primary else Dentition.Permanent

    /** Every tooth in display order: upper right, upper left, lower right, lower left. */
    fun teeth(d: Dentition): List<String> =
        if (d == Dentition.Primary) {
            primaryUpperRight + primaryUpperLeft + primaryLowerRight + primaryLowerLeft
        } else {
            upperRight + upperLeft + lowerRight + lowerLeft
        }

    /** "UR" → 1 (5 for primary), "UL" → 2, "LL" → 3, "LR" → 4; 0 for anything else. */
    fun quadrantDigit(
        quadrant: String,
        d: Dentition = Dentition.Permanent,
    ): Int {
        val base =
            when (quadrant) {
                "UR" -> 1
                "UL" -> 2
                "LL" -> 3
                "LR" -> 4
                else -> return 0
            }
        return if (d == Dentition.Primary) base + 4 else base
    }

    /** UR + 1 → "11" (permanent) or "51" (primary). */
    fun fdiKey(
        quadrant: String,
        tooth: Int,
        d: Dentition = Dentition.Permanent,
    ) = "${quadrantDigit(quadrant, d)}$tooth"

    /** Any stored tooth key ("UR8", "UR 8", "18") as its FDI key; other keys (and `__general__`) unchanged. */
    fun normalizeToothKey(
        key: String,
        d: Dentition = Dentition.Permanent,
    ): String {
        val s = key.replace(" ", "")
        if (s == GENERAL_KEY || fdiTooth.matches(s)) return s
        val m = legacyTooth.find(s) ?: return s
        return fdiKey(m.groupValues[1], m.groupValues[2].toInt(), d)
    }

    /** A tooth's value: its FDI key first, then the legacy one. */
    fun toothValue(
        chart: Map<String, String>,
        quadrant: String,
        tooth: Int,
        d: Dentition = Dentition.Permanent,
    ): String = chart[fdiKey(quadrant, tooth, d)] ?: chart["$quadrant$tooth"].orEmpty()

    /**
     * Legacy tooth keys (UR1…) moved to FDI ("11"…); everything else kept. An FDI key already present wins over its
     * legacy twin whatever the map's order (Flutter's result depended on it).
     */
    fun migrateLegacy(chart: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        chart.forEach { (k, v) -> if (legacyTooth.find(k) == null) out[k] = v }
        chart.forEach { (k, v) ->
            val m = legacyTooth.find(k) ?: return@forEach
            out.getOrPut(fdiKey(m.groupValues[1], m.groupValues[2].toInt())) { v }
        }
        return out
    }

    fun isEditableCell(key: String) = editableCell.matches(key)

    /** The editable rows of [jaw] (`TOP` / `BOTTOM`), in row order, each [cellsPerRow] wide. */
    fun editableRows(
        chart: Map<String, String>,
        jaw: String,
    ): List<List<String>> {
        val cells = cellsPerRow(dentition(chart))
        val rows = mutableMapOf<Int, MutableList<String>>()
        chart.forEach { (k, v) ->
            val m = editableCell.find(k) ?: return@forEach
            if (m.groupValues[1] != jaw) return@forEach
            val row = rows.getOrPut(m.groupValues[2].toInt()) { MutableList(cells) { "" } }
            m.groupValues[3].toInt().takeIf { it < cells }?.let { row[it] = v }
        }
        return rows.entries.sortedBy { it.key }.map { it.value.toList() }
    }

    /** The read-only history rows of [jaw], in order, with their visit's date and doctor. */
    fun historyRows(
        chart: Map<String, String>,
        jaw: String,
    ): List<DentalHistoryRow> {
        val cells = cellsPerRow(dentition(chart))
        val rows = mutableMapOf<Int, MutableList<String>>()
        val dates = mutableMapOf<Int, String>()
        val doctors = mutableMapOf<Int, String>()
        chart.forEach { (k, v) ->
            historyMeta.find(k)?.takeIf { it.groupValues[1] == jaw }?.let { m ->
                val i = m.groupValues[2].toInt()
                if (m.groupValues[3] == "date") dates[i] = v else doctors[i] = v
                return@forEach
            }
            historyCell.find(k)?.takeIf { it.groupValues[1] == jaw }?.let { m ->
                val c = m.groupValues[3].toInt()
                if (c < cells) rows.getOrPut(m.groupValues[2].toInt()) { MutableList(cells) { "" } }[c] = v
            }
        }
        return rows.entries.sortedBy { it.key }.map {
                (i, row) ->
            DentalHistoryRow(i, row.toList(), dates[i], doctors[i])
        }
    }

    /** The next free history row index of [jaw]. */
    fun nextHistoryIndex(
        chart: Map<String, String>,
        jaw: String,
    ): Int =
        chart.keys
            .mapNotNull { k ->
                (
                    historyMeta.find(
                        k,
                    ) ?: historyCell.find(k)
                )?.takeIf { it.groupValues[1] == jaw }?.groupValues?.get(2)?.toInt()
            }.maxOrNull()
            ?.plus(1) ?: 0

    /**
     * A new 025-2 starts from the patient's latest one (any doctor): its teeth and history carry over, and its
     * editable rows become history rows dated [visitDate] by [visitDoctor] (empty rows are dropped).
     */
    fun prefillFromLatest(
        latest: Map<String, String>,
        visitDate: LocalDate,
        visitDoctor: String,
    ): Map<String, String> {
        val migrated = migrateLegacy(latest)
        val cells = cellsPerRow(dentition(migrated))
        val out = LinkedHashMap(migrated.filterKeys { !isEditableCell(it) })
        listOf(TOP, BOTTOM).forEach { jaw ->
            var next = nextHistoryIndex(out, jaw)
            editableRows(migrated, jaw).forEach { row ->
                if (row.all { it.isBlank() }) return@forEach
                (0 until cells).forEach {
                        c ->
                    row[c].trim().takeIf(String::isNotEmpty)?.let { out["${jaw}_HIST_${next}_$c"] = it }
                }
                out["${jaw}_HIST_${next}_date"] = visitDate.toString()
                out["${jaw}_HIST_${next}_doctor"] = visitDoctor
                next++
            }
        }
        return out
    }

    /** A history date "2026-03-12" as "12.03.2026"; anything else as written. */
    fun displayHistoryDate(iso: String?): String {
        if (iso.isNullOrEmpty()) return ""
        val parts = iso.split('-')
        return if (parts.size == 3) "${parts[2].padStart(2, '0')}.${parts[1].padStart(2, '0')}.${parts[0]}" else iso
    }
}

package shifa.doctor.data.forms

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

/**
 * A read-only history row of a 025-2 dental chart: its cells, the visit's date and doctor, and the teeth its cells
 * are for (null: not recorded, rows carried over by Flutter).
 */
data class DentalHistoryRow(
    val index: Int,
    val cells: List<String>,
    val date: String? = null,
    val doctor: String? = null,
    val dentition: Dentition? = null,
)

/**
 * The 025-2 dental chart's key grammar (06 §3.2; ported from Flutter's `dental_chart_codec.dart`), on a flat
 * `Map<String, String>` as the backend stores it:
 * - teeth in ISO 3950 / FDI two-digit keys: "11".."48" permanent, "51".."85" primary; legacy "UR1".."LL8" migrated;
 * - editable rows above and below the jaws: `TOP_{row}_{cell}`, `BOTTOM_{row}_{cell}` (16 cells, 10 for primary);
 * - read-only history rows from earlier forms: `TOP_HIST_{i}_{cell}` with `TOP_HIST_{i}_date` / `_doctor` (and BOTTOM),
 *   and `_dentition` (ours: the teeth its cells are for; Flutter's codec skips the key);
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
    private val historyMeta = Regex("^(TOP|BOTTOM)_HIST_(\\d+)_(date|doctor|dentition)$")

    fun cellsPerRow(d: Dentition) = if (d == Dentition.Primary) 10 else 16

    fun teethPerQuadrant(d: Dentition) = if (d == Dentition.Primary) 5 else 8

    fun dentition(chart: Map<String, String>): Dentition = dentitionOf(chart[DENTITION_KEY])

    private fun dentitionOf(raw: String?) =
        if (raw?.trim()?.lowercase() == "primary") Dentition.Primary else Dentition.Permanent

    private fun code(d: Dentition) = if (d == Dentition.Primary) "primary" else "permanent"

    /** The upper jaw's teeth left to right on the chart (right quadrant, then left). */
    fun upperTeeth(d: Dentition): List<String> =
        if (d == Dentition.Primary) primaryUpperRight + primaryUpperLeft else upperRight + upperLeft

    fun lowerTeeth(d: Dentition): List<String> =
        if (d == Dentition.Primary) primaryLowerRight + primaryLowerLeft else lowerRight + lowerLeft

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

    /** The read-only history rows of [jaw], in order, with their visit's date, doctor and teeth. */
    fun historyRows(
        chart: Map<String, String>,
        jaw: String,
    ): List<DentalHistoryRow> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        val dates = mutableMapOf<Int, String>()
        val doctors = mutableMapOf<Int, String>()
        val teeth = mutableMapOf<Int, Dentition>()
        chart.forEach { (k, v) ->
            historyMeta.find(k)?.takeIf { it.groupValues[1] == jaw }?.let { m ->
                val i = m.groupValues[2].toInt()
                when (m.groupValues[3]) {
                    "date" -> dates[i] = v
                    "doctor" -> doctors[i] = v
                    else -> teeth[i] = dentitionOf(v)
                }
                return@forEach
            }
            historyCell.find(k)?.takeIf { it.groupValues[1] == jaw }?.let { m ->
                rows.getOrPut(m.groupValues[2].toInt()) { mutableMapOf() }[m.groupValues[3].toInt()] = v
            }
        }
        val current = dentition(chart)
        return rows.entries.sortedBy { it.key }.map { (i, sparse) ->
            DentalHistoryRow(
                i,
                List(cellsPerRow(teeth[i] ?: current)) { sparse[it].orEmpty() },
                dates[i],
                doctors[i],
                teeth[i],
            )
        }
    }

    /**
     * The history rows that line up with the chart's teeth: a row recorded for the other dentition is left out (its
     * cells would sit under other teeth); switching back shows it again.
     */
    fun visibleHistoryRows(
        chart: Map<String, String>,
        jaw: String,
    ): List<DentalHistoryRow> =
        dentition(chart).let { d -> historyRows(chart, jaw).filter { it.dentition == null || it.dentition == d } }

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
     * editable rows become history rows dated [visitDate] (ISO day) by [visitDoctor]: that form's (empty rows dropped).
     * Every history row is marked with the teeth it was recorded for (an older untagged one: that form's).
     */
    fun prefillFromLatest(
        latest: Map<String, String>,
        visitDate: String,
        visitDoctor: String,
    ): Map<String, String> {
        val migrated = migrateLegacy(latest)
        val teeth = dentition(migrated)
        val cells = cellsPerRow(teeth)
        val out = LinkedHashMap(migrated.filterKeys { !isEditableCell(it) })
        listOf(TOP, BOTTOM).forEach { jaw ->
            historyRows(migrated, jaw).forEach { h -> out.getOrPut("${jaw}_HIST_${h.index}_dentition") { code(teeth) } }
            var next = nextHistoryIndex(out, jaw)
            editableRows(migrated, jaw).forEach { row ->
                if (row.all { it.isBlank() }) return@forEach
                (0 until cells).forEach {
                        c ->
                    row[c].trim().takeIf(String::isNotEmpty)?.let { out["${jaw}_HIST_${next}_$c"] = it }
                }
                out["${jaw}_HIST_${next}_date"] = visitDate
                out["${jaw}_HIST_${next}_doctor"] = visitDoctor
                out["${jaw}_HIST_${next}_dentition"] = code(teeth)
                next++
            }
        }
        return out
    }

    /** Sets one editable cell (an empty [value] removes the key). */
    fun withCell(
        chart: Map<String, String>,
        jaw: String,
        row: Int,
        cell: Int,
        value: String,
    ): Map<String, String> {
        val key = "${jaw}_${row}_$cell"
        return if (value.isBlank()) chart - key else chart + (key to value)
    }

    /**
     * The editable row indices of [jaw] in use: the rows present, and an empty one kept by [blankRows] (an added row
     * has no keys until a cell is set).
     */
    fun rowIndices(
        chart: Map<String, String>,
        jaw: String,
    ): List<Int> =
        chart.keys
            .mapNotNull { k -> editableCell.find(k)?.takeIf { it.groupValues[1] == jaw }?.groupValues?.get(2)?.toInt() }
            .distinct()
            .sorted()

    /** Removes an editable row and its cells. */
    fun withoutRow(
        chart: Map<String, String>,
        jaw: String,
        row: Int,
    ): Map<String, String> =
        chart.filterKeys {
                k ->
            editableCell.find(k)?.let { it.groupValues[1] == jaw && it.groupValues[2].toInt() == row } != true
        }

    /**
     * Adult or child teeth. The editable rows are cleared (flow 06/10 §7: their cells would land on other teeth); the
     * teeth and history rows stay as stored ([visibleHistoryRows] shows those recorded for these teeth).
     */
    fun withDentition(
        chart: Map<String, String>,
        d: Dentition,
    ): Map<String, String> = chart.filterKeys { !isEditableCell(it) } + (DENTITION_KEY to code(d))

    /** A history date "2026-03-12" as "12.03.2026"; anything else as written. */
    fun displayHistoryDate(iso: String?): String {
        if (iso.isNullOrEmpty()) return ""
        val parts = iso.split('-')
        return if (parts.size == 3) "${parts[2].padStart(2, '0')}.${parts[1].padStart(2, '0')}.${parts[0]}" else iso
    }
}

package shifa.doctor.data.forms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DentalChartCodecTest {
    private val codec = DentalChartCodec

    @Test
    fun `FDI keys for both dentitions, legacy keys normalised, others untouched`() {
        assertThat(codec.fdiKey("UR", 1)).isEqualTo("11")
        assertThat(codec.fdiKey("LL", 8)).isEqualTo("38")
        assertThat(codec.fdiKey("UR", 1, Dentition.Primary)).isEqualTo("51")
        assertThat(codec.fdiKey("LR", 5, Dentition.Primary)).isEqualTo("85")
        assertThat(codec.normalizeToothKey("UR 8")).isEqualTo("18")
        assertThat(codec.normalizeToothKey("UL3", Dentition.Primary)).isEqualTo("63")
        assertThat(codec.normalizeToothKey("47")).isEqualTo("47")
        assertThat(codec.normalizeToothKey("__general__")).isEqualTo("__general__")
        assertThat(codec.normalizeToothKey("TOP_0_1")).isEqualTo("TOP_0_1")
        assertThat(codec.teeth(Dentition.Permanent)).hasSize(32)
        assertThat(codec.teeth(Dentition.Primary)).hasSize(20)
        assertThat(codec.teeth(Dentition.Primary).first()).isEqualTo("55")
    }

    @Test
    fun `the dentition comes from the meta key and sets the row width`() {
        assertThat(codec.dentition(emptyMap())).isEqualTo(Dentition.Permanent)
        assertThat(codec.dentition(mapOf("__dentition__" to " Primary "))).isEqualTo(Dentition.Primary)
        assertThat(codec.cellsPerRow(Dentition.Primary)).isEqualTo(10)
        assertThat(codec.cellsPerRow(Dentition.Permanent)).isEqualTo(16)
    }

    @Test
    fun `legacy teeth migrate, and an FDI key wins over its legacy twin in any order`() {
        val legacyFirst = linkedMapOf("UR1" to "C", "11" to "P", "UL2" to "R", "TOP_0_0" to "x")
        val fdiFirst = linkedMapOf("11" to "P", "UR1" to "C")
        assertThat(codec.migrateLegacy(legacyFirst)).isEqualTo(mapOf("11" to "P", "22" to "R", "TOP_0_0" to "x"))
        assertThat(codec.migrateLegacy(fdiFirst)).isEqualTo(mapOf("11" to "P"))
        assertThat(codec.toothValue(mapOf("UR1" to "C"), "UR", 1)).isEqualTo("C")
        assertThat(codec.toothValue(mapOf("UR1" to "C", "11" to "P"), "UR", 1)).isEqualTo("P")
    }

    @Test
    fun `editable and history rows read sparse cells in order, out-of-range cells dropped`() {
        val chart =
            mapOf(
                "TOP_1_3" to "A",
                "TOP_0_0" to "O",
                "TOP_0_15" to "K",
                "TOP_0_16" to "ignored",
                "BOTTOM_0_2" to "P",
                "TOP_HIST_2_1" to "C",
                "TOP_HIST_2_date" to "2026-03-12",
                "TOP_HIST_2_doctor" to "Dr. A",
                "TOP_HIST_0_0" to "R",
                "BOTTOM_HIST_5_0" to "I",
            )
        val top = codec.editableRows(chart, "TOP")
        assertThat(top).hasSize(2)
        assertThat(top[0][0] to top[0][15]).isEqualTo("O" to "K")
        assertThat(top[1][3]).isEqualTo("A")
        assertThat(codec.editableRows(chart, "BOTTOM").single()[2]).isEqualTo("P")
        val history = codec.historyRows(chart, "TOP")
        assertThat(history.map { it.index }).containsExactly(0, 2).inOrder()
        assertThat(history[1].cells[1]).isEqualTo("C")
        assertThat(history[1].date to history[1].doctor).isEqualTo("2026-03-12" to "Dr. A")
        assertThat(codec.nextHistoryIndex(chart, "TOP")).isEqualTo(3)
        assertThat(codec.nextHistoryIndex(chart, "BOTTOM")).isEqualTo(6)
        assertThat(codec.nextHistoryIndex(emptyMap(), "TOP")).isEqualTo(0)
    }

    @Test
    fun `a new form takes the latest one's teeth and history and turns its rows into dated history`() {
        val latest =
            mapOf(
                "__dentition__" to "permanent",
                "UR6" to "C",
                "TOP_HIST_0_4" to "O",
                "TOP_HIST_0_date" to "2026-01-10",
                "TOP_0_2" to " P ",
                "TOP_1_0" to "  ",
                "BOTTOM_0_7" to "K",
            )
        val out = codec.prefillFromLatest(latest, "2026-11-04", "Dr. Karimov")
        assertThat(out["16"]).isEqualTo("C")
        assertThat(out["__dentition__"]).isEqualTo("permanent")
        assertThat(out.keys.none { codec.isEditableCell(it) }).isTrue()
        // The kept history row stays 0; the latest row becomes 1, the empty one is dropped.
        assertThat(out["TOP_HIST_0_4"]).isEqualTo("O")
        assertThat(out["TOP_HIST_1_2"]).isEqualTo("P")
        assertThat(out["TOP_HIST_1_date"]).isEqualTo("2026-11-04")
        assertThat(out["TOP_HIST_1_doctor"]).isEqualTo("Dr. Karimov")
        assertThat(out.keys.any { it.startsWith("TOP_HIST_2") }).isFalse()
        assertThat(out["BOTTOM_HIST_0_7"]).isEqualTo("K")
        assertThat(codec.displayHistoryDate("2026-03-12")).isEqualTo("12.03.2026")
        assertThat(codec.displayHistoryDate("12/03")).isEqualTo("12/03")
    }

    @Test
    fun `carried history is tagged with its teeth and hidden under the other dentition`() {
        val latest = mapOf("TOP_0_0" to "C", "TOP_HIST_0_1" to "R", "TOP_HIST_0_date" to "2026-01-05")
        val chart = codec.prefillFromLatest(latest, "2026-10-20", "Dr. Rahimova")
        assertThat(chart).containsEntry("TOP_HIST_0_dentition", "permanent")
        assertThat(chart).containsEntry("TOP_HIST_1_dentition", "permanent")
        assertThat(codec.visibleHistoryRows(chart, codec.TOP).map { it.cells.size }).containsExactly(16, 16)
        val child = codec.withDentition(chart, Dentition.Primary)
        assertThat(codec.visibleHistoryRows(child, codec.TOP)).isEmpty()
        // Rows without a tag (written by Flutter) show under either.
        assertThat(
            codec.visibleHistoryRows(child - "TOP_HIST_1_dentition", codec.TOP).map { it.index },
        ).containsExactly(1)
    }
}

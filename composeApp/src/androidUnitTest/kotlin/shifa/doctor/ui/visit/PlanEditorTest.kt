@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.model.DentalRecord
import shifa.doctor.data.model.PlanLine
import shifa.doctor.data.model.PlanServiceOption
import shifa.doctor.data.model.ToothServiceLine
import shifa.doctor.data.model.planLines

class PlanEditorTest {
    private val catalog =
        listOf(
            PlanServiceOption(
                "svc-a",
                "CLINIC_CATALOG",
                catalogItemId = 10,
                title = "Filling",
                defaultPriceMinor = 100_000,
                currency = "UZS",
            ),
            PlanServiceOption(
                "svc-b",
                "CLINIC_CATALOG",
                catalogItemId = 11,
                title = "Crown",
                defaultPriceMinor = 200_000,
                currency = "UZS",
            ),
            PlanServiceOption("svc-c", "CLINIC_CATALOG", catalogItemId = 12, title = "Old", active = false),
        )

    // Flutter's `dental_plan_editor_test.dart` fixture.
    private val fixture =
        DentalRecord.read(
            apiJson.parseToJsonElement(
                """{"version":2,"dentition":"permanent","teeth":{"16":[{"serviceKey":"svc-a","title":"Filling","amountMinor":100000,"currency":"UZS"}],"26":[{"serviceKey":"svc-b","title":"Crown","amountMinor":200000,"currency":"UZS"}]},"discountPercent":10,"notes":"Phase 1"}""",
            ),
        )!!

    @Test
    fun `Flutter's fixture - two lines with their tooth, catalogue item and a discount of 30000 in all`() {
        val lines = fixture.planLines(catalog)
        assertThat(lines).hasSize(2)
        val filling = lines.first { it.title == "Filling" }
        assertThat(filling.catalogItemId).isEqualTo(10L)
        assertThat(filling.discountMinor).isGreaterThan(0L)
        assertThat(
            filling.specialtyMetadata,
        ).isEqualTo("""{"fdi":"16","dentition":"permanent","source":"PLAN_WIZARD"}""")
        val crown = lines.first { it.title == "Crown" }
        assertThat(crown.catalogItemId).isEqualTo(11L)
        assertThat(lines.sumOf { it.discountMinor }).isEqualTo(30_000L)
        assertThat(lines.map { it.sortOrder }).containsExactly(0, 1).inOrder()
        assertThat(lines.all { it.status == "PLANNED" && it.quantity == 1 }).isTrue()
    }

    @Test
    fun `the discount is spread per currency, never over a line, general last, untitled lines left out`() {
        val record =
            DentalRecord(
                teeth =
                    mapOf(
                        "__general__" to
                            listOf(
                                ToothServiceLine(title = "Cleaning", amountMinor = 3, currency = "UZS"),
                            ),
                        "11" to
                            listOf(
                                ToothServiceLine(title = "Veneer", amountMinor = 1_000, currency = "usd"),
                                ToothServiceLine(title = "", amountMinor = 500),
                            ),
                        "21" to listOf(ToothServiceLine(title = "Filling", amountMinor = 3, currency = "UZS")),
                    ),
                discountPercent = 33.0,
            )
        val lines = record.planLines()
        // Chart order: 11 and 21 before the general line.
        assertThat(lines.map { it.title }).containsExactly("Veneer", "Filling", "Cleaning").inOrder()
        assertThat(lines.first { it.title == "Veneer" }.discountMinor).isEqualTo(330L)
        // UZS: 33 % of 6 is 2; the rounding lands on the last UZS line, each within its price.
        assertThat(lines.filter { it.currency == "UZS" }.sumOf { it.discountMinor }).isEqualTo(2L)
        assertThat(lines.all { it.discountMinor <= it.unitPriceMinor }).isTrue()
        assertThat(lines.last().specialtyMetadata).isEqualTo("""{"dentition":"permanent","source":"PLAN_WIZARD"}""")
    }

    @Test
    fun `a line too cheap for its share passes the rest on, blank currency counts as UZS, totals match the lines`() {
        fun record(
            percent: Double,
            vararg lines: ToothServiceLine,
        ) = DentalRecord(teeth = mapOf("11" to lines.toList()), discountPercent = percent)
        val leftover =
            record(
                40.0,
                ToothServiceLine(title = "A", amountMinor = 101),
                ToothServiceLine(title = "B", amountMinor = 101),
                ToothServiceLine(title = "C", amountMinor = 0),
            )
        assertThat(leftover.planLines().sumOf { it.discountMinor }).isEqualTo(81L)
        val blank =
            record(
                50.0,
                ToothServiceLine(title = "A", amountMinor = 1, currency = ""),
                ToothServiceLine(title = "B", amountMinor = 1, currency = "UZS"),
            )
        assertThat(blank.planLines().sumOf { it.discountMinor }).isEqualTo(1L)
        assertThat(blank.planLines().map { it.currency }.toSet()).containsExactly("UZS")
        val half = record(50.0, ToothServiceLine(title = "A", amountMinor = 101))
        val net = half.planLines().sumOf { it.unitPriceMinor - it.discountMinor }
        assertThat(half.totals["UZS"]).isEqualTo(net)
    }

    @Test
    fun `the editor offers the active plan services and keeps what the plan needs`() {
        val classToTest = PlanEditor(null, catalog)
        assertThat(classToTest.state.value.services?.map { it.title }).containsExactly("Filling", "Crown")
        classToTest.add("36", classToTest.state.value.services!![1])
        classToTest.discount("10")
        classToTest.notes("Phase 2")
        assertThat(classToTest.state.value.record.lines("36").single().serviceKey).isEqualTo("svc-b")
        assertThat(classToTest.lines().single().discountMinor).isEqualTo(20_000L)
        assertThat(classToTest.documentation()).isEqualTo(
            """{"version":2,"dentition":"permanent","teeth":{"36":[{"serviceKey":"svc-b","catalogItemId":11,"title":"Crown","amountMinor":200000,"currency":"UZS"}]},"discountPercent":10.0,"notes":"Phase 2"}""",
        )
        // An existing plan's record opens as it was.
        assertThat(PlanEditor(fixture, catalog).state.value.discount).isEqualTo("10")
    }

    @Test
    fun `a plan's teeth - all done, some done, none done, cancelled lines left out`() {
        fun line(
            id: Long,
            fdi: String?,
            status: String,
        ) = PlanLine(id, statusRaw = status, specialtyMetadata = fdi?.let { """{"fdi":"$it"}""" })
        val states =
            planToothStates(
                listOf(
                    line(1, "16", "COMPLETED"),
                    line(2, "16", "PLANNED"),
                    line(3, "26", "COMPLETED"),
                    line(4, "36", "PLANNED"),
                    line(5, "46", "CANCELLED"),
                    line(6, null, "PLANNED"),
                ),
            ) { it.statusRaw == "COMPLETED" }
        assertThat(
            states,
        ).containsExactly("16", ToothPlanState.Partial, "26", ToothPlanState.Done, "36", ToothPlanState.Planned)
    }
}

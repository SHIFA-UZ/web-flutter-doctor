@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.forms.Dentition

class DentalModelsTest {
    private fun read(json: String) = DentalRecord.read(apiJson.parseToJsonElement(json))

    @Test
    fun `the payload is Flutter's version 2, empty teeth left out`() {
        val record =
            DentalRecord(
                teeth =
                    mapOf(
                        "16" to
                            listOf(
                                ToothServiceLine(
                                    serviceId = 12,
                                    title = "Filling",
                                    amountMinor = 100_000,
                                    currency = "UZS",
                                ),
                            ),
                        "17" to emptyList(),
                        "__general__" to
                            listOf(
                                ToothServiceLine(
                                    serviceId = 3,
                                    title = "Consultation",
                                    amountMinor = 50_000,
                                    currency = "UZS",
                                ),
                            ),
                    ),
                discountPercent = 10.0,
                notes = " Anaesthesia: lidocaine ",
            )
        assertThat(apiJson.encodeToString(DentalRecord.serializer(), record.payload())).isEqualTo(
            """{"version":2,"dentition":"permanent","teeth":{"16":[{"serviceId":12,"title":"Filling","amountMinor":100000,"currency":"UZS"}],"__general__":[{"serviceId":3,"title":"Consultation","amountMinor":50000,"currency":"UZS"}]},"discountPercent":10.0,"notes":"Anaesthesia: lidocaine"}""",
        )
        // It reads back as it was saved.
        assertThat(
            read(apiJson.encodeToString(DentalRecord.serializer(), record.payload())),
        ).isEqualTo(record.payload())
    }

    @Test
    fun `a saved record reads leniently, legacy keys as FDI, a bad line skipped, nothing saved as null`() {
        val r =
            read(
                """{"dentition":"PRIMARY","teeth":{"UR1":[{"serviceId":1,"title":"Sealant","amountMinor":20000,"currency":"UZS"},null,{"amountMinor":"many"}],"55":[]},"discountPercent":"5","notes":null}""",
            )!!
        assertThat(r.dentitionKind).isEqualTo(Dentition.Primary)
        assertThat(r.teeth.keys).containsExactly("51", "55")
        assertThat(r.teeth["51"]?.single()?.title).isEqualTo("Sealant")
        assertThat(r.discountPercent).isEqualTo(5.0)
        assertThat(r.notes).isEmpty()
        // A legacy key and its FDI twin are one tooth, not two lists added up; odd metadata is ignored.
        val twins =
            read(
                """{"dentition":{},"teeth":{"UR1":[{"title":"A","amountMinor":1}],"11":[{"title":"B","amountMinor":2}]},"notes":[]}""",
            )!!
        assertThat(twins.teeth.keys).containsExactly("11")
        assertThat(twins.lineCount).isEqualTo(1)
        assertThat(twins.notes).isEmpty()
        assertThat(DentalRecord(discountPercent = 150.0).payload().discountPercent).isEqualTo(100.0)
        assertThat(read("{}")).isNull()
        assertThat(read("[]")).isNull()
    }

    @Test
    fun `totals are per currency, never dropping a second one, discount rounded`() {
        val r =
            DentalRecord(
                teeth =
                    mapOf(
                        "16" to listOf(ToothServiceLine(title = "Filling", amountMinor = 100_005, currency = "UZS")),
                        "21" to
                            listOf(
                                ToothServiceLine(title = "Crown", amountMinor = 25_000, currency = "usd"),
                                ToothServiceLine(title = "Free", amountMinor = 0),
                            ),
                    ),
                discountPercent = 10.0,
            )
        assertThat(r.subtotals).containsExactly("UZS", 100_005L, "USD", 25_000L).inOrder()
        assertThat(r.totals).containsExactly("UZS", 90_005L, "USD", 22_500L).inOrder()
        assertThat(r.lineCount).isEqualTo(3)
        assertThat(r.counts).containsExactly("16", 1, "21", 2)
    }
}

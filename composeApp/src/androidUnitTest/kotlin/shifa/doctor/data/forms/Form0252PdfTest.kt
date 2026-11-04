@file:Suppress("ktlint:standard:max-line-length") // the form's labels

package shifa.doctor.data.forms

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import shifa.doctor.data.model.FormFollowup
import shifa.doctor.data.model.PatientForm
import shifa.doctor.utils.PdfOp
import shifa.doctor.utils.PdfPage
import kotlin.math.ceil

class Form0252PdfTest {
    // A text's height as lines of half-size-wide characters: enough to test the flow without platform fonts.
    private fun fakeMeasure(
        text: String,
        size: Float,
        bold: Boolean,
        width: Float,
    ): Float {
        if (text.isEmpty()) return 0f
        val perLine = (width / (size * 0.5f)).toInt().coerceAtLeast(1)
        val lines = text.split('\n').sumOf { ceil(it.length.coerceAtLeast(1) / perLine.toFloat()).toInt() }
        return lines * size * 1.2f
    }

    private val classToTest = Form0252Pdf(::fakeMeasure) { null }

    private fun texts(pages: List<PdfPage>) =
        pages.flatMap { p -> p.ops.filterIsInstance<PdfOp.Text>().map { it.text } }

    private val form =
        PatientForm(
            id = 5,
            patientId = 14,
            date = "2026-03-12",
            fullName = "Jasur Aliyev",
            age = 41,
            doctorName = "Ulugbek Karimov",
            doctorClinic = "Shifa Dev Clinic",
            formNumber = 12,
            complaints = "Og'riq 26-tishda",
            diagnosis = "Pulpit",
            diagnosisCode = "K04.0",
            diagnosisDisplay = "Pulpitis",
            dentalChart =
                mapOf(
                    "TOP_HIST_0_5" to "C",
                    "TOP_HIST_0_date" to "2026-01-10",
                    "TOP_HIST_0_doctor" to "Dr. A",
                    "TOP_0_10" to "P",
                    "BOTTOM_0_3" to "K",
                ),
            followups = listOf(FormFollowup("2026-03-20", "Davolash yakunlandi", "Karimov")),
        )

    @Test
    fun `the form reads in the official order with the header, the patient block and the ICD line`() {
        val pages = classToTest.pages(form)
        val all = texts(pages)
        assertThat(pages.first().width to pages.first().height).isEqualTo(842f to 595f)
        assertThat(
            all,
        ).containsAtLeast("STOMATOLOGIK BEMORNING TIBBIY KARTASI", "Nr. 12", "Sana: 12.03.2026", "Bemor ID: 14")
        assertThat(all).containsAtLeast("To'liq ism", "Jasur Aliyev", "41 yosh", "Shifokor", "Ulugbek Karimov")
        val order =
            listOf("SHIKOYATI", "OBYEKTIV TEKSHIRUV", "TASHXIS", "BOSHDAN O'TKAZGAN VA YO'LDOSH KASALLIKLAR", "TISH JIPSLANISHI (PRIPKUS)", "KASALLIK RIVOJLANISHI", "OG'IZ BO'SHLIG'I HOLATI", "RENTGEN VA LABORATOR TEKSHIRUV MA'LUMOTLARI", "TISH DIAGRAMMASI", "DAVOLANISH", "DAVOLANISH NATIJASI (EPIKRIZ)", "KO'RSATMALAR", "QAYTA TASHRIFLAR")
        assertThat(order.map { all.indexOf(it) }).isInOrder()
        // The ICD code and its name, then the doctor's own wording.
        assertThat(all).containsAtLeast("TASHXIS", "K04.0 — Pulpitis", "Pulpit").inOrder()
        // Nothing is drawn outside the page.
        pages.forEach { p -> p.ops.filterIsInstance<PdfOp.Text>().forEach { assertThat(it.y).isAtMost(595f - 40f) } }
    }

    @Test
    fun `the dental chart puts history and visit rows around the FDI numbers, with the history's date and doctor`() {
        val all = texts(classToTest.pages(form))
        val chartStart = all.indexOf("TISH DIAGRAMMASI")
        val chart = all.subList(chartStart, all.indexOf("DAVOLANISH"))
        // History row (C), its note, this visit's row (P), the upper numbers, then the lower row (K).
        assertThat(chart).containsAtLeast("C", "10.01.2026 · Dr. A", "P", "18", "11", "21", "28", "K").inOrder()
        val primary = classToTest.pages(form.copy(dentalChart = mapOf("__dentition__" to "primary", "TOP_0_0" to "R")))
        assertThat(texts(primary)).containsAtLeast("55", "51", "61", "65").inOrder()
        // An older single-value chart: one row per jaw over its own numbers.
        val legacy = texts(classToTest.pages(form.copy(dentalChart = mapOf("UR6" to "C", "LL1" to "O"))))
        assertThat(legacy).containsAtLeast("C", "18", "O", "48").inOrder()
    }

    @Test
    fun `a long note flows onto the next pages, split between words, nothing lost`() {
        val long = (1..900).joinToString(" ") { "so'z$it" }
        val pages = classToTest.pages(form.copy(treatment = long))
        assertThat(pages.size).isAtLeast(3)
        val all = texts(pages)
        val start = all.indexOf("DAVOLANISH") + 1
        val parts = all.subList(start, all.indexOf("DAVOLANISH NATIJASI (EPIKRIZ)"))
        assertThat(parts.size).isAtLeast(2)
        assertThat(parts.joinToString(" ")).isEqualTo(long)
    }

    @Test
    fun `the return visits table and the signature appear only when there are some`() {
        val all = texts(classToTest.pages(form))
        assertThat(
            all,
        ).containsAtLeast(
            "Sana",
            "Davolovchi shifokor familiyasi",
            "20.03.2026",
            "Davolash yakunlandi",
            "Karimov",
        ).inOrder()
        val none = classToTest.pages(form.copy(followups = emptyList()))
        assertThat(texts(none)).doesNotContain("QAYTA TASHRIFLAR")
        assertThat(none.flatMap { it.ops }.filterIsInstance<PdfOp.Image>()).isEmpty()
        val signed =
            classToTest.pages(
                form.copy(
                    patientSignatureImageBase64 = "data:image/png;base64,iVBORw0KGgo=",
                    patientSignedAt = "2026-03-13T10:00:00Z",
                ),
            )
        assertThat(signed.flatMap { it.ops }.filterIsInstance<PdfOp.Image>()).hasSize(1)
        assertThat(texts(signed)).containsAtLeast("BEMOR IMZOSI", "Sana: 13.03.2026").inOrder()
    }

    @Test
    fun `a return visit longer than a page splits between words with nothing cut off`() {
        val findings = (1..1500).joinToString(" ") { "topilma$it" }
        val pages = classToTest.pages(form.copy(followups = listOf(FormFollowup("2026-03-20", findings, "Karimov"))))
        val all = texts(pages)
        val start = all.indexOf("Davolovchi shifokor familiyasi") + 1
        // Date, the first part, the doctor; then the rest of the findings on the next pages.
        assertThat(
            all.subList(start, start + 3).let {
                listOf(it[0], it[2])
            },
        ).containsExactly("20.03.2026", "Karimov").inOrder()
        val parts = all.drop(start).filter { it.startsWith("topilma") }
        assertThat(parts.size).isAtLeast(2)
        assertThat(parts.joinToString(" ")).isEqualTo(findings)
        pages.forEach {
                p ->
            p.ops.filterIsInstance<PdfOp.Rect>().forEach { assertThat(it.y + it.height).isAtMost(595f - 40f + 0.01f) }
        }
    }

    @Test
    fun `a dental history row and its caption are never on different pages`() {
        // Enough history rows to fill more than a page: every caption follows its row on the same page.
        val chart =
            (0 until 25).flatMap {
                    i ->
                listOf(
                    "TOP_HIST_${i}_0" to "C",
                    "TOP_HIST_${i}_date" to "2026-01-${(i % 28 + 1).toString().padStart(2, '0')}",
                    "TOP_HIST_${i}_doctor" to "Dr. $i",
                )
            }.toMap()
        val pages =
            classToTest.pages(
                form.copy(dentalChart = chart, complaints = (1..40).joinToString("\n") { "line $it" }),
            )
        var checked = 0
        pages.forEach { page ->
            val texts = page.ops.filterIsInstance<PdfOp.Text>()
            texts.forEachIndexed { i, t ->
                if (t.text.contains(" · Dr. ")) {
                    // The row's cell comes right before its caption, on the same page.
                    assertThat(texts.getOrNull(i - 1)?.text).isEqualTo("C")
                    checked++
                }
            }
        }
        assertThat(checked).isEqualTo(25)
        assertThat(pages.size).isAtLeast(2)
        val all = texts(pages)
        (0 until 25).forEach { i ->
            val caption = all.indexOfFirst { it.endsWith(" · Dr. $i") }
            assertThat(all[caption - 1]).isEqualTo("C")
        }
    }
}

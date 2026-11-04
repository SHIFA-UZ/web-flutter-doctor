@file:Suppress("ktlint:standard:max-line-length") // the official form's labels and the chart's geometry

package shifa.doctor.data.forms

import shifa.doctor.data.model.PatientForm
import shifa.doctor.utils.PdfAlign
import shifa.doctor.utils.PdfOp
import shifa.doctor.utils.PdfPage
import shifa.doctor.utils.measurePdfText
import shifa.doctor.utils.renderPdf
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The 025-2 as a PDF (FORM-03, 06 §3.6; Flutter's `_build0252PortraitContent`): landscape A4, the official form's
 * Uzbek labels whatever the app's language (it is a state medical form), the header, the patient block, every section
 * in the form's order, the dental chart (history rows with their date and doctor, then this visit's rows, around the
 * FDI numbers), the return visits table and the patient's signature. Text flows across pages; a paragraph longer than
 * a page is split between words. The layout is shared; [measure] and [render] are the platform's (tests pass fakes).
 */
class Form0252Pdf(
    private val measure: (text: String, size: Float, bold: Boolean, width: Float) -> Float = ::measurePdfText,
    private val render: (List<PdfPage>) -> ByteArray? = ::renderPdf,
) {
    fun pdf(form: PatientForm): ByteArray? = render(pages(form))

    fun pages(form: PatientForm): List<PdfPage> = Layout(form).run()

    private inner class Layout(
        private val form: PatientForm,
    ) {
        private val pages = mutableListOf<PdfPage>()
        private var ops = mutableListOf<PdfOp>()
        private var y = MARGIN

        fun run(): List<PdfPage> {
            header()
            patientBlock()
            section(Labels.COMPLAINTS, form.complaints)
            section(Labels.OBJECTIVE_EXAM, form.visualCheckup)
            // The ICD code and its name first, then the doctor's own wording (Flutter's order).
            val icd =
                listOfNotNull(form.diagnosisCode?.trim(), form.diagnosisDisplay?.trim())
                    .filter(String::isNotEmpty)
                    .takeIf { it.size == 2 }
                    ?.joinToString(" — ")
            section(Labels.DIAGNOSIS, listOfNotNull(icd, form.diagnosis?.trim()?.ifEmpty { null }).joinToString("\n"))
            section(Labels.PAST_AND_COMORBID, form.otherIllnesses)
            section(Labels.OCCLUSION, form.occlusion)
            section(Labels.DISEASE_DEVELOPMENT, form.moreDetails)
            section(Labels.ORAL_CAVITY, form.oralCavityCondition)
            section(Labels.XRAY_LAB, form.xrayLabData)
            sectionHeader(Labels.DENTAL_CHART)
            dentalChart()
            section(Labels.TREATMENT, form.treatment)
            section(Labels.TREATMENT_RESULT, form.treatmentResult)
            section(Labels.RECOMMENDATIONS, form.recommendations)
            if (form.followups.isNotEmpty()) {
                sectionHeader(Labels.RETURN_VISITS)
                followups()
            }
            signature()
            pages += PdfPage(PAGE_W, PAGE_H, ops)
            return pages
        }

        private val contentW = PAGE_W - 2 * MARGIN
        private val bottom = PAGE_H - MARGIN

        private fun newPage() {
            pages += PdfPage(PAGE_W, PAGE_H, ops)
            ops = mutableListOf()
            y = MARGIN
        }

        /** Room for [height] more points, on a new page if this one is full (never for an empty page). */
        private fun need(height: Float) {
            if (y + height > bottom && y > MARGIN) newPage()
        }

        private fun header() {
            val third = contentW / 3
            val left =
                listOf(
                    PdfOp.Text(MARGIN, y, third - 8, form.doctorClinic.orEmpty(), 11f, bold = true),
                ).let {
                    val h = measure(form.doctorClinic.orEmpty(), 11f, true, third - 8)
                    it + PdfOp.Text(MARGIN, y + h + 2, third - 8, Labels.ORDER, 9f, gray = 0.4f)
                }
            ops += left
            ops += PdfOp.Text(MARGIN + third, y, third, Labels.TITLE, 17f, bold = true, align = PdfAlign.Center)
            val right =
                listOf(
                    "Nr. ${form.formNumber?.toString().orEmpty()}",
                    "${Labels.DATE}: ${day(form.date)}",
                    "${Labels.PATIENT_ID}: ${form.patientId}",
                )
            right.forEachIndexed {
                    i,
                    line,
                ->
                ops += PdfOp.Text(MARGIN + 2 * third, y + i * 13f, third, line, 10f, align = PdfAlign.End)
            }
            val leftH =
                measure(
                    form.doctorClinic.orEmpty(),
                    11f,
                    true,
                    third - 8,
                ) + 2 + measure(Labels.ORDER, 9f, false, third - 8)
            val h = maxOf(leftH, measure(Labels.TITLE, 17f, true, third), right.size * 13f)
            y += h + 8
            ops += PdfOp.Line(MARGIN, y, PAGE_W - MARGIN, y, gray = 0.75f)
            y += 12
        }

        private fun patientBlock() {
            val rows =
                listOfNotNull(
                    Labels.FULL_NAME to form.fullName,
                    Labels.PATIENT_ID to form.patientId.toString(),
                    Labels.DATE to day(form.date),
                    form.age?.let { Labels.AGE to "$it yosh" },
                    form.gender?.let { Labels.GENDER to it },
                    form.address?.let { Labels.ADDRESS to it },
                    form.job?.let { Labels.JOB to it },
                    form.doctorName?.let { Labels.DOCTOR to it },
                )
            val valueW = contentW - 2 * BLOCK_PAD - LABEL_W
            val heights = rows.map { (_, v) -> maxOf(measure(v, 11f, false, valueW), 14f) + ROW_GAP }
            val total = heights.sum() + 2 * BLOCK_PAD - ROW_GAP
            need(total)
            ops += PdfOp.Rect(MARGIN, y, contentW, total, stroke = 0.4f, strokeWidth = 1f)
            var rowY = y + BLOCK_PAD
            rows.forEachIndexed { i, (label, value) ->
                ops += PdfOp.Text(MARGIN + BLOCK_PAD, rowY, LABEL_W, label, 11f, bold = true)
                ops += PdfOp.Text(MARGIN + BLOCK_PAD + LABEL_W, rowY, valueW, value, 11f)
                rowY += heights[i]
            }
            y += total + 2
        }

        private fun sectionHeader(title: String) {
            val h = measure(title, 14f, true, contentW - 16) + 12
            // The header keeps at least one line of its section with it.
            need(14 + h + 16)
            y += 14
            ops += PdfOp.Rect(MARGIN, y, contentW, h, fill = 0.88f)
            ops += PdfOp.Text(MARGIN + 8, y + 6, contentW - 16, title, 14f, bold = true)
            y += h + 4
        }

        private fun section(
            title: String,
            text: String?,
        ) {
            sectionHeader(title)
            text?.trim()?.takeIf(String::isNotEmpty)?.split('\n')?.forEach { paragraph(it) }
        }

        /** A paragraph, split between words across pages when it doesn't fit (06 §3.6: long notes). */
        private fun paragraph(text: String) {
            var rest = text
            while (rest.isNotEmpty()) {
                val h = measure(rest, 11f, false, contentW)
                if (y + h <= bottom) {
                    ops += PdfOp.Text(MARGIN, y, contentW, rest, 11f)
                    y += h + 2
                    return
                }
                val words = rest.split(' ')
                val fit = fitWords(words, 11f, false, contentW, bottom - y)
                if (fit == 0) {
                    if (y == MARGIN) {
                        // One word taller than a page: drawn as it is (it can't be split by words).
                        ops += PdfOp.Text(MARGIN, y, contentW, rest, 11f)
                        y += h + 2
                        return
                    }
                    newPage()
                    continue
                }
                ops += PdfOp.Text(MARGIN, y, contentW, words.take(fit).joinToString(" "), 11f)
                newPage()
                rest = words.drop(fit).joinToString(" ")
            }
        }

        /** How many of [words] fit in [room] points of height at [width] (binary search; 0 when not even one). */
        private fun fitWords(
            words: List<String>,
            size: Float,
            bold: Boolean,
            width: Float,
            room: Float,
        ): Int {
            var fit = 0
            var lo = 1
            var hi = words.size
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                if (measure(words.take(mid).joinToString(" "), size, bold, width) <= room) {
                    fit = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return fit
        }

        private fun dentalChart() {
            val chart = form.dentalChart
            val d = DentalChartCodec.dentition(chart)
            val perQuad = DentalChartCodec.teethPerQuadrant(d)
            val cells = DentalChartCodec.cellsPerRow(d)
            val rowW = cells * BOX + 2 * GAP + DIVIDER
            val x0 = MARGIN + (contentW - rowW) / 2

            fun cellX(i: Int) =
                if (i < perQuad) x0 + i * BOX else x0 + perQuad * BOX + 2 * GAP + DIVIDER + (i - perQuad) * BOX

            fun cellRow(values: List<String>) {
                need(ROW)
                values.take(cells).forEachIndexed { i, v ->
                    ops += PdfOp.Rect(cellX(i), y, BOX, BOX, stroke = 0.4f)
                    if (v.isNotBlank()) ops += PdfOp.Text(cellX(i), y + 7, BOX, v.trim(), 9f, align = PdfAlign.Center)
                }
                ops += PdfOp.Rect(x0 + perQuad * BOX + GAP, y, DIVIDER, BOX, fill = 0.75f)
                y += ROW
            }

            fun note(text: String) {
                if (text.isBlank()) return
                need(NOTE)
                ops += PdfOp.Text(x0, y, rowW, text, 7f, gray = 0.4f)
                y += NOTE
            }

            fun numbers(teeth: List<String>) {
                need(NUMBERS)
                teeth.forEachIndexed {
                        i,
                        n,
                    ->
                    ops += PdfOp.Text(cellX(i), y + 5, BOX, n, 10f, bold = true, align = PdfAlign.Center)
                }
                y += NUMBERS
            }

            val primary = d == Dentition.Primary
            val upper = if (primary) DentalChartCodec.primaryUpperRight + DentalChartCodec.primaryUpperLeft else DentalChartCodec.upperRight + DentalChartCodec.upperLeft
            val lower = if (primary) DentalChartCodec.primaryLowerRight + DentalChartCodec.primaryLowerLeft else DentalChartCodec.lowerRight + DentalChartCodec.lowerLeft
            val jaws = listOf(DentalChartCodec.TOP, DentalChartCodec.BOTTOM)
            val rows =
                chart.keys.any {
                    it.startsWith(
                        "${DentalChartCodec.TOP}_",
                    ) || it.startsWith("${DentalChartCodec.BOTTOM}_")
                }
            // The whole chart on one page when it fits one, so its rows stay with the tooth numbers.
            val total =
                if (rows) {
                    jaws.sumOf { jaw ->
                        DentalChartCodec.historyRows(
                            chart,
                            jaw,
                        ).sumOf { (ROW + if (caption(it).isBlank()) 0f else NOTE).toDouble() } +
                            DentalChartCodec.editableRows(chart, jaw).size * ROW.toDouble()
                    }.toFloat() + NUMBERS
                } else {
                    2 * ROW + 2 * NUMBERS
                }
            if (total + 14 <= bottom - MARGIN) need(total + 14)
            y += 6
            if (rows) {
                // History rows (dated, signed) then this visit's rows, above the upper numbers and below them.
                jaws.forEach { jaw ->
                    if (jaw == DentalChartCodec.BOTTOM) numbers(upper)
                    DentalChartCodec.historyRows(chart, jaw).forEach { h ->
                        val caption = caption(h)
                        // A history row and its date and doctor stay on one page.
                        need(ROW + if (caption.isBlank()) 0f else NOTE)
                        cellRow(h.cells)
                        note(caption)
                    }
                    DentalChartCodec.editableRows(chart, jaw).forEach(::cellRow)
                }
            } else {
                // A chart of single tooth values (older forms): one row per jaw over its numbers.
                fun teeth(
                    right: String,
                    left: String,
                ) = (perQuad downTo 1).map { DentalChartCodec.toothValue(chart, right, it, d) } +
                    (1..perQuad).map { DentalChartCodec.toothValue(chart, left, it, d) }
                cellRow(teeth("UR", "UL"))
                numbers(upper)
                cellRow(teeth("LR", "LL"))
                numbers(lower)
            }
            y += 8
        }

        private fun followups() {
            val dateW = 80f
            val doctorW = 170f
            val columns = listOf(dateW, contentW - dateW - doctorW, doctorW)

            fun height(
                values: List<String>,
                header: Boolean,
            ) = values.zip(columns).maxOf { (v, w) -> measure(v, 9f, header, w - 8) } + 8

            fun draw(
                values: List<String>,
                header: Boolean,
                h: Float,
            ) {
                var x = MARGIN
                values.zip(columns).forEach { (v, w) ->
                    ops += PdfOp.Rect(x, y, w, h, fill = if (header) 0.93f else null, stroke = 0.4f)
                    ops += PdfOp.Text(x + 4, y + 4, w - 8, v, 9f, bold = header)
                    x += w
                }
                y += h
            }

            // A row taller than the room left goes to a fresh page; one taller than a page is split between the
            // findings' words, its date and doctor on the first part (nothing is cut off).
            fun row(
                values: List<String>,
                header: Boolean,
            ) {
                var rest = values
                while (true) {
                    val h = height(rest, header)
                    if (y + h <= bottom) return draw(rest, header, h)
                    if (y > MARGIN && h <= bottom - MARGIN) {
                        newPage()
                        continue
                    }
                    val words = rest[1].split(' ')
                    val fit = fitWords(words, 9f, header, columns[1] - 8, bottom - y - 8)
                    if (fit == 0) {
                        if (y > MARGIN) {
                            newPage()
                            continue
                        }
                        return draw(rest, header, h)
                    }
                    draw(listOf(rest[0], words.take(fit).joinToString(" "), rest[2]), header, bottom - y)
                    newPage()
                    rest = listOf("", words.drop(fit).joinToString(" "), "")
                }
            }
            y += 6
            row(listOf(Labels.DATE, Labels.FINDINGS, Labels.DOCTOR_SURNAME), header = true)
            form.followups.forEach {
                    f ->
                row(listOf(day(f.date), f.clinicalFindings, f.doctorName.orEmpty()), header = false)
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun signature() {
            val raw = form.patientSignatureImageBase64?.trim()?.takeIf(String::isNotEmpty) ?: return
            val bytes =
                runCatching {
                    Base64.decode(raw.substringAfter(",", raw).trim())
                }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
            y += 16
            sectionHeader(Labels.SIGNATURE)
            need(72f + 8 + 20)
            y += 8
            ops += PdfOp.Image(MARGIN, y, contentW, 72f, bytes)
            y += 72f + 8
            form.patientSignedAt?.trim()?.takeIf(String::isNotEmpty)?.let {
                ops += PdfOp.Text(MARGIN, y, contentW, "${Labels.DATE}: ${day(it)}", 10f)
                y += 14
            }
        }
    }

    /** A history row's caption: "10.01.2026 · Dr. A". */
    private fun caption(h: DentalHistoryRow) =
        listOf(
            DentalChartCodec.displayHistoryDate(h.date),
            h.doctor?.trim().orEmpty(),
        ).filter(String::isNotEmpty).joinToString(" · ")

    /** The official 025-2's Uzbek labels (Flutter's `_Form0252PdfUz`), kept in Uzbek in every app language. */
    object Labels {
        const val TITLE = "STOMATOLOGIK BEMORNING TIBBIY KARTASI"
        const val ORDER = "2017-yil 25-dekabrdagi Nr. 777-sonli buyruq bilan tasdiqlangan 025-2 raqamli tibbiy hujjat shakli"
        const val FULL_NAME = "To'liq ism"
        const val PATIENT_ID = "Bemor ID"
        const val DATE = "Sana"
        const val AGE = "Yoshi"
        const val GENDER = "Jinsi"
        const val ADDRESS = "Manzil"
        const val JOB = "Kasbi"
        const val DIAGNOSIS = "TASHXIS"
        const val DOCTOR = "Shifokor"
        const val COMPLAINTS = "SHIKOYATI"
        const val OBJECTIVE_EXAM = "OBYEKTIV TEKSHIRUV"
        const val PAST_AND_COMORBID = "BOSHDAN O'TKAZGAN VA YO'LDOSH KASALLIKLAR"
        const val OCCLUSION = "TISH JIPSLANISHI (PRIPKUS)"
        const val DISEASE_DEVELOPMENT = "KASALLIK RIVOJLANISHI"
        const val ORAL_CAVITY = "OG'IZ BO'SHLIG'I HOLATI"
        const val XRAY_LAB = "RENTGEN VA LABORATOR TEKSHIRUV MA'LUMOTLARI"
        const val DENTAL_CHART = "TISH DIAGRAMMASI"
        const val TREATMENT = "DAVOLANISH"
        const val TREATMENT_RESULT = "DAVOLANISH NATIJASI (EPIKRIZ)"
        const val RECOMMENDATIONS = "KO'RSATMALAR"
        const val RETURN_VISITS = "QAYTA TASHRIFLAR"
        const val SIGNATURE = "BEMOR IMZOSI"
        const val FINDINGS = "Qayta kasallanib murojaat qilgan bemor anamnezi, statusi, tashxisi va davolanish kundaligi"
        const val DOCTOR_SURNAME = "Davolovchi shifokor familiyasi"
    }

    companion object {
        // Landscape A4 in points, 40 pt margins (Flutter's page).
        const val PAGE_W = 842f
        const val PAGE_H = 595f
        const val MARGIN = 40f
        private const val LABEL_W = 200f
        private const val BLOCK_PAD = 12f
        private const val ROW_GAP = 8f
        private const val BOX = 26f
        private const val GAP = 4f
        private const val DIVIDER = 2f
        private const val ROW = BOX + 2
        private const val NOTE = 10f
        private const val NUMBERS = 24f

        /** "2026-03-12" (or a timestamp) as "12.03.2026"; anything else as written. */
        fun day(raw: String): String = DentalChartCodec.displayHistoryDate(raw.take(10))
    }
}

package shifa.doctor.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImagesToPdfTest {
    // The smallest JPEG header this writer reads: SOI, an APP0 segment to skip, then SOF0 (height 400, width 300, 3
    // channels).
    private fun jpeg(
        w: Int,
        h: Int,
        channels: Int = 3,
        marker: Int = 0xC0,
        precision: Int = 8,
        end: Boolean = true,
    ): ByteArray {
        val app0 = listOf(0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00)
        val length = 8 + 3 * channels
        val sof = listOf(0xFF, marker, 0x00, length, precision, h shr 8, h and 0xFF, w shr 8, w and 0xFF, channels)
        val tail = if (end) listOf(0xFF, 0xD9) else emptyList()
        return (
            listOf(0xFF, 0xD8) + app0 + sof +
                List(3 * channels) {
                    0
                } + List(4) { 0 } + tail
        ).map { it.toByte() }.toByteArray()
    }

    @Test
    fun `the frame header gives the size after skipping segments, and only JPEGs it can embed pass`() {
        assertThat(jpegSize(jpeg(300, 400))).isEqualTo(Triple(300, 400, 3))
        assertThat(jpegSize(jpeg(10, 10, channels = 1))).isEqualTo(Triple(10, 10, 1))
        assertThat(jpegSize(jpeg(10, 10, channels = 4))).isNull()
        assertThat(jpegSize("%PDF-1.4".encodeToByteArray())).isNull()
        assertThat(jpegSize(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))).isNull()
        // Progressive is fine; lossless, 12-bit, a cut-off file or a frame header without its components are not.
        assertThat(jpegSize(jpeg(10, 10, marker = 0xC2))).isEqualTo(Triple(10, 10, 3))
        assertThat(jpegSize(jpeg(10, 10, marker = 0xC3))).isNull()
        assertThat(jpegSize(jpeg(10, 10, precision = 12))).isNull()
        assertThat(jpegSize(jpeg(10, 10, end = false))).isNull()
        val bare = listOf(0xFF, 0xD8, 0xFF, 0xC0, 0x00, 0x11, 0x08, 0x00, 0x01, 0x00, 0x01, 0x03, 0xFF, 0xD9)
        assertThat(jpegSize(bare.map { it.toByte() }.toByteArray())).isNull()
    }

    @Test
    fun `each JPEG becomes a page with the image unchanged, others are skipped, none at all is null`() {
        val first = jpeg(300, 400)
        val pdf = imagesToPdf(listOf(first, "not an image".encodeToByteArray(), jpeg(400, 300)))!!
        val text = pdf.decodeToString()
        assertThat(text).startsWith("%PDF-1.4")
        assertThat(text).contains("/Count 2")
        assertThat(Regex("/Type /Page /Parent").findAll(text).count()).isEqualTo(2)
        assertThat(
            text,
        ).contains("/Width 300 /Height 400 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode")
        // The first JPEG's bytes are in the file as they were.
        assertThat(pdf.toList().windowed(first.size).any { it == first.toList() }).isTrue()
        assertThat(imagesToPdf(listOf(byteArrayOf(9)))).isNull()
    }

    @Test
    fun `the cross-reference table points at every object`() {
        val pdf = imagesToPdf(listOf(jpeg(300, 400)))!!
        // One char per byte: the offsets are byte offsets.
        val text = String(pdf, Charsets.ISO_8859_1)
        val xref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertThat(text.substring(xref)).startsWith("xref\n0 6\n")
        val offsets = text.substring(xref).lines().drop(3).take(5).map { it.take(10).toInt() }
        offsets.forEachIndexed { i, at -> assertThat(text.substring(at)).startsWith("${i + 1} 0 obj") }
    }

    @Test
    fun `a tall photo fits the page height inside the margin, centred, aspect kept`() {
        val (left, top, w, h) = pdfImageBox(600, 1600)
        assertThat(top).isWithin(0.01).of(PDF_PAGE_MARGIN.toDouble())
        assertThat(h).isWithin(0.01).of((PDF_PAGE_HEIGHT - 2 * PDF_PAGE_MARGIN).toDouble())
        assertThat(w / h).isWithin(0.001).of(600.0 / 1600)
        assertThat(left + w / 2).isWithin(0.01).of(PDF_PAGE_WIDTH / 2.0)
    }

    @Test
    fun `a wide or phone-shaped photo fits the page width, centred vertically`() {
        listOf(1600 to 900, 1200 to 1600).forEach { (pw, ph) ->
            val (left, top, w, h) = pdfImageBox(pw, ph)
            assertThat(left).isWithin(0.01).of(PDF_PAGE_MARGIN.toDouble())
            assertThat(w).isWithin(0.01).of((PDF_PAGE_WIDTH - 2 * PDF_PAGE_MARGIN).toDouble())
            assertThat(w / h).isWithin(0.001).of(pw.toDouble() / ph)
            assertThat(top + h / 2).isWithin(0.01).of(PDF_PAGE_HEIGHT / 2.0)
        }
    }
}

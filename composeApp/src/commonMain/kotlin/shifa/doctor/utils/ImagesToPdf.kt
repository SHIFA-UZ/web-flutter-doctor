package shifa.doctor.utils

/**
 * A scanned document (06 §4.2): each JPEG on its own A4 page ([PDF_PAGE_WIDTH] × [PDF_PAGE_HEIGHT] points), fitted
 * inside a [PDF_PAGE_MARGIN] margin and centred, aspect kept. The JPEG goes in as it is (`/DCTDecode`), so a page
 * weighs what its photo does: the platform PDF writers re-encode (Android's `PdfDocument` stored a small image as
 * 1.4 MB a page). The pickers already give upright JPEGs (quality 85, at most 1600 px). Anything that isn't a
 * baseline/progressive JPEG with 1 or 3 colour channels is skipped; null when no page is left.
 */
fun imagesToPdf(pages: List<ByteArray>): ByteArray? {
    val images = pages.mapNotNull { bytes -> jpegSize(bytes)?.let { bytes to it } }
    if (images.isEmpty()) return null
    val out = PdfBytes()
    out.text("%PDF-1.4\n%âãÏÓ\n")
    // Objects: 1 catalog, 2 pages, then per page: page, content, image.
    val pageIds = images.indices.map { 3 + it * 3 }
    out.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    out.obj(2, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${images.size} >>")
    images.forEachIndexed { i, (jpeg, size) ->
        val (w, h, channels) = size
        val id = pageIds[i]
        val (left, top, boxW, boxH) = pdfImageBox(w, h)
        // PDF's origin is bottom-left.
        val bottom = PDF_PAGE_HEIGHT - top - boxH
        out.obj(
            id,
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PDF_PAGE_WIDTH $PDF_PAGE_HEIGHT] /Contents ${id + 1} 0 R " +
                "/Resources << /XObject << /Im0 ${id + 2} 0 R >> >> >>",
        )
        val draw = "q ${num(boxW)} 0 0 ${num(boxH)} ${num(left)} ${num(bottom)} cm /Im0 Do Q"
        out.obj(id + 1, "<< /Length ${draw.length} >>\nstream\n$draw\nendstream")
        val space = if (channels == 1) "/DeviceGray" else "/DeviceRGB"
        out.stream(
            id + 2,
            "<< /Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace $space /BitsPerComponent 8 " +
                "/Filter /DCTDecode /Length ${jpeg.size} >>",
            jpeg,
        )
    }
    return out.finish(size = 3 + images.size * 3)
}

/**
 * Width, height and colour channels from a JPEG's frame header; null when it isn't one a PDF's `/DCTDecode` takes:
 * 8-bit baseline, extended or progressive Huffman (SOF0–2), 1 or 3 channels, a complete frame header, and the file
 * ending with its end-of-image marker (a truncated photo is skipped, not turned into a broken page).
 */
internal fun jpegSize(bytes: ByteArray): Triple<Int, Int, Int>? {
    fun u(i: Int) = bytes[i].toInt() and 0xFF
    if (bytes.size < 4 || u(0) != 0xFF || u(1) != 0xD8) return null
    if (u(bytes.size - 2) != 0xFF || u(bytes.size - 1) != 0xD9) return null
    var i = 2
    while (i + 9 < bytes.size) {
        if (u(i) != 0xFF) return null
        val marker = u(i + 1)
        if (marker == 0xFF) {
            i++
            continue
        }
        val length = (u(i + 2) shl 8) or u(i + 3)
        // Any other frame type (lossless, arithmetic, hierarchical) isn't something a PDF reader must decode.
        if (marker in 0xC3..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return null
        if (marker in 0xC0..0xC2) {
            val precision = u(i + 4)
            val h = (u(i + 5) shl 8) or u(i + 6)
            val w = (u(i + 7) shl 8) or u(i + 8)
            val channels = u(i + 9)
            val complete = length == 8 + 3 * channels && i + 2 + length <= bytes.size
            return Triple(w, h, channels).takeIf {
                complete && precision == 8 && w > 0 && h > 0 && (channels == 1 || channels == 3)
            }
        }
        if (length < 2) return null
        i += 2 + length
    }
    return null
}

const val PDF_PAGE_WIDTH = 595
const val PDF_PAGE_HEIGHT = 842
const val PDF_PAGE_MARGIN = 24

/** Where an image of [width] × [height] goes on the page: left, top, width, height in points. */
fun pdfImageBox(
    width: Int,
    height: Int,
): List<Double> {
    val boxW = (PDF_PAGE_WIDTH - 2 * PDF_PAGE_MARGIN).toDouble()
    val boxH = (PDF_PAGE_HEIGHT - 2 * PDF_PAGE_MARGIN).toDouble()
    val scale = minOf(boxW / width, boxH / height)
    val w = width * scale
    val h = height * scale
    return listOf((PDF_PAGE_WIDTH - w) / 2, (PDF_PAGE_HEIGHT - h) / 2, w, h)
}

/** Two decimals, dot separated, whatever the locale. */
private fun num(v: Double): String {
    val cents = kotlin.math.round(v * 100).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/** A PDF being written: objects with their byte offsets, then the cross-reference table. */
private class PdfBytes {
    private val chunks = mutableListOf<ByteArray>()
    private var length = 0
    private val offsets = mutableMapOf<Int, Int>()

    fun text(s: String) = bytes(s.encodeToByteArray())

    private fun bytes(b: ByteArray) {
        chunks += b
        length += b.size
    }

    fun obj(
        id: Int,
        body: String,
    ) {
        offsets[id] = length
        text("$id 0 obj\n$body\nendobj\n")
    }

    fun stream(
        id: Int,
        dict: String,
        data: ByteArray,
    ) {
        offsets[id] = length
        text("$id 0 obj\n$dict\nstream\n")
        bytes(data)
        text("\nendstream\nendobj\n")
    }

    fun finish(size: Int): ByteArray {
        val xref = length
        text("xref\n0 $size\n0000000000 65535 f \n")
        (1 until size).forEach { text(offsets.getValue(it).toString().padStart(10, '0') + " 00000 n \n") }
        text("trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        val all = ByteArray(length)
        var at = 0
        chunks.forEach {
            it.copyInto(all, at)
            at += it.size
        }
        return all
    }
}

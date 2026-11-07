package shifa.doctor.utils

import kotlin.math.ceil

// ponytail: an estimate (half the size per character, 1.2 line height) until WEB-05 draws PDFs on the web.
actual fun measurePdfText(
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): Float {
    val perLine = (width / (size * 0.5f)).toInt().coerceAtLeast(1)
    val lines = text.split('\n').sumOf { ceil(it.length.coerceAtLeast(1) / perLine.toDouble()).toInt() }
    return lines * size * 1.2f
}

actual fun renderPdf(pages: List<PdfPage>): ByteArray? = null

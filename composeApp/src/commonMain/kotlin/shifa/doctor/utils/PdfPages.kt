package shifa.doctor.utils

/**
 * A PDF drawn from simple commands (FORM-03; the 025-2 until BE-01 renders it on the backend). The layout is shared
 * code; each platform only measures and draws text (with the system fonts, so Cyrillic and Uzbek Latin both work),
 * rectangles, lines and images. Coordinates are points from the page's top-left corner. Grey levels: 0 black, 1 white.
 */
sealed interface PdfOp {
    /** Text wrapped inside [width] from ([x], [y]) down; [align] within the width. */
    data class Text(
        val x: Float,
        val y: Float,
        val width: Float,
        val text: String,
        val size: Float,
        val bold: Boolean = false,
        val gray: Float = 0f,
        val align: PdfAlign = PdfAlign.Start,
    ) : PdfOp

    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fill: Float? = null,
        val stroke: Float? = null,
        val strokeWidth: Float = 0.5f,
    ) : PdfOp

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val gray: Float = 0.6f,
        val width: Float = 1f,
    ) : PdfOp

    /** An image (PNG or JPEG bytes) fitted inside the box, aspect kept, centred. */
    class Image(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val bytes: ByteArray,
    ) : PdfOp
}

enum class PdfAlign { Start, Center, End }

class PdfPage(
    val width: Float,
    val height: Float,
    val ops: List<PdfOp>,
)

/** The height [text] takes wrapped inside [width] (the platform's text layout, as [renderPdf] draws it). */
expect fun measurePdfText(
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): Float

/** The pages as one PDF; null when the platform couldn't write it. */
expect fun renderPdf(pages: List<PdfPage>): ByteArray?

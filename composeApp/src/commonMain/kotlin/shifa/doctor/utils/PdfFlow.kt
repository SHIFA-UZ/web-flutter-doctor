package shifa.doctor.utils

/**
 * Text flowing down pages for a [PdfPage] layout (the 025-2, FORM-03; the visit summary, PDF-01): the ops of the
 * current page, the pen's [y], a new page when something doesn't fit, section headers that keep a line of their
 * section, and paragraphs split between words across pages. [measure] is the platform's text layout (tests fake it).
 */
open class PdfFlow(
    val pageW: Float,
    val pageH: Float,
    val margin: Float,
    protected val measure: (text: String, size: Float, bold: Boolean, width: Float) -> Float,
) {
    protected val pages = mutableListOf<PdfPage>()
    protected var ops = mutableListOf<PdfOp>()
    protected var y = margin
    protected val contentW = pageW - 2 * margin
    protected val bottom = pageH - margin

    protected fun newPage() {
        pages += PdfPage(pageW, pageH, ops)
        ops = mutableListOf()
        y = margin
    }

    /** Room for [height] more points, on a new page if this one is full (never for an empty page). */
    protected fun need(height: Float) {
        if (y + height > bottom && y > margin) newPage()
    }

    /** The pages, the current one last. */
    protected fun finish(): List<PdfPage> {
        pages += PdfPage(pageW, pageH, ops)
        return pages
    }

    /** A grey band with [title]; it keeps at least one line of its section with it. */
    protected fun sectionHeader(
        title: String,
        size: Float = 14f,
    ) {
        val h = measure(title, size, true, contentW - 16) + 12
        need(14 + h + 16)
        y += 14
        ops += PdfOp.Rect(margin, y, contentW, h, fill = 0.88f)
        ops += PdfOp.Text(margin + 8, y + 6, contentW - 16, title, size, bold = true)
        y += h + 4
    }

    /** A paragraph, split between words across pages when it doesn't fit (long notes). */
    protected fun paragraph(
        text: String,
        size: Float = 11f,
        bold: Boolean = false,
    ) {
        var rest = text
        while (rest.isNotEmpty()) {
            val h = measure(rest, size, bold, contentW)
            if (y + h <= bottom) {
                ops += PdfOp.Text(margin, y, contentW, rest, size, bold = bold)
                y += h + 2
                return
            }
            val words = rest.split(' ')
            val fit = fitWords(words, size, bold, contentW, bottom - y)
            if (fit == 0) {
                if (y == margin) {
                    // One word taller than a page: drawn as it is (it can't be split by words).
                    ops += PdfOp.Text(margin, y, contentW, rest, size, bold = bold)
                    y += h + 2
                    return
                }
                newPage()
                continue
            }
            ops += PdfOp.Text(margin, y, contentW, words.take(fit).joinToString(" "), size, bold = bold)
            newPage()
            rest = words.drop(fit).joinToString(" ")
        }
    }

    /** How many of [words] fit in [room] points of height at [width] (binary search; 0 when not even one). */
    protected fun fitWords(
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
}

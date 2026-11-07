package shifa.doctor.utils

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.compose.resources.decodeToImageBitmap
import shifa.doctor.data.api.httpLogger
import kotlin.io.encoding.Base64
import kotlin.math.ceil

// ponytail: each page is drawn on a browser canvas (system fonts, so Cyrillic works) and stored as a JPEG, so the text
// in a web PDF can't be selected or searched; embed fonts in a vector PDF if that matters.

private const val PX_PER_PT = 2.0
private const val LINE_HEIGHT = 1.17 // Android's StaticLayout line height for the system sans
private const val JPEG_QUALITY = 0.92

private val measuring: JsAny by lazy { canvasContext(newCanvas(1, 1)) }

actual fun measurePdfText(
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): Float =
    if (text.isEmpty()) {
        0f
    } else {
        ceil(
            wrap(measuring, text, size, bold, width).size * size * LINE_HEIGHT,
        ).toFloat()
    }

actual fun renderPdf(pages: List<PdfPage>): ByteArray? {
    if (pages.isEmpty()) return null
    return runCatching { jpegPagesPdf(pages.map(::pageJpeg)) }
        .onFailure { httpLogger.log("FAILED to write a PDF: ${it::class.simpleName}") }
        .getOrNull()
}

/** Lines of [text] inside [width] points: words greedily, a word wider than a line split by characters. */
private fun wrap(
    ctx: JsAny,
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): List<String> {
    setFont(ctx, font(size, bold))
    val fits = { s: String -> textWidth(ctx, s) <= width }
    val lines = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (fits(candidate)) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) lines += line
            var rest = word
            while (rest.length > 1 && !fits(rest)) {
                val cut = (1 until rest.length).lastOrNull { fits(rest.take(it)) } ?: 1
                lines += rest.take(cut)
                rest = rest.drop(cut)
            }
            line = rest
        }
        lines += line
    }
    return lines
}

private fun font(
    size: Float,
    bold: Boolean,
) = "${if (bold) "bold " else ""}${size}px sans-serif"

private fun gray(level: Float): String = (level.coerceIn(0f, 1f) * 255).toInt().let { "rgb($it,$it,$it)" }

private fun pageJpeg(page: PdfPage): JpegPage {
    val w = ceil(page.width * PX_PER_PT).toInt()
    val h = ceil(page.height * PX_PER_PT).toInt()
    val canvas = newCanvas(w, h)
    val ctx = canvasContext(canvas)
    fillRect(ctx, "rgb(255,255,255)", 0.0, 0.0, w.toDouble(), h.toDouble())
    scale(ctx, PX_PER_PT)
    page.ops.forEach { draw(ctx, it) }
    val data = jpegDataUrl(canvas, JPEG_QUALITY)
    return JpegPage(Base64.decode(data.substringAfter(',')), page.width.toInt(), page.height.toInt(), margin = 0)
}

private fun draw(
    ctx: JsAny,
    op: PdfOp,
) {
    when (op) {
        is PdfOp.Text -> {
            if (op.text.isEmpty()) return
            val lines = wrap(ctx, op.text, op.size, op.bold, op.width)
            setFill(ctx, gray(op.gray))
            lines.forEachIndexed { i, line ->
                val x =
                    when (op.align) {
                        PdfAlign.Start -> op.x.toDouble()
                        PdfAlign.Center -> op.x + (op.width - textWidth(ctx, line)) / 2.0
                        PdfAlign.End -> op.x + op.width - textWidth(ctx, line)
                    }
                fillText(ctx, line, x, op.y + i * op.size * LINE_HEIGHT)
            }
        }
        is PdfOp.Rect -> {
            op.fill?.let {
                fillRect(
                    ctx,
                    gray(it),
                    op.x.toDouble(),
                    op.y.toDouble(),
                    op.width.toDouble(),
                    op.height.toDouble(),
                )
            }
            op.stroke?.let {
                strokeRect(
                    ctx,
                    gray(it),
                    op.strokeWidth.toDouble(),
                    op.x.toDouble(),
                    op.y.toDouble(),
                    op.width.toDouble(),
                    op.height.toDouble(),
                )
            }
        }
        is PdfOp.Line ->
            line(
                ctx,
                gray(op.gray),
                op.width.toDouble(),
                op.x1.toDouble(),
                op.y1.toDouble(),
                op.x2.toDouble(),
                op.y2.toDouble(),
            )
        is PdfOp.Image -> {
            // Decoded synchronously by Skia (the browser only decodes asynchronously), then copied into a canvas.
            val bitmap = runCatching { op.bytes.decodeToImageBitmap() }.getOrNull() ?: return
            val pixels = bitmap.toPixelMap()
            val source = newCanvas(bitmap.width, bitmap.height)
            val image = imageData(source, bitmap.width, bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) setPixel(image, y * bitmap.width + x, pixels[x, y].toArgb())
            }
            putImage(source, image)
            val s = minOf(op.width / bitmap.width, op.height / bitmap.height)
            val w = bitmap.width * s
            val h = bitmap.height * s
            drawCanvas(
                ctx,
                source,
                op.x + (op.width - w) / 2.0,
                op.y + (op.height - h) / 2.0,
                w.toDouble(),
                h.toDouble(),
            )
        }
    }
}

private fun newCanvas(
    width: Int,
    height: Int,
): JsAny =
    js(
        "(function () { var c = document.createElement('canvas'); c.width = width; c.height = height; return c })()",
    )

private fun canvasContext(canvas: JsAny): JsAny =
    js(
        "(function () { var g = canvas.getContext('2d'); g.textBaseline = 'top'; return g })()",
    )

private fun setFont(
    ctx: JsAny,
    font: String,
) {
    js("ctx.font = font")
}

private fun textWidth(
    ctx: JsAny,
    text: String,
): Double = js("ctx.measureText(text).width")

private fun setFill(
    ctx: JsAny,
    color: String,
) {
    js("ctx.fillStyle = color")
}

private fun fillText(
    ctx: JsAny,
    text: String,
    x: Double,
    y: Double,
) {
    js("ctx.fillText(text, x, y)")
}

private fun scale(
    ctx: JsAny,
    factor: Double,
) {
    js("ctx.scale(factor, factor)")
}

private fun fillRect(
    ctx: JsAny,
    color: String,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
) {
    js("ctx.fillStyle = color; ctx.fillRect(x, y, w, h)")
}

private fun strokeRect(
    ctx: JsAny,
    color: String,
    lineWidth: Double,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
) {
    js("ctx.strokeStyle = color; ctx.lineWidth = lineWidth; ctx.strokeRect(x, y, w, h)")
}

private fun line(
    ctx: JsAny,
    color: String,
    lineWidth: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
) {
    js(
        "ctx.strokeStyle = color; ctx.lineWidth = lineWidth;" +
            " ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke()",
    )
}

private fun imageData(
    canvas: JsAny,
    width: Int,
    height: Int,
): JsAny = js("canvas.getContext('2d').createImageData(width, height)")

private fun setPixel(
    image: JsAny,
    index: Int,
    argb: Int,
) {
    js(
        "var d = image.data, i = index * 4; d[i] = (argb >> 16) & 255; d[i + 1] = (argb >> 8) & 255;" +
            " d[i + 2] = argb & 255; d[i + 3] = (argb >>> 24) & 255",
    )
}

private fun putImage(
    canvas: JsAny,
    image: JsAny,
) {
    js("canvas.getContext('2d').putImageData(image, 0, 0)")
}

private fun drawCanvas(
    ctx: JsAny,
    source: JsAny,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
) {
    js("ctx.drawImage(source, x, y, w, h)")
}

private fun jpegDataUrl(
    canvas: JsAny,
    quality: Double,
): String = js("canvas.toDataURL('image/jpeg', quality)")

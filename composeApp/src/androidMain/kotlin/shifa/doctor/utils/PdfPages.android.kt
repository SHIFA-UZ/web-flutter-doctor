package shifa.doctor.utils

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import shifa.doctor.data.api.httpLogger
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max

private fun paint(
    size: Float,
    bold: Boolean,
    gray: Float = 0f,
) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    color = grayColor(gray)
}

private fun grayColor(gray: Float): Int = (gray.coerceIn(0f, 1f) * 255).toInt().let { Color.rgb(it, it, it) }

private fun layout(
    text: String,
    paint: TextPaint,
    width: Float,
    align: PdfAlign = PdfAlign.Start,
): StaticLayout =
    StaticLayout.Builder
        .obtain(text, 0, text.length, paint, max(1, width.toInt()))
        .setAlignment(
            when (align) {
                PdfAlign.Start -> Layout.Alignment.ALIGN_NORMAL
                PdfAlign.Center -> Layout.Alignment.ALIGN_CENTER
                PdfAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
            },
        ).build()

actual fun measurePdfText(
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): Float = if (text.isEmpty()) 0f else ceil(layout(text, paint(size, bold), width).height.toFloat())

actual fun renderPdf(pages: List<PdfPage>): ByteArray? {
    if (pages.isEmpty()) return null
    val doc = PdfDocument()
    return try {
        pages.forEachIndexed { i, p ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(p.width.toInt(), p.height.toInt(), i + 1).create())
            p.ops.forEach { draw(page.canvas, it) }
            doc.finishPage(page)
        }
        ByteArrayOutputStream().use {
            doc.writeTo(it)
            it.toByteArray()
        }
    } catch (e: Exception) {
        httpLogger.log("FAILED to write a PDF: ${e::class.simpleName}")
        null
    } finally {
        doc.close()
    }
}

private fun draw(
    canvas: Canvas,
    op: PdfOp,
) {
    when (op) {
        is PdfOp.Text -> {
            if (op.text.isEmpty()) return
            canvas.save()
            canvas.translate(op.x, op.y)
            layout(op.text, paint(op.size, op.bold, op.gray), op.width, op.align).draw(canvas)
            canvas.restore()
        }
        is PdfOp.Rect -> {
            val r = RectF(op.x, op.y, op.x + op.width, op.y + op.height)
            op.fill?.let { canvas.drawRect(r, Paint().apply { color = grayColor(it) }) }
            op.stroke?.let {
                canvas.drawRect(
                    r,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = op.strokeWidth
                        color = grayColor(it)
                    },
                )
            }
        }
        is PdfOp.Line ->
            canvas.drawLine(
                op.x1,
                op.y1,
                op.x2,
                op.y2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = op.width
                    color = grayColor(op.gray)
                },
            )
        is PdfOp.Image -> {
            val bitmap = BitmapFactory.decodeByteArray(op.bytes, 0, op.bytes.size) ?: return
            val scale = minOf(op.width / bitmap.width, op.height / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = op.x + (op.width - w) / 2
            val top = op.y + (op.height - h) / 2
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
        }
    }
}

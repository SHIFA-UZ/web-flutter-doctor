package shifa.doctor.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddRect
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSAttributedString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIImage
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawInRect
import platform.UIKit.drawWithRect
import kotlin.math.ceil

private fun attributes(
    size: Float,
    bold: Boolean,
    gray: Float = 0f,
    align: PdfAlign = PdfAlign.Start,
): Map<Any?, Any?> {
    val style =
        NSMutableParagraphStyle().apply {
            setAlignment(
                when (align) {
                    PdfAlign.Start -> NSTextAlignmentLeft
                    PdfAlign.Center -> NSTextAlignmentCenter
                    PdfAlign.End -> NSTextAlignmentRight
                },
            )
        }
    return mapOf(
        NSFontAttributeName to
            (if (bold) UIFont.boldSystemFontOfSize(size.toDouble()) else UIFont.systemFontOfSize(size.toDouble())),
        NSForegroundColorAttributeName to UIColor.colorWithWhite(gray.toDouble(), 1.0),
        NSParagraphStyleAttributeName to style,
    )
}

@OptIn(ExperimentalForeignApi::class)
actual fun measurePdfText(
    text: String,
    size: Float,
    bold: Boolean,
    width: Float,
): Float {
    if (text.isEmpty()) return 0f
    val string = NSAttributedString.create(string = text, attributes = attributes(size, bold))
    val height =
        string.boundingRectWithSize(
            CGSizeMake(width.toDouble(), 100_000.0),
            NSStringDrawingUsesLineFragmentOrigin,
            null,
        )
            .useContents { this.size.height }
    return ceil(height).toFloat()
}

@OptIn(ExperimentalForeignApi::class)
actual fun renderPdf(pages: List<PdfPage>): ByteArray? {
    if (pages.isEmpty()) return null
    val first = pages.first()
    val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, first.width.toDouble(), first.height.toDouble()))
    return renderer.PDFDataWithActions { context ->
        pages.forEach { page ->
            context?.beginPageWithBounds(
                CGRectMake(0.0, 0.0, page.width.toDouble(), page.height.toDouble()),
                emptyMap<Any?, Any?>(),
            )
            page.ops.forEach(::draw)
        }
    }.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun draw(op: PdfOp) {
    val cg = UIGraphicsGetCurrentContext() ?: return
    when (op) {
        is PdfOp.Text -> {
            if (op.text.isEmpty()) return
            NSAttributedString.create(string = op.text, attributes = attributes(op.size, op.bold, op.gray, op.align))
                .drawWithRect(
                    CGRectMake(op.x.toDouble(), op.y.toDouble(), op.width.toDouble(), 100_000.0),
                    NSStringDrawingUsesLineFragmentOrigin,
                    null,
                )
        }
        is PdfOp.Rect -> {
            val rect = CGRectMake(op.x.toDouble(), op.y.toDouble(), op.width.toDouble(), op.height.toDouble())
            op.fill?.let {
                UIColor.colorWithWhite(it.toDouble(), 1.0).setFill()
                CGContextFillRect(cg, rect)
            }
            op.stroke?.let {
                UIColor.colorWithWhite(it.toDouble(), 1.0).setStroke()
                CGContextSetLineWidth(cg, op.strokeWidth.toDouble())
                CGContextAddRect(cg, rect)
                CGContextStrokePath(cg)
            }
        }
        is PdfOp.Line -> {
            UIColor.colorWithWhite(op.gray.toDouble(), 1.0).setStroke()
            CGContextSetLineWidth(cg, op.width.toDouble())
            CGContextMoveToPoint(cg, op.x1.toDouble(), op.y1.toDouble())
            CGContextAddLineToPoint(cg, op.x2.toDouble(), op.y2.toDouble())
            CGContextStrokePath(cg)
        }
        is PdfOp.Image -> {
            val image = UIImage.imageWithData(op.bytes.toNSData()) ?: return
            val (w, h) = image.size.useContents { width to height }
            if (w <= 0.0 || h <= 0.0) return
            val scale = minOf(op.width / w, op.height / h)
            val dw = w * scale
            val dh = h * scale
            image.drawInRect(CGRectMake(op.x + (op.width - dw) / 2, op.y + (op.height - dh) / 2, dw, dh))
        }
    }
}

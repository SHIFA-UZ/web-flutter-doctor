package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.UIKit.UITapGestureRecognizer
import platform.darwin.NSObject

// PDFKit scrolls and pinch-zooms by itself; a native view gets no Compose taps, so a tap recognizer calls [onTap].
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun PdfView(
    bytes: ByteArray,
    modifier: Modifier,
    onTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val target = remember { TapTarget { tap() } }
    val document =
        remember(
            bytes,
        ) { runCatching { PDFDocument(data = bytes.toNSData()) }.getOrNull()?.takeIf { it.pageCount > 0u } }
    if (document == null) return fallback()
    UIKitView(
        factory = {
            PDFView().apply {
                autoScales = true
                this.document = document
                addGestureRecognizer(
                    UITapGestureRecognizer(
                        target,
                        NSSelectorFromString("tapped"),
                    ).apply { cancelsTouchesInView = false },
                )
            }
        },
        modifier = modifier,
        // A new document replaces the shown one (another patient's file must never stay on screen).
        update = { it.document = document },
    )
}

@OptIn(BetaInteropApi::class)
private class TapTarget(private val onTap: () -> Unit) : NSObject() {
    @ObjCAction
    fun tapped() = onTap()
}

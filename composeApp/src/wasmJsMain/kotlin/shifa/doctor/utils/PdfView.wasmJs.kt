package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement

/**
 * The pages drawn by pdf.js (index.html's `shifaPdf`) in a scrolling box, each as wide as the view: phone browsers have
 * no PDF viewer inside a page (Chrome on Android only offered "Open"). [fallback] when it can't be read; [onTap] doesn't
 * reach it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PdfView(
    bytes: ByteArray,
    modifier: Modifier,
    onTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    var failed by remember(bytes) { mutableStateOf(false) }
    if (failed) return fallback()
    val url = remember(bytes) { blobUrl(bytes, "application/pdf") }
    val box =
        remember(bytes) {
            (document.createElement("div") as HTMLDivElement).apply {
                style.width = "100%"
                style.height = "100%"
                style.overflowY = "auto"
                style.setProperty("-webkit-overflow-scrolling", "touch")
            }
        }
    DisposableEffect(url) {
        val stop = renderPdf(box, url) { failed = true }
        onDispose {
            stopPdf(stop)
            revokeUrl(url)
        }
    }
    WebElementView(factory = { box }, modifier = modifier)
}

private fun renderPdf(
    box: HTMLDivElement,
    url: String,
    onFail: () -> Unit,
): JsAny = js("window.shifaPdf.render(box, url, onFail)")

private fun stopPdf(stop: JsAny): Unit = js("stop()")

private fun revokeUrl(url: String) {
    js("URL.revokeObjectURL(url)")
}

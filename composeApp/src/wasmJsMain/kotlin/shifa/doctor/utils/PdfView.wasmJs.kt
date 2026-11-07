package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLIFrameElement

/** The browser's own PDF viewer in an iframe (its toolbar zooms, prints and downloads). [onTap] doesn't reach it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PdfView(
    bytes: ByteArray,
    modifier: Modifier,
    onTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val url = remember(bytes) { blobUrl(bytes, "application/pdf") }
    DisposableEffect(url) { onDispose { revokeUrl(url) } }
    WebElementView(
        factory = {
            (document.createElement("iframe") as HTMLIFrameElement).apply {
                style.border = "none"
                style.width = "100%"
                style.height = "100%"
            }
        },
        modifier = modifier,
        update = { it.src = url },
    )
}

private fun revokeUrl(url: String) {
    js("URL.revokeObjectURL(url)")
}

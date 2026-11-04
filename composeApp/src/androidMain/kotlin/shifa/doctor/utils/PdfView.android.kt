package shifa.doctor.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import java.io.File
import kotlin.concurrent.thread

private const val A4 = 1f / 1.414f

// Pages render lazily at the view's width. ponytail: no pinch zoom here (iOS's PDFKit has it); add transformable if asked.
@Composable
actual fun PdfView(
    bytes: ByteArray,
    modifier: Modifier,
    onTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val cacheDir = LocalContext.current.cacheDir
    // Opened off the main thread (writing a large PDF would freeze the screen) and closed off it too, since close()
    // waits for a page that is still rendering. A new state per document, so a closed renderer is never read again.
    val state = remember(bytes) { mutableStateOf<Doc>(Doc.Loading) }
    LaunchedEffect(bytes) {
        val owner = Owner()
        try {
            withContext(Dispatchers.IO) { owner.adopt(open(cacheDir, bytes)) }
            state.value = owner.renderer?.let(Doc::Ready) ?: Doc.Failed
            awaitCancellation()
        } finally {
            owner.dispose()
        }
    }
    val renderer =
        when (val doc = state.value) {
            Doc.Loading -> return Box(modifier.background(ShifaColors.Bg.Canvas))
            Doc.Failed -> return fallback()
            is Doc.Ready -> doc.renderer.takeIf { it.pageCount > 0 } ?: return fallback()
        }
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(ShifaColors.Bg.Canvas)
                .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) },
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        ) {
            items(renderer.pageCount) { index ->
                val page by produceState<ImageBitmap?>(null, renderer, index, width) {
                    value = withContext(Dispatchers.IO) { runCatching { renderer.render(index, width) }.getOrNull() }
                }
                page?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                    ?: Box(Modifier.fillMaxWidth().aspectRatio(A4).background(ShifaColors.Bg.Surface))
            }
        }
    }
}

private sealed interface Doc {
    data object Loading : Doc

    data object Failed : Doc

    class Ready(
        val renderer: PdfRenderer,
    ) : Doc
}

// Who closes the renderer when the screen leaves while it is still opening: whichever of the opening thread and the
// disposal comes second.
private class Owner : SynchronizedObject() {
    var renderer: PdfRenderer? = null
        private set
    private var disposed = false

    fun adopt(opened: PdfRenderer?) {
        val late = synchronized(this) { if (disposed) opened else null.also { renderer = opened } }
        late?.close()
    }

    fun dispose() {
        val owned =
            synchronized(this) {
                disposed = true
                renderer
            }
        owned?.let { thread { synchronized(it) { it.close() } } }
    }
}

// PdfRenderer needs a seekable file; the open descriptor keeps it readable after the delete, which always happens.
private fun open(
    dir: File,
    bytes: ByteArray,
): PdfRenderer? {
    val file = runCatching { File.createTempFile("document", ".pdf", dir) }.getOrNull() ?: return null
    return try {
        file.writeBytes(bytes)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        runCatching { PdfRenderer(fd) }.onFailure { fd.close() }.getOrNull()
    } catch (e: Exception) {
        null
    } finally {
        file.delete()
    }
}

// One page at a time: PdfRenderer allows a single open page, and close() may come from the UI thread.
private fun PdfRenderer.render(
    index: Int,
    width: Int,
): ImageBitmap =
    synchronized(this) {
        openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(width, width * page.height / page.width, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        }
    }

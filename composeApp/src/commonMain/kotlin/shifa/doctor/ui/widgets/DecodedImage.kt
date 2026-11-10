package shifa.doctor.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import io.kamel.core.Resource
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import shifa.doctor.data.api.fileCacheKey

/**
 * A picture's bytes decoded away from the UI thread (a 12-megapixel photo froze the screen while it was decoded in
 * composition): null while decoding, then the bitmap or the failure. Previews and screenshot tests decode at once.
 */
@Composable
fun decodedImage(
    bytes: ByteArray,
    key: Any? = bytes,
): Result<ImageBitmap>? {
    if (LocalInspectionMode.current) return remember(key) { runCatching { bytes.decodeToImageBitmap() } }
    return produceState<Result<ImageBitmap>?>(null, key) {
        value = withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() } }
    }.value
}

/**
 * A backend file's picture. The backend signs a file's URL anew on every read, so a picture that loaded is kept under
 * the URL without its signature ([fileCacheKey]): a poll doesn't download it again. One that failed (an expired
 * signature, no network) is keyed by the whole URL instead, so the next read's fresh URL loads it.
 */
@Composable
fun fileImageResource(url: String): Resource<Painter> {
    val stable = fileCacheKey(url)
    var failed by remember(stable) { mutableStateOf(false) }
    val resource = asyncPainterResource(url, key = if (failed) url else stable)
    if (resource is Resource.Failure && !failed) SideEffect { failed = true }
    return resource
}

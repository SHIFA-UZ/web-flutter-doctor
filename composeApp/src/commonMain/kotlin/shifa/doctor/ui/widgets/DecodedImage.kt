package shifa.doctor.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

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

package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

@Composable
actual fun rememberImagePickerLauncher(onPicked: (PickedImage?) -> Unit): () -> Unit =
    rememberPhotoLauncher(camera = false, onPicked)

/** Phones' browsers open the camera (`capture`); a computer's opens the file dialog. No permission is asked first. */
@Composable
actual fun rememberCameraLauncher(
    onTaken: (PickedImage?) -> Unit,
    onDenied: () -> Unit,
): () -> Unit = rememberPhotoLauncher(camera = true, onTaken)

@Composable
private fun rememberPhotoLauncher(
    camera: Boolean,
    onPicked: (PickedImage?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val result = rememberUpdatedState(onPicked)
    return remember(camera) {
        {
            chooseFile("image/*", camera) { file ->
                if (file == null) {
                    result.value(null)
                } else {
                    scope.launch {
                        val name = fileName(file).substringBeforeLast('.') + ".jpg"
                        val jpeg = normalizedJpeg(file, PHOTO_MAX_SIDE, PHOTO_JPEG_QUALITY / 100.0)
                        result.value(jpeg?.let { PickedImage(name, it) })
                    }
                }
            }
        }
    }
}

// Browsers have no settings page a site can open; the permission is in the address bar.
actual fun openAppSettings() = Unit

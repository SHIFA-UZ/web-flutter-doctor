package shifa.doctor.utils

import androidx.compose.runtime.Composable

/**
 * A photo ready to upload (flow 08/02 §4): JPEG at quality [PHOTO_JPEG_QUALITY], upright, the longest side at most
 * [PHOTO_MAX_SIDE]; HEIC and PNG come out as JPEG. Copied from the patient app's picker and given that size rule.
 */
class PickedImage(
    val fileName: String,
    val bytes: ByteArray,
)

const val PHOTO_MAX_SIDE = 1600
const val PHOTO_JPEG_QUALITY = 85

/** The photo library (no permission needed on either platform). Null when cancelled or unreadable. */
@Composable
expect fun rememberImagePickerLauncher(onPicked: (PickedImage?) -> Unit): () -> Unit

/**
 * The camera; asks for the camera permission first. [onTaken] gets null when cancelled or there's no camera;
 * [onDenied] runs when the permission is refused (now or earlier), so the screen can point to the OS settings.
 */
@Composable
expect fun rememberCameraLauncher(
    onTaken: (PickedImage?) -> Unit,
    onDenied: () -> Unit,
): () -> Unit

/** The app's page in the OS settings (permissions). */
expect fun openAppSettings()

package shifa.doctor.utils

import androidx.compose.runtime.Composable

// ponytail: photo picking on the web is WEB-05 (<input type="file" accept="image/*">); until then it reads as a cancel.
@Composable
actual fun rememberImagePickerLauncher(onPicked: (PickedImage?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberCameraLauncher(
    onTaken: (PickedImage?) -> Unit,
    onDenied: () -> Unit,
): () -> Unit = { onTaken(null) }

// Browsers have no settings page a site can open; the permission is in the address bar.
actual fun openAppSettings() = Unit

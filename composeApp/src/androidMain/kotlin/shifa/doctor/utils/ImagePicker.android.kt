package shifa.doctor.utils

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// The gallery file is copied into the cache first, so the EXIF orientation can be read the same way as the camera's.
@Composable
actual fun rememberImagePickerLauncher(onPicked: (PickedImage?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult picked(null)
            scope.launch {
                val jpeg =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val file = File(context.cacheDir, "camera/picked.jpg").apply { parentFile?.mkdirs() }
                            context.contentResolver.openInputStream(uri)!!.use {
                                    input ->
                                file.outputStream().use(input::copyTo)
                            }
                            preparedJpeg(file).also { file.delete() }
                        }.getOrNull()
                    }
                picked(jpeg?.let { PickedImage("photo.jpg", it) })
            }
        }
    return { launcher.launch("image/*") }
}

// The manifest declares CAMERA (video calls), and then TakePicture throws unless it's granted, so it's asked for
// first. Once it is refused for good the OS no longer asks and answers "denied" at once, so the screen offers Settings.
@Composable
actual fun rememberCameraLauncher(
    onTaken: (PickedImage?) -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taken by rememberUpdatedState(onTaken)
    val denied by rememberUpdatedState(onDenied)
    val file = remember { File(context.cacheDir, "camera/photo.jpg") }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (!ok) return@rememberLauncherForActivityResult taken(null)
            scope.launch {
                val jpeg = withContext(Dispatchers.IO) { runCatching { preparedJpeg(file) }.getOrNull() }
                file.delete()
                taken(jpeg?.let { PickedImage("photo.jpg", it) })
            }
        }
    val takePicture = {
        try {
            file.parentFile?.mkdirs()
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (_: ActivityNotFoundException) {
            taken(null)
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) takePicture() else denied()
        }
    return {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            takePicture()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }
}

// Decoded at a power-of-two sample near the limit, rotated by its EXIF orientation (BitmapFactory ignores it), scaled
// to at most PHOTO_MAX_SIDE, JPEG 85.
private fun preparedJpeg(file: File): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
    val decoded =
        BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
            },
        ) ?: error("Not an image")
    val orientation =
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val longest = maxOf(decoded.width, decoded.height)
    val scale = if (longest > PHOTO_MAX_SIDE) PHOTO_MAX_SIDE.toFloat() / longest else 1f
    val matrix =
        Matrix().apply {
            postScale(scale, scale)
            // All eight EXIF orientations, mirrored ones included; createBitmap moves the result back into view.
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> postRotate(90f).also { postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> postRotate(-90f).also { postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
            }
        }
    val upright =
        if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
            )
        }
    return ByteArrayOutputStream().use {
        upright.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it)
        it.toByteArray()
    }
}

actual fun openAppSettings() {
    appContext.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", appContext.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

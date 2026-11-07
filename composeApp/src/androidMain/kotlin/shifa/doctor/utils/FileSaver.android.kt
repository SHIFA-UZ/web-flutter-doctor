package shifa.doctor.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import shifa.doctor.data.api.httpLogger
import java.io.File
import java.io.FileOutputStream

actual class FileSaver {
    private var context: Context? = null

    fun setContext(ctx: Context) {
        context = ctx
    }

    actual fun saveFile(
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String> {
        val ctx = context ?: return Result.failure(Exception("Context not available"))

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveFileMediaStore(ctx, fileName, fileBytes)
            } else {
                saveFileLegacy(ctx, fileName, fileBytes)
            }
        } catch (e: Exception) {
            httpLogger.log("FAILED to save a file: ${e::class.simpleName}") // no message: it names the file
            Result.failure(e)
        }
    }

    private fun saveFileMediaStore(
        context: Context,
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String> {
        val contentValues =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

        val uri =
            context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues,
            ) ?: return Result.failure(Exception("Failed to create file"))

        // A missing stream or a failed write is a failure, and the half-made entry is removed.
        val written =
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(fileBytes) } != null
            }.getOrDefault(false)
        if (!written) {
            context.contentResolver.delete(uri, null, null)
            return Result.failure(Exception("Failed to write file"))
        }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, contentValues, null, null)

        return Result.success(uri.toString())
    }

    // Android 9 and below: the app's own Downloads folder, which needs no storage permission.
    private fun saveFileLegacy(
        context: Context,
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String> {
        val downloadsDir =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS,
            ) ?: return Result.failure(Exception("No storage"))
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { outputStream ->
            outputStream.write(fileBytes)
        }

        return Result.success(file.absolutePath)
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}

@Composable
actual fun rememberFileSaver(): FileSaver {
    val context = LocalContext.current
    return androidx.compose.runtime.remember {
        FileSaver().apply { setContext(context) }
    }
}

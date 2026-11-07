package shifa.doctor.utils

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import shifa.doctor.data.api.httpLogger
import java.io.File

@Composable
actual fun rememberFileShare(): (fileName: String, mimeType: String, bytes: ByteArray) -> Boolean {
    val context = LocalContext.current
    return { fileName, mimeType, bytes ->
        runCatching {
            // `share/` in the cache (res/xml/file_paths.xml); each export replaces the last.
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, safeFileName(fileName)).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { httpLogger.log("FAILED to share a file: ${it::class.simpleName}") }.isSuccess
    }
}

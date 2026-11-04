package shifa.doctor.utils

import androidx.compose.runtime.Composable

expect class FileSaver() {
    fun saveFile(
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String>
}

@Composable
expect fun rememberFileSaver(): FileSaver

/** A file name any file system takes. */
fun safeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").trim().ifEmpty { "document" }

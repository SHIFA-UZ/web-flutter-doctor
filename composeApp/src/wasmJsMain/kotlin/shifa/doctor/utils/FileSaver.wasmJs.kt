package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class FileSaver actual constructor() {
    /** The browser downloads it; the result is the file name (the browser picks the folder). */
    actual fun saveFile(
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String> =
        runCatching {
            val name = safeFileName(fileName)
            downloadBytes(name, "application/octet-stream", fileBytes)
            name
        }
}

@Composable
actual fun rememberFileSaver(): FileSaver = remember { FileSaver() }

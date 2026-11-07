package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import shifa.doctor.data.api.httpLogger

actual class FileSaver {
    @OptIn(ExperimentalForeignApi::class)
    actual fun saveFile(
        fileName: String,
        fileBytes: ByteArray,
    ): Result<String> {
        return try {
            val documentsPath =
                NSSearchPathForDirectoriesInDomains(
                    NSDocumentDirectory,
                    NSUserDomainMask,
                    true,
                ).firstOrNull() as? String ?: return Result.failure(Exception("Documents directory not found"))

            val filePath = "$documentsPath/$fileName"

            val nsData =
                fileBytes.usePinned { pinned ->
                    NSData.create(
                        bytes = pinned.addressOf(0),
                        length = fileBytes.size.toULong(),
                    )
                }

            val success = nsData.writeToFile(filePath, atomically = true)

            if (success) {
                Result.success(filePath)
            } else {
                Result.failure(Exception("Failed to write file"))
            }
        } catch (e: Exception) {
            httpLogger.log("FAILED to save a file: ${e::class.simpleName}") // no message: it names the file
            Result.failure(e)
        }
    }
}

@Composable
actual fun rememberFileSaver(): FileSaver {
    return remember { FileSaver() }
}

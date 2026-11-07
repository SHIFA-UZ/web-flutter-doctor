package shifa.doctor.utils

actual fun writeCacheFile(
    name: String,
    bytes: ByteArray,
): String = blobUrl(bytes, mimeTypeOf(name))

/** The type a browser needs to play or show a `blob:` file, from its extension. */
internal fun mimeTypeOf(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

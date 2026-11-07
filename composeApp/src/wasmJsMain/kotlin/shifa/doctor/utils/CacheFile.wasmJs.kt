package shifa.doctor.utils

actual fun writeCacheFile(
    name: String,
    bytes: ByteArray,
): String = blobUrl(bytes, "application/octet-stream")

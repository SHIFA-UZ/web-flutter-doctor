package shifa.doctor.utils

/** Writes [bytes] to the app's cache as [name] and returns a URL the platform player can open. */
expect fun writeCacheFile(
    name: String,
    bytes: ByteArray,
): String

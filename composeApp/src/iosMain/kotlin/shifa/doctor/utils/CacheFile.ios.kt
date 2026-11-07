package shifa.doctor.utils

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL

actual fun writeCacheFile(
    name: String,
    bytes: ByteArray,
): String {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    bytes.toNSData().writeToURL(url, true)
    return url.absoluteString ?: url.path.orEmpty()
}

package shifa.doctor.utils

// The browser calls the web actuals share. Kotlin/Wasm passes no ByteArray to JavaScript, so bytes go over one by one.

/** A JavaScript `Uint8Array` (or any array of bytes) as a [ByteArray]. */
internal fun toByteArray(array: JsAny): ByteArray = ByteArray(lengthOf(array)) { byteAt(array, it).toByte() }

private fun lengthOf(array: JsAny): Int = js("array.length")

internal fun byteAt(
    array: JsAny,
    index: Int,
): Int = js("array[index]")

internal fun toUint8Array(bytes: ByteArray): JsAny {
    val array = newUint8Array(bytes.size)
    for (i in bytes.indices) setByte(array, i, bytes[i].toInt() and 0xff)
    return array
}

/** A `blob:` URL for [bytes], valid while the page lives. */
internal fun blobUrl(
    bytes: ByteArray,
    mimeType: String,
): String = createObjectUrl(toUint8Array(bytes), mimeType)

/** The browser's download of [bytes] as [fileName]. */
internal fun downloadBytes(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
) {
    val url = blobUrl(bytes, mimeType)
    clickDownload(url, fileName)
    revokeObjectUrl(url)
}

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun setByte(
    array: JsAny,
    index: Int,
    value: Int,
) {
    js("array[index] = value")
}

private fun createObjectUrl(
    array: JsAny,
    mimeType: String,
): String = js("URL.createObjectURL(new Blob([array], { type: mimeType }))")

private fun revokeObjectUrl(url: String) {
    js("setTimeout(function () { URL.revokeObjectURL(url) }, 60000)")
}

private fun clickDownload(
    url: String,
    fileName: String,
) {
    js(
        "var a = document.createElement('a'); a.href = url; a.download = fileName;" +
            "document.body.appendChild(a); a.click(); a.remove()",
    )
}

/**
 * Opens [url] in a new tab; false when the browser blocked it. Not `noopener`: with it `window.open` returns null even
 * when the tab opened, so the opener is cut by hand instead.
 */
internal fun openTab(url: String): Boolean =
    js("(function () { var w = window.open(url, '_blank'); if (w) { w.opener = null } return w !== null })()")

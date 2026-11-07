package shifa.doctor.utils

import kotlinx.coroutines.await
import kotlin.js.Promise

// The browser's file dialog (<input type="file">) for the document, photo and camera launchers.

/** Opens the dialog; [done] gets the chosen `File`, or null when it was closed. */
internal fun chooseFile(
    accept: String,
    camera: Boolean,
    done: (JsAny?) -> Unit,
) {
    js(
        """(function () {
    var input = document.createElement('input'), settled = false;
    input.type = 'file'; input.accept = accept; input.style.display = 'none';
    if (camera) input.setAttribute('capture', 'environment');
    function finish(f) { if (settled) return; settled = true; input.remove(); done(f) }
    input.onchange = function () { finish(input.files && input.files.length ? input.files[0] : null) };
    input.oncancel = function () { finish(null) };
    document.body.appendChild(input); input.click();
})()""",
    )
}

internal fun fileName(file: JsAny): String = js("file.name || 'file'")

/** The file's bytes; null when the browser couldn't read it. */
internal suspend fun readFile(file: JsAny): ByteArray? =
    runCatching { toByteArray(fileBytes(file).await<JsAny>()) }.getOrNull()

private fun fileBytes(file: JsAny): Promise<JsAny> =
    js(
        "file.arrayBuffer().then(function (b) { return new Uint8Array(b) })",
    )

/** A photo as the phones send it: JPEG, upright, the longest side at most [maxSide]; null when it can't be decoded. */
internal suspend fun normalizedJpeg(
    file: JsAny,
    maxSide: Int,
    quality: Double,
): ByteArray? = runCatching { jpegOf(file, maxSide, quality).await<JsAny?>()?.let(::toByteArray) }.getOrNull()

private fun jpegOf(
    file: JsAny,
    maxSide: Int,
    quality: Double,
): Promise<JsAny?> =
    js(
        """createImageBitmap(file, { imageOrientation: 'from-image' }).then(function (bmp) {
    var scale = Math.min(1, maxSide / Math.max(bmp.width, bmp.height));
    var c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(bmp.width * scale)); c.height = Math.max(1, Math.round(bmp.height * scale));
    var g = c.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height);
    g.drawImage(bmp, 0, 0, c.width, c.height); bmp.close();
    return new Promise(function (resolve) {
        c.toBlob(function (b) {
            if (!b) return resolve(null);
            b.arrayBuffer().then(function (a) { resolve(new Uint8Array(a)) }, function () { resolve(null) });
        }, 'image/jpeg', quality);
    });
}).catch(function () { return null })""",
    )

package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The Screen Wake Lock API while in composition (a video visit), taken again whenever the page comes back into view
 * (browsers drop it when the tab is hidden). Browsers without it just let the screen sleep.
 */
@Composable
actual fun KeepScreenOn() =
    DisposableEffect(Unit) {
        val lock = holdWakeLock()
        onDispose { releaseWakeLock(lock) }
    }

private fun holdWakeLock(): JsAny =
    js(
        """(function () {
    var h = { lock: null, done: false };
    function take() {
        if (h.done || !navigator.wakeLock || document.visibilityState !== 'visible') return;
        navigator.wakeLock.request('screen').then(function (l) {
            if (h.done) { l.release().catch(function () {}) } else { h.lock = l }
        }, function () {});
    }
    h.onVisible = take;
    document.addEventListener('visibilitychange', take);
    take();
    return h;
})()""",
    )

private fun releaseWakeLock(h: JsAny) {
    js(
        "h.done = true; document.removeEventListener('visibilitychange', h.onVisible);" +
            " if (h.lock) h.lock.release().catch(function () {})",
    )
}

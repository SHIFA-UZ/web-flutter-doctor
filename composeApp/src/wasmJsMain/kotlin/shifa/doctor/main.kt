package shifa.doctor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

// kotlinx-datetime knows only UTC on the web until this module registers the IANA zones (the practice time zone).
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

// WEB-04: the app in the page's `composeApp` element (index.html).
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    JsJodaTimeZoneModule
    bridgeBrowserBack()
    ComposeViewport("composeApp") {
        App()
    }
}

/**
 * WEB-10: the browser's Back works as the app's Back. One history entry is kept armed; its Back reaches the app as
 * Escape, which Compose's back handling takes (a sheet closes, a pushed screen pops, as Android's Back does), and the
 * entry is armed again. At Home's root the app stays (nothing to go back to).
 */
private fun bridgeBrowserBack() {
    js(
        """(function () {
    history.pushState({ shifa: true }, '');
    window.addEventListener('popstate', function () {
        var host = document.getElementById('composeApp');
        var canvas = host && host.shadowRoot && host.shadowRoot.querySelector('canvas');
        if (canvas) ['keydown', 'keyup'].forEach(function (type) {
            canvas.dispatchEvent(new KeyboardEvent(type, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, composed: true }));
        });
        history.pushState({ shifa: true }, '');
    });
})()""",
    )
}

package shifa.doctor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import org.jetbrains.compose.resources.configureWebResources
import shifa.doctor.data.tracking.PostHogWebSink
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.utils.WEB_POSTHOG_KEY
import shifa.doctor.utils.fadeBootFrame

// kotlinx-datetime knows only UTC on the web until this module registers the IANA zones (the practice time zone).
@JsModule("@js-joda/timezone")
external object JsJodaTimeZoneModule

// WEB-04: the app in the page's `composeApp` element (index.html).
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    JsJodaTimeZoneModule
    // Fonts, icons and strings keep their names between builds, and the host's nginx lets browsers keep fonts and icons
    // for 60 days: each URL carries the build's resource hash (index.html's data-resources, written by the deploy), so a
    // deploy reaches every browser. Empty in local builds.
    val version = resourceVersion()
    configureWebResources { resourcePathMapping { path -> if (version.isEmpty()) "./$path" else "./$path?v=$version" } }
    bridgeBrowserBack()
    // Product analytics (docs/posthog): only a build with the project key; Tracking decides whether anything is sent.
    if (WEB_POSTHOG_KEY.isNotEmpty()) {
        Tracking.start(
            PostHogWebSink(WEB_POSTHOG_KEY),
            webHost = window.location.hostname,
        )
    }
    ComposeViewport("composeApp") {
        App()
    }
    // Normally ResourceWarmup lifts the loading frame once the splash can draw; this is the backstop.
    fadeBootFrame(BOOT_FRAME_LIMIT_MS)
}

private const val BOOT_FRAME_LIMIT_MS = 10_000

private fun resourceVersion(): String = js("document.documentElement.dataset.resources || ''")

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
        // The address of what the app shows (WEB-10), not the entry Back landed on.
        history.pushState({ shifa: true }, '', window.shifaPath || location.pathname);
    });
})()""",
    )
}

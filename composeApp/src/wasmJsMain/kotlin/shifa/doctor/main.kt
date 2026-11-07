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
    ComposeViewport("composeApp") {
        App()
    }
}

package shifa.doctor.utils

import androidx.compose.runtime.Composable

/** The browser's share sheet where there is one (Safari, phones), the clipboard elsewhere. */
@Composable
actual fun rememberTextShare(): (text: String) -> Unit = { shareText(it) }

private fun shareText(text: String) {
    js(
        "if (navigator.share) { navigator.share({ text: text }).catch(function () {}) }" +
            " else { navigator.clipboard.writeText(text) }",
    )
}

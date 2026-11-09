package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * The web has no system font to fall back to, so emoji people type (chat, names, notes) drew as nothing. This loads
 * Noto Color Emoji once the app has started (2.4 MB from jsDelivr, compressed; the browser keeps it a year); a font the
 * resolver has preloaded is where Compose looks for characters Manrope lacks. Offline or blocked: no emoji, as before.
 */
@Composable
internal fun EmojiFont() {
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(Unit) {
        val bytes = runCatching { toByteArray(fetchBytes(EMOJI_FONT_URL).await<JsAny>()) }.getOrNull()
        if (bytes != null) resolver.preload(FontFamily(Font("NotoColorEmoji", bytes)))
    }
}

// The vector (COLRv1) build: half the bitmap build's 10.7 MB, sharp at any size.
private const val EMOJI_FONT_URL = "https://cdn.jsdelivr.net/gh/googlefonts/noto-emoji@v2.051/fonts/Noto-COLRv1.ttf"

private fun fetchBytes(url: String): Promise<JsAny> =
    js(
        """fetch(url).then(function (r) { if (!r.ok) throw new Error(r.status); return r.arrayBuffer(); })
    .then(function (b) { return new Uint8Array(b); })""",
    )

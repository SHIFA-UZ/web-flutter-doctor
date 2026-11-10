package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.preloadFont
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allDrawableResources
import shifa.doctor.generated.resources.allPluralStringResources
import shifa.doctor.ui.theme.manropeFontFamily
import shifa.doctor.ui.theme.manropeFonts

/**
 * ponytail: works around Compose Multiplatform 1.10's web resource cache. A string, icon or font load belongs to the
 * first composable that asked for it; when that one leaves the screen mid-load, everything else waiting on the same
 * resource stays blank for good (random missing labels and icons). This reads every resource once from a composable
 * that never leaves, so every load has an owner that is never cancelled; it reads again in a new language. Strings
 * aren't read here: the web looks them up in its own table (AppLocale.wasmJs.kt), read once per language.
 * Fixed in Compose Multiplatform 1.12.2 (`AsyncCache` loads in its own scope): delete this with that upgrade.
 *
 * It also keeps the start smooth on slow phones: index.html's loading frame (animated by the browser, smooth however
 * busy the page is) stays until the fonts and icons have loaded and the fonts have shaped text once, so that first-time
 * work doesn't stutter the splash animation or Sign In; then [resourcesReady].
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun ResourceWarmup() {
    // The same (file, weight) pairs as the theme: CMP keeps a font per weight.
    val fonts = manropeFonts.map { (font, weight) -> preloadFont(font, weight) }
    val drawables = Res.allDrawableResources.values
    // Each resource in its own scope, so one that arrives recomposes only its own call, not all ~180.
    val loadedIcons = remember { mutableIntStateOf(0) }
    drawables.forEach { key(it) { WarmDrawable(it, loadedIcons) } }
    Res.allPluralStringResources.values.forEach { key(it) { WarmPlural(it) } }
    val fontsReady = fonts.all { it.value != null }
    val iconsReady by remember { derivedStateOf { loadedIcons.intValue >= drawables.size } }
    val family by rememberUpdatedState(manropeFontFamily())
    val measurer = rememberTextMeasurer()
    LaunchedEffect(fontsReady) {
        if (!fontsReady) return@LaunchedEffect
        // An icon that never loads doesn't hold the app back for long.
        withTimeoutOrNull(ICONS_WAIT_MS) { snapshotFlow { iconsReady }.first { it } }
        // The first text in each weight builds its typeface and shaping caches: here, not in Sign In's first frame.
        manropeFonts.forEach { (_, weight) ->
            measurer.measure(SAMPLE, TextStyle(fontFamily = family, fontWeight = weight, fontSize = 16.sp))
        }
        resourcesReady.value = true
        fadeBootFrame(0)
    }
    // After the start, so its download doesn't hold the splash. Stays when this workaround goes (CMP 1.12.2).
    if (resourcesReady.value) EmojiFont()
}

@Composable
private fun WarmDrawable(
    resource: DrawableResource,
    loaded: MutableIntState,
) {
    // A vector is a 1 dp placeholder and a picture 1 px while it loads.
    val ready = painterResource(resource).intrinsicSize.width > LocalDensity.current.density
    LaunchedEffect(ready) { if (ready) loaded.intValue++ }
}

@Composable
private fun WarmPlural(resource: PluralStringResource) {
    pluralStringResource(resource, 1)
}

private const val ICONS_WAIT_MS = 1_500L
private const val SAMPLE = "Shifa Doctor Sign In Войти Kirish Тизимга кириш Ққ Ғғ Ҳҳ Ўў 0123456789"

/** Fades index.html's loading frame (the splash's first frame) out after [delayMs] and the next two frames. */
fun fadeBootFrame(delayMs: Int) {
    js(
        """setTimeout(function () { requestAnimationFrame(function () { requestAnimationFrame(function () {
    var boot = document.getElementById('boot');
    if (!boot) return;
    boot.style.opacity = '0';
    setTimeout(function () { boot.remove(); }, 300);
}); }); }, delayMs)""",
    )
}

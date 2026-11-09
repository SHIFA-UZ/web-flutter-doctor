package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allDrawableResources
import shifa.doctor.generated.resources.allFontResources
import shifa.doctor.generated.resources.allPluralStringResources
import shifa.doctor.generated.resources.appName
import shifa.doctor.generated.resources.shifa_mark

/**
 * ponytail: works around Compose Multiplatform 1.10's web resource cache. A string, icon or font load belongs to the
 * first composable that asked for it; when that one leaves the screen mid-load, everything else waiting on the same
 * resource stays blank for good (random missing labels and icons). This reads every resource once from a composable
 * that never leaves, so every load has an owner that is never cancelled; it reads again in a new language. Strings
 * aren't read here: the web looks them up in its own table (AppLocale.wasmJs.kt), read once per language.
 * Fixed in Compose Multiplatform 1.12.2 (`AsyncCache` loads in its own scope): delete this with that upgrade.
 */
@Composable
actual fun ResourceWarmup() {
    Res.allFontResources.values.forEach { Font(it) }
    Res.allDrawableResources.values.forEach { painterResource(it) }
    Res.allPluralStringResources.values.forEach { pluralStringResource(it, 1) }
    // index.html's loading frame stays until the splash can draw its icon and name: a string is empty and a vector is
    // a 1 dp placeholder while they load.
    val name = tr(Res.string.appName)
    val mark = painterResource(Res.drawable.shifa_mark)
    val ready = name.isNotEmpty() && mark.intrinsicSize.width > LocalDensity.current.density
    LaunchedEffect(ready) { if (ready) fadeBootFrame(0) }
}

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

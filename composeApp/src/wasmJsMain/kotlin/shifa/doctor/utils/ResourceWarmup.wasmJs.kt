package shifa.doctor.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.allDrawableResources
import shifa.doctor.generated.resources.allFontResources
import shifa.doctor.generated.resources.allPluralStringResources
import shifa.doctor.generated.resources.allStringResources

/**
 * ponytail: works around Compose Multiplatform 1.10's web resource cache. A string, icon or font load belongs to the
 * first composable that asked for it; when that one leaves the screen mid-load, everything else waiting on the same
 * resource stays blank for good (random missing labels and icons). This reads every resource once from a composable
 * that never leaves, so every load has an owner that is never cancelled; it reads again in a new language.
 * Fixed in Compose Multiplatform 1.12.2 (`AsyncCache` loads in its own scope): delete this with that upgrade.
 */
@Composable
actual fun ResourceWarmup() {
    Res.allFontResources.values.forEach { Font(it) }
    Res.allDrawableResources.values.forEach { painterResource(it) }
    Res.allStringResources.values.forEach { stringResource(it) }
    Res.allPluralStringResources.values.forEach { pluralStringResource(it, 1) }
}

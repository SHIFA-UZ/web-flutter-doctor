package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Points the platform locale at [code] for [content] (CMP has no runtime locale switch). Resources re-resolve
 * because [LocalAppLocale] changing recomposes the whole tree. Uzbek Cyrillic passes `uz` here and sets
 * [LocalUzbekCyrillic].
 */
@Composable
expect fun ProvideAppLocale(
    code: String,
    content: @Composable () -> Unit,
)

internal val LocalAppLocale = staticCompositionLocalOf { "" }

/** True while the app shows Uzbek in Cyrillic: the `uz` (Latin) strings are transliterated as they resolve. */
val LocalUzbekCyrillic = staticCompositionLocalOf { false }

/**
 * The app's string lookup: use it instead of `stringResource` so Uzbek Cyrillic works everywhere. The template is
 * transliterated before the arguments go in, so names and numbers are never transliterated.
 */
@Composable
fun tr(
    resource: StringResource,
    vararg args: Any,
): String = localized(stringResource(resource), args)

/** [tr] for `<plurals>`: [quantity] picks the form, [args] fill it (usually the quantity first). */
@Composable
fun trPlural(
    resource: PluralStringResource,
    quantity: Int,
    vararg args: Any,
): String = localized(pluralStringResource(resource, quantity), args)

@Composable
private fun localized(
    template: String,
    args: Array<out Any>,
): String {
    val cyrillic = LocalUzbekCyrillic.current
    val text = remember(template, cyrillic) { if (cyrillic) UzbekCyrillic.fromLatin(template) else template }
    return text.withArgs(args)
}

private val ARG = Regex("""%(\d+)\$[ds]""")

/** CMP's own `%1$s` / `%1$d` substitution, for templates fetched without arguments. */
internal fun String.withArgs(args: Array<out Any>): String =
    if (args.isEmpty()) this else ARG.replace(this) { args[it.groupValues[1].toInt() - 1].toString() }

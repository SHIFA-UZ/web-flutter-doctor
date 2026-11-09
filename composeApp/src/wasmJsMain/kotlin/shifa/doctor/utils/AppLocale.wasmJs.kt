package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import shifa.doctor.generated.resources.Res
import kotlin.io.encoding.Base64

@Composable
actual fun ProvideAppLocale(
    code: String,
    content: @Composable () -> Unit,
) {
    // Compose's Locale.current reads navigator.languages on the web: point it at the app's language.
    remember(code) { overrideBrowserLanguage(code) }
    LaunchedEffect(code) { loadStrings(code) }
    CompositionLocalProvider(LocalAppLocale provides code, content = content)
}

// ponytail: CMP 1.10 reads each web string on its own, and per string the whole language file again from the browser's
// cache; with ~2,900 strings a language switch took seconds. Each language's file is read once here instead and looked
// up in memory. Until it has loaded, strings come from CMP. Drop it once CMP reads a file once.
private val tables = mutableStateMapOf<String, Map<String, String>>() // folder (values, values-ru) → key → text

@Composable
internal actual fun stringOf(resource: StringResource): String {
    val code = LocalAppLocale.current
    // English is the default folder, which also fills keys another language lacks (as CMP does).
    val own = tables[if (code == "en") "values" else "values-$code"]
    return own?.let { it[resource.key] ?: tables["values"]?.get(resource.key) } ?: stringResource(resource)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadStrings(code: String) {
    for (folder in if (code == "en") listOf("values") else listOf("values", "values-$code")) {
        if (folder in tables) continue
        // Both files or none: a failed read leaves this language to CMP.
        val files =
            STRING_FILES.map { file ->
                runCatching { Res.readBytes("$folder/$file.commonMain.cvr") }.getOrNull() ?: return
            }
        tables[folder] = files.fold(emptyMap()) { all, bytes -> all + parseStrings(bytes.decodeToString()) }
    }
}

private val STRING_FILES = listOf("strings", "strings_kit") // composeResources/values*/<name>.xml

/** CMP's .cvr: a line per string, `string|key|base64 text`. */
internal fun parseStrings(cvr: String): Map<String, String> =
    cvr
        .lineSequence()
        .mapNotNull { line ->
            val (type, key, text) = line.split('|').takeIf { it.size == 3 } ?: return@mapNotNull null
            if (type == "string") key to Base64.decode(text).decodeToString() else null
        }.toMap()

private fun overrideBrowserLanguage(code: String) {
    js(
        "Object.defineProperty(navigator, 'languages', { get: function () { return [code] }, configurable: true });" +
            "Object.defineProperty(navigator, 'language', { get: function () { return code }, configurable: true })",
    )
}

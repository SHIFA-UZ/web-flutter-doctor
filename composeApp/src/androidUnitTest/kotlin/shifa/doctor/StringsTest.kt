package shifa.doctor

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

// Unit tests run from the module dir. uz and ru never fall back to English (strings/README.md), so every key and
// every plural must exist in all three, with the same placeholders.
class StringsTest {
    private val res = "src/commonMain/composeResources"
    private val locales = listOf("values-uz", "values-ru")

    private fun entries(
        dir: String,
        file: String,
        tag: String,
    ): Map<String, String> =
        Regex("""<$tag name="(\w+)">(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("$res/$dir/$file").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun placeholders(text: String) = Regex("""%\d+\$[ds]""").findAll(text).map { it.value }.toSet()

    @Test
    fun `uz and ru have exactly the English keys`() {
        listOf("strings.xml" to "string", "strings_kit.xml" to "string", "plurals.xml" to "plurals").forEach {
                (file, tag) ->
            val en = entries("values", file, tag).keys
            locales.forEach {
                assertWithMessage(
                    "$it/$file",
                ).that(entries(it, file, tag).keys).containsExactlyElementsIn(en)
            }
        }
    }

    @Test
    fun `every locale uses the English placeholders`() {
        listOf("strings.xml", "strings_kit.xml").forEach { file ->
            val en = entries("values", file, "string")
            locales.forEach { dir ->
                entries(dir, file, "string").forEach { (key, value) ->
                    assertWithMessage("$dir/$key").that(placeholders(value)).isEqualTo(placeholders(en.getValue(key)))
                }
            }
        }
        val en = entries("values", "plurals.xml", "plurals")
        locales.forEach { dir ->
            entries(dir, "plurals.xml", "plurals").forEach { (key, items) ->
                assertWithMessage("$dir/$key").that(placeholders(items)).isEqualTo(placeholders(en.getValue(key)))
            }
        }
    }

    @Test
    fun `no value carries an escape CMP would show literally`() {
        (listOf("values") + locales).forEach { dir ->
            listOf("strings.xml", "strings_kit.xml", "plurals.xml").forEach { file ->
                val text = File("$res/$dir/$file").readText()
                assertWithMessage("$dir/$file").that(text).doesNotContain("\\'")
                assertWithMessage("$dir/$file").that(text).doesNotContain("\\\"")
            }
        }
    }
}

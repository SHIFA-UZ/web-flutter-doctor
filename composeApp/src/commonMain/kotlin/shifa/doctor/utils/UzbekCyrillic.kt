package shifa.doctor.utils

/**
 * Uzbek Latin → Cyrillic for UI strings (CORE-04; port of `lib/core/localization/uzbek_latin_to_cyrillic.dart`).
 * Placeholders (`%1$s`, `%d`, `{x}`, `{{x}}`) pass through untouched. On top of the Dart mapping it follows the
 * orthography for `ya/yu/yo/ye` (я/ю/ё/е; `ye` after a consonant is ъе), word-initial `e` (э) and the letter-between apostrophe (ъ, "ma'lumot" →
 * "маълумот"). `ts` stays тс: Cyrillic ц appears only in loanwords the Latin copy can't mark.
 */
object UzbekCyrillic {
    private val placeholder = Regex("""%\d+\$[sd]|%[sd]|\{\{[^}]+\}\}|\{[^}]+\}""")

    fun fromLatin(input: String): String {
        if (input.isEmpty()) return input
        val out = StringBuilder()
        var start = 0
        placeholder.findAll(input).forEach { m ->
            out.append(segment(input.substring(start, m.range.first)))
            out.append(m.value)
            start = m.range.last + 1
        }
        out.append(segment(input.substring(start)))
        return out.toString()
    }

    private fun isApostrophe(c: Char?) = c == '\'' || c == '’' || c == 'ʼ' || c == '‘' || c == 'ʻ'

    private val single =
        mapOf(
            'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "г", 'h' to "ҳ",
            'i' to "и", 'j' to "ж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п",
            'q' to "қ", 'r' to "р", 's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'w' to "в", 'x' to "х",
            'y' to "й", 'z' to "з",
        )

    private const val CONSONANTS = "bcdfghjklmnpqrstvwxz"

    private val pairs = mapOf("sh" to "ш", "ch" to "ч", "ya" to "я", "yu" to "ю", "ye" to "е")

    // Case follows the Latin: "Sh" → "Ш", "SH" → "Ш" (a one-letter result), "sh" → "ш".
    private fun cased(
        latin: String,
        cyr: String,
    ): String =
        when {
            latin.all { !it.isLetter() || it.isUpperCase() } && latin.any { it.isUpperCase() } -> cyr.uppercase()
            latin.first().isUpperCase() -> cyr.replaceFirstChar { it.uppercase() }
            else -> cyr
        }

    private fun segment(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val lc = c.lowercaseChar()
            val next = s.getOrNull(i + 1)
            val wordStart = i == 0 || !s[i - 1].isLetter() && !isApostrophe(s[i - 1])
            when {
                // o' / g' (any apostrophe form) → ў / ғ
                (lc == 'o' || lc == 'g') && isApostrophe(next) -> {
                    sb.append(cased(c.toString(), if (lc == 'o') "ў" else "ғ"))
                    i += 2
                }
                // yo' is й + ў ("yo'q" → "йўқ"); plain yo → ё
                lc == 'y' && next?.lowercaseChar() == 'o' && !isApostrophe(s.getOrNull(i + 2)) -> {
                    sb.append(cased(s.substring(i, i + 2), "ё"))
                    i += 2
                }
                // After a consonant the iotated e takes the hard sign: "obyekt" → "объект", "subyektiv" → "субъектив".
                lc == 'y' && next?.lowercaseChar() == 'e' && i > 0 && s[i - 1].lowercaseChar() in CONSONANTS -> {
                    sb.append("ъ").append(cased(s.substring(i, i + 2), "е"))
                    i += 2
                }
                next != null && pairs.containsKey("$lc${next.lowercaseChar()}") -> {
                    sb.append(cased(s.substring(i, i + 2), pairs.getValue("$lc${next.lowercaseChar()}")))
                    i += 2
                }
                isApostrophe(c) && i > 0 && s[i - 1].isLetter() && next?.isLetter() == true -> {
                    sb.append("ъ")
                    i++
                }
                lc == 'e' && wordStart -> {
                    sb.append(cased(c.toString(), "э"))
                    i++
                }
                single.containsKey(lc) -> {
                    sb.append(cased(c.toString(), single.getValue(lc)))
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}

package shifa.doctor.utils

/**
 * Phone numbers as the backend stores them (E.164, CORE-09). Uzbekistan is the default country: a 9-digit local
 * number ("90 123 45 67") gets +998, and "998…" without the plus gets it too. Anything else must already carry its
 * country code. Null when it can't be a phone number (7–15 digits after the country code rules).
 */
object PhoneE164 {
    private const val UZ = "998"
    private const val UZ_LOCAL_DIGITS = 9

    fun normalize(input: String?): String? {
        val text = input?.trim() ?: return null
        // Only digits and the usual separators, with at most one leading "+": "abc90…" and "++998…" are not numbers.
        if (!Regex("""\+?[0-9 ()\-.]+""").matches(text)) return null
        val plus = text.startsWith("+") || text.startsWith("00")
        val digits = text.filter(Char::isDigit).let { if (text.startsWith("00")) it.removePrefix("00") else it }
        val full =
            when {
                plus -> digits
                digits.length == UZ_LOCAL_DIGITS -> UZ + digits
                digits.startsWith(UZ) && digits.length == UZ.length + UZ_LOCAL_DIGITS -> digits
                else -> return null
            }
        if (full.startsWith(UZ) && full.length != UZ.length + UZ_LOCAL_DIGITS) return null
        return if (full.length in 8..15 && full[0] != '0') "+$full" else null
    }

    /** "+998901234567" (or "998901234567") → "+998 90 123 45 67"; other countries and non-numbers stay as given. */
    fun display(raw: String): String {
        val e164 = normalize(raw) ?: return raw
        if (!e164.startsWith("+$UZ") || e164.length != 13) return e164
        val d = e164.removePrefix("+$UZ")
        return "+$UZ ${d.take(2)} ${d.substring(2, 5)} ${d.substring(5, 7)} ${d.substring(7)}"
    }
}

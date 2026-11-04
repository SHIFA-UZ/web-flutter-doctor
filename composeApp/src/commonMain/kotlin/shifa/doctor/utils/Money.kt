package shifa.doctor.utils

import kotlin.math.abs

/**
 * The one money formatter (CORE-09; flow 09 README "Money formatting", 09 §8). Amounts are integers in minor units
 * (1/100) with a currency code. UZS shows whole units, never tiyin: "1 250 000 UZS". Other currencies show two
 * decimals: "1 250.50 USD". Thousands are grouped with a narrow no-break space (the spec's thin space, but one a line
 * can't break inside); the currency follows after a no-break space. Zero is "0 UZS"; "—" is for "no value", not here.
 */
object Money {
    private const val GROUP = ' '
    private const val NBSP = ' '
    private const val MINUS = '−'

    fun format(
        minor: Long,
        currency: String,
    ): String {
        val code = currency.uppercase()
        val sign = if (minor < 0) MINUS.toString() else ""
        // Quotient and remainder of the magnitude, computed without abs() (abs(Long.MIN_VALUE) stays negative).
        val whole = abs(minor / 100)
        val cents = abs(minor % 100)
        val number =
            if (code == "UZS") {
                group(if (cents >= 50) whole + 1 else whole) // tiyin are never shown: round to the nearest sum
            } else {
                "${group(whole)}.${cents.toString().padStart(2, '0')}"
            }
        return "$sign$number$NBSP$code"
    }

    /**
     * An amount the doctor typed ("1 250 000", "1250000", "1250.50", "1250,5") to minor units; null when it isn't one.
     * UZS takes whole units only.
     */
    fun parse(
        text: String,
        currency: String,
    ): Long? {
        val clean = text.filterNot { it.isWhitespace() || it == GROUP || it == NBSP }.replace(',', '.')
        if (clean.none(
                Char::isDigit,
            ) || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }
        ) {
            return null
        }
        val whole = clean.substringBefore('.').ifEmpty { "0" }
        val fraction = clean.substringAfter('.', "")
        if (fraction.length > 2 || (currency.equals("UZS", ignoreCase = true) && fraction.isNotEmpty())) return null
        val units = whole.toLongOrNull() ?: return null
        val cents = fraction.padEnd(2, '0').toLong()
        if (units > (Long.MAX_VALUE - cents) / 100) return null
        return units * 100 + cents
    }

    private fun group(value: Long): String =
        value.toString().reversed().chunked(
            3,
        ).joinToString(GROUP.toString()).reversed()
}

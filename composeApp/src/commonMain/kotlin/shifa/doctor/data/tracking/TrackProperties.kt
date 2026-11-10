package shifa.doctor.data.tracking

import kotlin.math.abs

/**
 * Property values as they are sent: `String`, `Int`, `Long`, `Double`, `Boolean`, an enum as its [trackName], or a
 * list of those. A null is left out (never the text "null"), and so is any other type: an object's `toString()` can
 * hold a name.
 */
fun trackProperties(pairs: Iterable<Pair<String, Any?>>): Map<String, Any> =
    buildMap { pairs.forEach { (key, value) -> value.trackValue()?.let { put(key, it) } } }

private fun Any?.trackValue(): Any? =
    when (this) {
        is List<*> -> mapNotNull { it.trackScalar() }
        else -> trackScalar()
    }

private fun Any?.trackScalar(): Any? =
    when (this) {
        is String, is Int, is Long, is Double, is Boolean -> this
        is Enum<*> -> trackName()
        else -> null
    }

/** An enum constant as a property value: `PinLockout` → `pin_lockout`, `CARD_EXTERNAL` → `card_external`. */
fun Enum<*>.trackName(): String =
    buildString {
        name.forEachIndexed { i, c ->
            if (c.isUpperCase() && i > 0 && (name[i - 1].isLowerCase() || name[i - 1].isDigit())) append('_')
            append(c.lowercaseChar())
        }
    }

/** A count as a range, for lists whose exact size says nothing more (patients, results, unread). */
fun countBucket(count: Int): String =
    when {
        count <= 0 -> "0"
        count == 1 -> "1"
        count <= 5 -> "2-5"
        count <= 20 -> "6-20"
        count <= 100 -> "21-100"
        else -> ">100"
    }

/** A duration as a range (a call, a recording): the exact seconds say nothing more. */
fun durationBucket(seconds: Long): String =
    when {
        seconds < 60 -> "<1m"
        seconds < 5 * 60 -> "1-5m"
        seconds < 15 * 60 -> "5-15m"
        seconds < 30 * 60 -> "15-30m"
        seconds < 60 * 60 -> "30-60m"
        else -> ">60m"
    }

/**
 * Money as a range (D2: never the exact amount). [minor] is the amount in minor units, as `utils/Money` takes it.
 * The ranges are in sums; another currency is only `foreign` (send the currency code beside it when it matters).
 * Null when there is no amount, so the property is left out.
 */
fun amountBucket(
    minor: Long?,
    currency: String,
): String? {
    if (minor == null) return null
    if (!currency.equals("UZS", ignoreCase = true)) return "foreign"
    val sums = abs(minor / 100)
    return when {
        sums == 0L -> "0"
        sums < 100_000 -> "<100k"
        sums < 500_000 -> "100k-500k"
        sums < 1_000_000 -> "500k-1m"
        sums < 5_000_000 -> "1m-5m"
        sums < 20_000_000 -> "5m-20m"
        else -> ">20m"
    }
}

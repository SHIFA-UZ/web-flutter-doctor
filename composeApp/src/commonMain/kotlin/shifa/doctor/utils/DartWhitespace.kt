package shifa.doctor.utils

/**
 * Dart's (ECMAScript) `\s` as a regex class: ASCII whitespace plus the Unicode spaces (no-break space and the like).
 * Kotlin's `\s` is ASCII-only on both platforms, so patterns ported from Dart use this instead.
 */
const val DART_WS = "[\\s   -     　﻿]"

package shifa.doctor.data.auth

import shifa.doctor.utils.currentInstant

/**
 * The 60 s resend wait per e-mail address and purpose (flow 01/04 §5; the backend keeps sign-in and reset codes
 * apart), kept while the app runs so going back and asking again within the minute doesn't send a second code. In
 * memory only: a restart may send again, which the backend's own rate limit (10 a minute) still bounds.
 */
class OtpCooldown(
    private val nowMs: () -> Long = { currentInstant().toEpochMilliseconds() },
) {
    private val until = mutableMapOf<String, Long>()

    // SMS sign-in: the masked e-mail the last code went to instead of the phone (null: by SMS).
    private val hints = mutableMapOf<String, String?>()

    /** Where the code still running for [email] went ([start]'s hint); null once it may be sent again. */
    fun hint(
        email: String,
        purpose: String,
    ): String? = if (secondsLeft(email, purpose) > 0) hints[key(email, purpose)] else null

    /** Whole seconds left before [email] may get another code for [purpose]; 0 when it may. */
    fun secondsLeft(
        email: String,
        purpose: String,
    ): Int {
        val left = ((until[key(email, purpose)] ?: 0L) - nowMs()).coerceAtLeast(0L)
        return ((left + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()
    }

    fun start(
        email: String,
        purpose: String,
        hint: String? = null,
    ) {
        until[key(email, purpose)] = nowMs() + COOLDOWN_MS
        hints[key(email, purpose)] = hint
    }

    private fun key(
        email: String,
        purpose: String,
    ) = "$purpose:${email.trim().lowercase()}"

    private companion object {
        const val COOLDOWN_MS = 60_000L
        const val MS_PER_SECOND = 1_000L
    }
}

val otpCooldown by lazy { OtpCooldown() }

package shifa.doctor.utils

/**
 * The backend's password rule (`PasswordPolicy.validate`, CORE-09): 8–128 characters with an upper-case letter, a
 * lower-case letter, a digit and a special character. The form shows which [Rule]s are still missing.
 */
object PasswordPolicy {
    const val MIN = 8
    const val MAX = 128

    enum class Rule { Length, Upper, Lower, Digit, Special }

    // The backend's own patterns, so the app never accepts what the server refuses ("Ä" isn't upper-case there).
    private val upper = Regex("[A-Z]")
    private val lower = Regex("[a-z]")
    private val digit = Regex("[0-9]")
    private val special = Regex("[^A-Za-z0-9\\s]")

    fun missing(password: String): Set<Rule> =
        buildSet {
            if (password.length !in MIN..MAX) add(Rule.Length)
            if (!upper.containsMatchIn(password)) add(Rule.Upper)
            if (!lower.containsMatchIn(password)) add(Rule.Lower)
            if (!digit.containsMatchIn(password)) add(Rule.Digit)
            if (!special.containsMatchIn(password)) add(Rule.Special)
        }

    fun isValid(password: String) = missing(password).isEmpty()
}

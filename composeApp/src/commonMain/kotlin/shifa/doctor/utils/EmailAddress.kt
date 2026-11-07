package shifa.doctor.utils

/** How the app writes an e-mail address before checking or sending it: trimmed and lower-cased (flows 01/03, 01/07). */
fun normalizedEmail(input: String) = input.trim().lowercase()

/** Something@something.something: the app's only e-mail check; the backend decides the rest. */
fun isEmailAddress(email: String) = EMAIL.matches(email)

private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

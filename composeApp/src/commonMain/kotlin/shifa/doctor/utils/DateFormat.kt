package shifa.doctor.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// The app's date and time shapes (TZ-1; flow 04 README §4.2): numeric, the same in every language, always for a value
// already on the practice wall clock (`PracticeClock.local`), never the phone's.

/** "05.12.2026". */
fun displayDate(date: LocalDate) = "${two(date.dayOfMonth)}.${two(date.monthNumber)}.${date.year}"

/** "09:05", 24-hour. */
fun displayTime(time: LocalTime) = "${two(time.hour)}:${two(time.minute)}"

/** [minutes] after midnight as "HH:mm" (wraps past a day), for slot and shift labels. */
fun displayMinutes(minutes: Int): String {
    val m = minutes.mod(24 * 60)
    return "${two(m / 60)}:${two(m % 60)}"
}

private fun two(n: Int) = n.toString().padStart(2, '0')

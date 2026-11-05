package shifa.doctor.data.chat

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import shifa.doctor.data.model.Conversation
import shifa.doctor.utils.PracticeClock

/** The list's filter menu (flow 07/01 §3): newest or oldest first, everyone or only conversations with unread. */
enum class ConversationSort(
    val unreadOnly: Boolean,
    val oldestFirst: Boolean,
) {
    Newest(false, false),
    Oldest(false, true),
    UnreadNewest(true, false),
    UnreadOldest(true, true),
}

/** The list as the menu asks; a conversation without activity sorts as the oldest (flow 07/01 §4). */
fun List<Conversation>.sortedFor(sort: ConversationSort): List<Conversation> {
    val by = compareBy<Conversation> { it.lastActivity ?: Instant.DISTANT_PAST }.thenBy { it.id }
    return filter { !sort.unreadOnly || it.unread > 0 }.sortedWith(if (sort.oldestFirst) by else by.reversed())
}

/** When a conversation was last active (flow 07/01 §4 target): today "HH:mm", yesterday, else a date; the practice's day. */
sealed interface ActivityTime {
    data class Today(
        val hm: String,
    ) : ActivityTime

    data object Yesterday : ActivityTime

    data class Day(
        val day: Int,
        val month: Int,
    ) : ActivityTime
}

fun activityTime(
    at: Instant,
    clock: PracticeClock,
): ActivityTime {
    val local = clock.local(at)
    val today = clock.local(clock.nowInstant()).date
    return when (local.date) {
        today ->
            ActivityTime.Today(
                "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}",
            )
        today.minus(DatePeriod(days = 1)) -> ActivityTime.Yesterday
        else -> ActivityTime.Day(local.dayOfMonth, local.monthNumber)
    }
}

/** The unread pill: capped at "99+" (flow 07/01 §4). */
fun unreadLabel(n: Int) = if (n > 99) "99+" else n.toString()

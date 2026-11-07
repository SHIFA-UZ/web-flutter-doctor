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

/** A row of the thread: a day divider, or a message (sent or still on its way). */
sealed interface ThreadRow {
    data class Day(
        val date: kotlinx.datetime.LocalDate,
        // Which divider this is, top down: the same day can come twice (an old failed text after today's messages).
        val ordinal: Int = 0,
    ) : ThreadRow

    data class Sent(
        val message: shifa.doctor.data.model.Message,
    ) : ThreadRow

    class Pending(
        val localId: Long,
        val text: String,
        val at: Instant,
        val failed: Boolean,
        // A photo on its way (CHAT-04).
        val image: ByteArray? = null,
        // A document on its way (CHAT-05): its name and size.
        val documentName: String? = null,
        val documentSize: Long? = null,
        // A voice note on its way (CHAT-06): its length.
        val voiceSeconds: Int? = null,
    ) : ThreadRow
}

/**
 * The thread oldest first with a divider before each new practice day (flow 07/02 §3 target: Flutter had none), the
 * texts still on their way last.
 */
fun threadRows(
    messages: List<shifa.doctor.data.model.Message>,
    pending: List<ThreadRow.Pending>,
    clock: PracticeClock,
): List<ThreadRow> {
    val rows = mutableListOf<ThreadRow>()
    var day: kotlinx.datetime.LocalDate? = null

    fun dayOf(at: Instant) {
        val d = clock.local(at).date
        if (d != day) {
            rows += ThreadRow.Day(d, rows.count { it is ThreadRow.Day })
            day = d
        }
    }
    messages.forEach {
        dayOf(it.sentAt)
        rows += ThreadRow.Sent(it)
    }
    pending.forEach {
        dayOf(it.at)
        rows += it
    }
    return rows
}

/** A file's size (flow 07/06 §4): "812 B", "812.0 KB", "2.4 MB" (one decimal). */
fun fileSizeLabel(bytes: Long): String {
    fun oneDecimal(v: Double): String {
        val tenths = kotlin.math.round(v * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
        else -> "${oneDecimal(bytes / (1024.0 * 1024.0))} MB"
    }
}

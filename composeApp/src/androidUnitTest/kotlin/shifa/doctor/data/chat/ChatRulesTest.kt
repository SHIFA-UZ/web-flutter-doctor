package shifa.doctor.data.chat

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.model.Conversation
import shifa.doctor.utils.PracticeClock

class ChatRulesTest {
    private fun c(
        id: Long,
        at: String?,
        unread: Int = 0,
    ) = Conversation(id, id, "P$id", null, false, null, at?.let { Instant.parse(it) }, unread)

    private val list =
        listOf(
            c(1, "2026-11-05T05:00:00Z", 2),
            c(2, "2026-11-04T05:00:00Z"),
            c(3, null, 1),
            c(4, "2026-11-05T06:00:00Z"),
        )

    @Test
    fun `newest or oldest first, unread only, no activity sorts as the oldest`() {
        assertThat(list.sortedFor(ConversationSort.Newest).map { it.id }).containsExactly(4L, 1L, 2L, 3L).inOrder()
        assertThat(list.sortedFor(ConversationSort.Oldest).map { it.id }).containsExactly(3L, 2L, 1L, 4L).inOrder()
        assertThat(list.sortedFor(ConversationSort.UnreadNewest).map { it.id }).containsExactly(1L, 3L).inOrder()
        assertThat(list.sortedFor(ConversationSort.UnreadOldest).map { it.id }).containsExactly(3L, 1L).inOrder()
    }

    @Test
    fun `today a time, yesterday, else a date, all on the practice's day`() {
        // 00:30 on 6 Nov in Tashkent (19:30 UTC on the 5th).
        val clock = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T19:30:00Z") })
        assertThat(activityTime(Instant.parse("2026-11-05T19:10:00Z"), clock)).isEqualTo(ActivityTime.Today("00:10"))
        assertThat(activityTime(Instant.parse("2026-11-05T05:00:00Z"), clock)).isEqualTo(ActivityTime.Yesterday)
        assertThat(activityTime(Instant.parse("2026-11-01T05:00:00Z"), clock)).isEqualTo(ActivityTime.Day(1, 11))
    }

    @Test
    fun `the pill caps at 99+`() {
        assertThat(unreadLabel(7)).isEqualTo("7")
        assertThat(unreadLabel(99)).isEqualTo("99")
        assertThat(unreadLabel(100)).isEqualTo("99+")
    }
}

@file:Suppress("ktlint:standard:max-line-length")

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

    @Test
    fun `a divider before each new practice day, texts on their way last`() {
        val clock = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T19:30:00Z") })

        fun m(
            id: Long,
            at: String,
        ) = shifa.doctor.data.model.Message(
            id, 7, 1, false, shifa.doctor.data.model.MessageType.Text, "t", null, null, null, null, shifa.doctor.data.model.MessageStatus.Sent, false,
            Instant.parse(
                at,
            ),
        )
        // 18:50 UTC on the 4th is 23:50 in Tashkent; 19:10 UTC is 00:10 on the 5th there.
        val rows =
            threadRows(
                listOf(m(1, "2026-11-04T18:50:00Z"), m(2, "2026-11-04T19:10:00Z"), m(3, "2026-11-05T08:00:00Z")),
                listOf(ThreadRow.Pending(1, "p", Instant.parse("2026-11-05T19:20:00Z"), false)),
                clock,
            )
        assertThat(rows.map { (it as? ThreadRow.Day)?.date?.toString() ?: it::class.simpleName })
            .containsExactly("2026-11-04", "Sent", "2026-11-05", "Sent", "Sent", "2026-11-06", "Pending").inOrder()
    }

    @Test
    fun `an old failed text after today's messages gets its own divider, never a repeated one`() {
        val clock = PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T09:00:00Z") })
        val today =
            shifa.doctor.data.model.Message(
                1, 7, 1, false, shifa.doctor.data.model.MessageType.Text, "t", null, null, null, null, shifa.doctor.data.model.MessageStatus.Sent, false,
                Instant.parse(
                    "2026-11-05T08:00:00Z",
                ),
            )
        val rows =
            threadRows(
                listOf(today),
                listOf(ThreadRow.Pending(1, "old", Instant.parse("2026-11-04T08:00:00Z"), true)),
                clock,
            )
        val days = rows.filterIsInstance<ThreadRow.Day>()
        assertThat(days.map { it.ordinal }).containsExactly(0, 1).inOrder()
        assertThat(days.map { it.ordinal }.toSet()).hasSize(days.size)
    }
}

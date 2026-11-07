@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.home

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import shifa.doctor.data.model.CalendarEntry

class HomeRulesTest {
    private fun at(t: String) = Instant.parse("2026-11-05T$t:00Z")

    private fun visit(
        id: Long,
        start: String,
        end: String,
        status: String = "CONFIRMED",
    ) = CalendarEntry.Appointment(at(start), at(end), id, id, "P$id", null, status, null, "", "Clinic")

    private fun schedule(
        hero: CalendarEntry.Appointment?,
        heroDay: LocalDate? = null,
        today: List<CalendarEntry.Appointment> = listOfNotNull(hero),
    ) = HomeSchedule(today, emptyList(), LocalDate(2026, 11, 5), hero, heroDay = heroDay)

    @Test
    fun `Start opens 15 minutes before, the pill counts down within the hour`() {
        val s = schedule(visit(1, "05:00", "05:30"))
        val early = heroLook(s, at("04:40"))!!
        assertThat(early.canStart).isFalse()
        assertThat(early.opensAt).isEqualTo(at("04:45"))
        assertThat(early.pill).isEqualTo(HeroPill.Soon)
        assertThat(early.minutes).isEqualTo(20)
        val open = heroLook(s, at("04:45"))!!
        assertThat(open.canStart).isTrue()
        assertThat(open.opensAt).isNull()
        assertThat(heroLook(s, at("05:05"))!!.pill).isEqualTo(HeroPill.Now)
        assertThat(heroLook(s, at("03:00"))!!.pill).isEqualTo(HeroPill.Confirmed)
    }

    @Test
    fun `a video call starts only 5 minutes before, the backend's join window`() {
        val call = visit(6, "05:00", "05:30").copy(location = "Video Consultation")
        val s = schedule(call)
        assertThat(heroLook(s, at("04:50"))!!.let { it.canStart to it.opensAt }).isEqualTo(false to at("04:55"))
        assertThat(heroLook(s, at("04:55"))!!.canStart).isTrue()
    }

    @Test
    fun `in progress continues any time, requested waits, another day never starts`() {
        assertThat(
            heroLook(schedule(visit(1, "05:00", "05:30", "IN_PROGRESS")), at("06:00"))!!.let { it.canStart to it.pill },
        )
            .isEqualTo(true to HeroPill.Now)
        val requested = heroLook(schedule(visit(2, "05:00", "05:30", "REQUESTED")), at("04:55"))!!
        assertThat(requested.canStart).isFalse()
        assertThat(requested.pill).isEqualTo(HeroPill.Waiting)
        assertThat(requested.opensAt).isNull()
        val later =
            heroLook(
                schedule(visit(3, "05:00", "05:30"), heroDay = LocalDate(2026, 11, 6), today = emptyList()),
                at("04:55"),
            )!!
        assertThat(later.canStart).isFalse()
        assertThat(later.pill).isEqualTo(HeroPill.Confirmed)
    }

    @Test
    fun `the timeline folds finished visits and past blocks, keeps one in progress`() {
        val done = visit(1, "03:00", "03:30", "COMPLETED")
        val noShow = visit(2, "03:30", "04:00", "NO_SHOW")
        val running = visit(3, "03:00", "03:30", "IN_PROGRESS")
        val cancelled = visit(4, "05:00", "05:30", "CANCELLED")
        val next = visit(5, "06:00", "06:30")
        val pastBlock = CalendarEntry.Blocked(at("02:00"), at("02:30"), 8, "Lunch")
        val s = HomeSchedule(listOf(done, noShow, running, cancelled, next), listOf(pastBlock), LocalDate(2026, 11, 5))
        val t = homeTimeline(s, at("04:10"))
        assertThat(t.earlier).containsExactly(pastBlock, done, noShow).inOrder()
        assertThat(t.upcoming).containsExactly(running, cancelled, next).inOrder()
    }

    @Test
    fun `stale when the day turns or today's hero leaves its slot`() {
        val s = schedule(visit(1, "05:00", "05:30"))
        assertThat(s.stale(at("05:10"), LocalDate(2026, 11, 5))).isFalse()
        assertThat(s.stale(at("05:30"), LocalDate(2026, 11, 5))).isTrue()
        assertThat(s.stale(at("05:10"), LocalDate(2026, 11, 6))).isTrue()
        assertThat(
            schedule(visit(2, "05:00", "05:30", "IN_PROGRESS")).stale(at("06:00"), LocalDate(2026, 11, 5)),
        ).isFalse()
        // Running over while the next visit's slot begins: that one takes over.
        val over = visit(3, "05:00", "05:30", "IN_PROGRESS")
        val next = visit(4, "06:00", "06:30")
        assertThat(schedule(over, today = listOf(over, next)).stale(at("06:05"), LocalDate(2026, 11, 5))).isTrue()
    }
}

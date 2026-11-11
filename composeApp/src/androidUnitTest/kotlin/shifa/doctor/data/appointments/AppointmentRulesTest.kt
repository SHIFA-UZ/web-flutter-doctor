package shifa.doctor.data.appointments

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.utils.PracticeClock

// Practice zone Tashkent (UTC+5). Times below are UTC.
class AppointmentRulesTest {
    private fun at(iso: String) = Instant.parse(iso)

    private fun clock(now: String) = PracticeClock("Asia/Tashkent", { at(now) }, { TimeZone.of("Asia/Tashkent") })

    private fun appt(
        start: String,
        end: String,
        status: String = "CONFIRMED",
        video: Boolean = false,
        locationId: Long? = 1,
    ) = CalendarEntry.Appointment(
        at(start),
        at(end),
        1,
        2,
        "P",
        null,
        status,
        null,
        "",
        if (video) "Video Consultation" else "Clinic",
        locationId,
    )

    @Test
    fun `the main button follows status first`() {
        val now = clock("2026-11-04T05:00:00Z")
        assertThat(
            ctaFor(appt("2026-11-04T04:00:00Z", "2026-11-04T04:30:00Z", "COMPLETED"), now),
        ).isEqualTo(Cta(CtaKind.OpenSummary, true))
        assertThat(
            ctaFor(appt("2026-11-04T04:00:00Z", "2026-11-04T04:30:00Z", "IN_PROGRESS"), now),
        ).isEqualTo(Cta(CtaKind.Continue, true))
        assertThat(ctaFor(appt("2026-11-04T06:00:00Z", "2026-11-04T06:30:00Z", "CANCELLED"), now)).isNull()
        assertThat(ctaFor(appt("2026-11-04T04:00:00Z", "2026-11-04T04:30:00Z", "NO_SHOW"), now)).isNull()
    }

    @Test
    fun `a video visit opens 5 minutes before and closes an hour after`() {
        val v = appt("2026-11-04T06:00:00Z", "2026-11-04T06:30:00Z", video = true)
        assertThat(ctaFor(v, clock("2026-11-04T05:54:00Z"))).isEqualTo(Cta(CtaKind.Start, false, CtaHint.VideoTooEarly))
        assertThat(ctaFor(v, clock("2026-11-04T05:55:00Z"))).isEqualTo(Cta(CtaKind.Start, true))
        assertThat(ctaFor(v, clock("2026-11-04T07:29:00Z"))).isEqualTo(Cta(CtaKind.Start, true))
        assertThat(ctaFor(v, clock("2026-11-04T07:30:00Z"))).isEqualTo(Cta(CtaKind.Ended, false, CtaHint.VideoEnded))
    }

    @Test
    fun `an in-person visit can still start the same practice day, not the next`() {
        // 23:00–23:30 Tashkent on Nov 3 (18:00–18:30 UTC).
        val late = appt("2026-11-03T18:00:00Z", "2026-11-03T18:30:00Z")
        assertThat(ctaFor(late, clock("2026-11-03T18:50:00Z"))).isEqualTo(Cta(CtaKind.Start, true)) // 23:50 same day
        assertThat(
            ctaFor(late, clock("2026-11-03T19:30:00Z")),
        ).isEqualTo(Cta(CtaKind.Ended, false, CtaHint.Past)) // 00:30 next day
    }

    @Test
    fun `durations step back by the slot length and forward by the free slots that follow`() {
        val a = appt("2026-11-04T05:00:00Z", "2026-11-04T06:00:00Z") // 60 min
        val day =
            listOf(
                CalendarEntry.FreeSlot(at("2026-11-04T06:00:00Z"), at("2026-11-04T06:30:00Z"), 1),
                CalendarEntry.FreeSlot(at("2026-11-04T06:30:00Z"), at("2026-11-04T07:00:00Z"), 1),
                CalendarEntry.FreeSlot(at("2026-11-04T07:30:00Z"), at("2026-11-04T08:00:00Z"), 1), // gap before it
                CalendarEntry.FreeSlot(at("2026-11-04T06:00:00Z"), at("2026-11-04T06:15:00Z"), 2), // another location
            )
        assertThat(durationOptions(a, day)).containsExactly(30, 60, 90, 120).inOrder()
        assertThat(durationOptions(a, emptyList())).containsExactly(60)
    }

    @Test
    fun `the shortening step is the GCD of the slot lengths, never under 5 minutes`() {
        val a = appt("2026-11-04T05:00:00Z", "2026-11-04T06:00:00Z") // 60 min
        val day =
            listOf(
                CalendarEntry.FreeSlot(at("2026-11-04T07:00:00Z"), at("2026-11-04T07:20:00Z"), 1),
                CalendarEntry.FreeSlot(at("2026-11-04T08:00:00Z"), at("2026-11-04T08:30:00Z"), 1),
            )
        assertThat(durationOptions(a, day)).containsExactly(10, 20, 30, 40, 50, 60).inOrder()
        val tiny = listOf(CalendarEntry.FreeSlot(at("2026-11-04T07:00:00Z"), at("2026-11-04T07:01:00Z"), 1))
        assertThat(durationOptions(a, tiny).first()).isEqualTo(5)
    }

    @Test
    fun `a move fits only where free slots cover the whole length`() {
        val day =
            listOf(
                CalendarEntry.FreeSlot(at("2026-11-04T05:00:00Z"), at("2026-11-04T05:30:00Z"), 1),
                CalendarEntry.FreeSlot(at("2026-11-04T05:30:00Z"), at("2026-11-04T06:00:00Z"), 1),
                CalendarEntry.FreeSlot(at("2026-11-04T07:00:00Z"), at("2026-11-04T07:30:00Z"), 1),
            )
        assertThat(fits(day[0] as CalendarEntry.FreeSlot, day, 60)).isTrue()
        assertThat(fits(day[1] as CalendarEntry.FreeSlot, day, 60)).isFalse()
        assertThat(fits(day[2] as CalendarEntry.FreeSlot, day, 30)).isTrue()
    }

    @Test
    fun `no-show is offered from the start of a confirmed visit until a day after its end`() {
        val a = appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z")
        assertThat(a.mayMarkNoShow(at("2026-11-04T04:59:00Z"))).isFalse()
        assertThat(a.mayMarkNoShow(at("2026-11-04T05:00:00Z"))).isTrue()
        assertThat(a.mayMarkNoShow(at("2026-11-05T05:30:00Z"))).isTrue()
        assertThat(a.mayMarkNoShow(at("2026-11-05T05:31:00Z"))).isFalse()
        // Started (the call): the rule allows it; the calendar's banner is for a confirmed visit only.
        assertThat(mayMarkNoShow("IN_PROGRESS", a.start, a.end, at("2026-11-04T05:10:00Z"))).isTrue()
        assertThat(
            appt(
                "2026-11-04T05:00:00Z",
                "2026-11-04T05:30:00Z",
                status = "IN_PROGRESS",
            ).mayMarkNoShow(at("2026-11-04T05:10:00Z")),
        ).isFalse()
        assertThat(
            appt(
                "2026-11-04T05:00:00Z",
                "2026-11-04T05:30:00Z",
                status = "COMPLETED",
            ).mayMarkNoShow(at("2026-11-04T05:10:00Z")),
        ).isFalse()
    }

    @Test
    fun `the backend's video flag wins over the booked location (BE-09)`() {
        val inClinicByWord = appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z", video = true).copy(videoFlag = false)
        assertThat(inClinicByWord.isVideo).isFalse()
        assertThat(appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z").copy(videoFlag = true).isVideo).isTrue()
        // A backend without the flag: the word.
        assertThat(appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z", video = true).isVideo).isTrue()
    }

    @Test
    fun `the payment chip shows for video and for any in-person visit with a payment`() {
        val clinic = appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z")
        val video = appt("2026-11-04T05:00:00Z", "2026-11-04T05:30:00Z", video = true)
        // Booked by the doctor: no payment, no chip.
        assertThat(clinic.copy(paymentStatus = "NOT_REQUIRED").showsPayment).isFalse()
        assertThat(clinic.showsPayment).isFalse()
        // Prepaid online, waiting for payment, or paid in clinic.
        assertThat(listOf("PAID", "PENDING", "PAY_IN_CLINIC").map { clinic.copy(paymentStatus = it).showsPayment })
            .containsExactly(true, true, true)
        assertThat(video.copy(paymentStatus = "NOT_REQUIRED").showsPayment).isTrue()
    }
}

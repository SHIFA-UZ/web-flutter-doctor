@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.data.schedule

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.RuleDto

class ScheduleRulesTest {
    private fun t(s: String) = LocalTime.parse(s)

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `rules round-trip through the weekly template, sorted, bad rows skipped, seconds kept`() {
        val rules =
            listOf(
                RuleDto(1, "14:00", "18:00", 30, 2),
                RuleDto(1, "09:00", "13:00", 30, 2),
                RuleDto(3, "10:00", "12:00", 15, 2),
                RuleDto(9, "10:00", "12:00", 15, 2), // no such weekday
                RuleDto(4, "late", "12:00", 15, 2),
                RuleDto(5, "09:00garbage", "12:00", 15, 2), // the whole value must parse
                RuleDto(6, "09:00:30", "12:00", 15, 2), // seconds kept as read
                RuleDto(7, "09:00:00.500", "12:00", 15, 2), // and fractions
            )
        val week = WeeklySchedule.of(rules)
        assertThat(week.ranges(1).map { it.start }).containsExactly(t("09:00"), t("14:00")).inOrder()
        assertThat(week.days.keys).containsExactly(1, 3, 6, 7)
        assertThat(week.toRules(2)).containsExactly(
            RuleDto(1, "09:00", "13:00", 30, 2),
            RuleDto(1, "14:00", "18:00", 30, 2),
            RuleDto(3, "10:00", "12:00", 15, 2),
            RuleDto(6, "09:00:30", "12:00", 15, 2),
            RuleDto(7, "09:00:00.500", "12:00", 15, 2),
        ).inOrder()
        assertThat(WeeklySchedule.of(week.toRules(2))).isEqualTo(week)
        assertThat(week.with(3, emptyList()).days.keys).containsExactly(1, 6, 7) // a day without ranges is off
    }

    @Test
    fun `a range is checked like the backend - end after start, slots of 5 or more, at most 200, no overlap`() {
        val morning = TimeRange(t("09:00"), t("13:00"), 30)
        assertThat(
            rangeProblem(TimeRange(t("13:00"), t("13:00"), 30), emptyList()),
        ).isEqualTo(RangeProblem.EndNotAfterStart)
        assertThat(
            rangeProblem(TimeRange(t("12:00"), t("14:00"), 30), listOf(morning)),
        ).isEqualTo(RangeProblem.Overlaps)
        assertThat(rangeProblem(TimeRange(t("13:00"), t("17:00"), 30), listOf(morning))).isNull() // touching is fine
        assertThat(rangeProblem(TimeRange(t("09:00"), t("10:00"), 0), emptyList())).isEqualTo(RangeProblem.SlotTooShort)
        assertThat(rangeProblem(TimeRange(t("00:00"), t("23:00"), 5), emptyList())).isEqualTo(RangeProblem.TooManySlots)
        assertThat(rangeProblem(TimeRange(t("00:00"), t("16:40"), 5), emptyList())).isNull() // exactly 200
    }

    @Test
    fun `a new period fits before, after or inside an existing one, never across`() {
        val existing = listOf(d("2026-09-30") to d("2027-10-30"))
        assertThat(periodFits(d("2026-06-01"), d("2026-09-29"), existing)).isTrue() // before
        assertThat(periodFits(d("2027-10-31"), d("2027-12-31"), existing)).isTrue() // after
        assertThat(periodFits(d("2026-11-01"), d("2027-01-31"), existing)).isTrue() // inside: nothing changes
        assertThat(periodFits(d("2027-10-01"), d("2027-12-31"), existing)).isFalse() // across the end
        assertThat(periodFits(d("2027-12-31"), d("2027-10-31"), existing)).isFalse() // reversed
    }

    @Test
    fun `the repository scopes by location, reads rows on their own and always sends the start date`() =
        runBlocking<Unit> {
            val sent = mutableListOf<String>()
            val engine =
                MockEngine { req ->
                    sent += "${req.method.value} ${req.url.encodedPath}?${req.url.encodedQuery} ${req.body.toByteArray().decodeToString()}"
                    // The second row has no times and a text slot size: it is skipped, the first is read.
                    respond(
                        """[{"weekday":2,"startTime":"09:00","endTime":"17:00","slotMinutes":30,"locationId":2},{"weekday":3,"slotMinutes":"x"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = ScheduleRepository { createHttpClient({ null }, engine) }
            val week = repo.rules(2).getOrThrow()
            assertThat(week.days.keys).containsExactly(2)
            repo.saveRules(2, week)
            repo.addPeriod(null, d("2027-12-31"), today = d("2026-11-04"))
            assertThat(
                sent[1],
            ).isEqualTo(
                """PUT /api/schedule/rules?locationId=2 [{"weekday":2,"startTime":"09:00","endTime":"17:00","slotMinutes":30,"locationId":2}]""",
            )
            // No start means from today, sent explicitly (without it the backend makes a one-day period).
            assertThat(
                sent[2],
            ).isEqualTo("""PATCH /api/schedule/valid-range? {"validFrom":"2026-11-04","validUntil":"2027-12-31"}""")
        }
}

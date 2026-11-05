package shifa.doctor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import shifa.doctor.data.home.HomeSchedule
import shifa.doctor.data.home.heroLook
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.utils.PracticeClock

private val now = Instant.parse("2026-11-05T04:12:00Z")

// Practice in Tashkent, the phone in Berlin: the practice-time note shows.
private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Europe/Berlin") })

private fun visit(
    id: Long,
    start: String,
    status: String = "CONFIRMED",
    name: String = "Patient $id",
    location: String = "Shifa Demo Clinic",
    reason: String = "",
) = CalendarEntry.Appointment(
    Instant.parse(start),
    Instant.parse(start).plus(kotlin.time.Duration.parse("30m")),
    id,
    id,
    name,
    null,
    status,
    null,
    reason,
    location,
)

private val day = kotlinx.datetime.LocalDate(2026, 11, 5)

// 09:00 done, 09:10 in its slot (the hero), a video visit, a cancelled one, one more, and lunch blocked.
private val busy =
    listOf(
        visit(1, "2026-11-05T04:00:00Z", "COMPLETED", "Dilnoza Rahimova"),
        visit(2, "2026-11-05T04:10:00Z", name = "Jasur Karimov", reason = "Tooth pain, lower left"),
        visit(
            3,
            "2026-11-05T05:00:00Z",
            name = "Malika Yusupova",
            location = "Video Consultation",
            reason = "Follow-up",
        ),
        visit(4, "2026-11-05T06:00:00Z", "CANCELLED", "Bekzod Tursunov"),
        visit(5, "2026-11-05T07:30:00Z", name = "Nodira Alimova"),
    )
private val lunch =
    CalendarEntry.Blocked(
        Instant.parse("2026-11-05T08:00:00Z"),
        Instant.parse("2026-11-05T09:00:00Z"),
        9,
        "Lunch",
    )
private val busySchedule = HomeSchedule(busy, listOf(lunch), day, busy[1], heroNow = true)

private val shell = HomeShell("Dr. med. Aziza Karimova", null, unread = 3, followUps = 2, showFollowUps = true)

// HOME-02: the first read: the metrics and the next patient as skeletons.
@Composable
internal fun HomeLoadingPreview() =
    PreviewTheme { HomeContent(HomeUiState(), shell.copy(unread = null, followUps = null), HomeActions(), clock) }

// HOME-02/03: loaded: 5 visits today, 3 unread, 2 follow-up tasks; the 09:10 visit in its slot is the hero.
@Composable
internal fun HomeLoadedPreview() =
    PreviewTheme {
        HomeContent(
            HomeUiState(
                loading = false,
                schedule = busySchedule,
                heroBirth = day.minus(kotlinx.datetime.DatePeriod(years = 42)),
            ),
            shell,
            HomeActions(),
            clock,
        )
    }

// HOME-03: the hero 48 minutes away on video: Start opens at 09:45; then the timeline, the done visit folded away.
@Composable
internal fun HomeHeroLaterPreview() =
    PreviewTheme {
        val later = busySchedule.copy(today = busy.drop(2), hero = busy[2], heroNow = false)
        Column(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S20),
        ) {
            heroLook(later, now)?.let { HeroCard(later, it, null, clock, HomeActions()) }
            Timeline(busySchedule, now, clock, HomeActions())
        }
    }

// HOME-03: today's visits are all done and nothing is booked for 14 days.
@Composable
internal fun HomeDonePreview() =
    PreviewTheme {
        HomeContent(
            HomeUiState(loading = false, schedule = HomeSchedule(listOf(busy[0]), emptyList(), day)),
            shell,
            HomeActions(),
            clock,
        )
    }

// HOME-03: the profile has no practice timezone: the card asks for one.
@Composable
internal fun HomeNoZonePreview() =
    PreviewTheme {
        HomeContent(
            HomeUiState(loading = false, schedule = HomeSchedule(day = day)),
            shell.copy(profileLoaded = true, zone = null),
            HomeActions(),
            PracticeClock(null, { now }),
        )
    }

// HOME-02: 200% text: the metric cards stack, one per row.
@Composable
internal fun HomeLargeTextPreview() =
    PreviewTheme {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, 2f),
        ) {
            HomeContent(
                HomeUiState(loading = false, schedule = HomeSchedule(busy.take(2), emptyList(), day)),
                shell,
                HomeActions(),
                clock,
            )
        }
    }

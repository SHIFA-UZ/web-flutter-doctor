package shifa.doctor.ui.home

import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import shifa.doctor.data.home.HomeSchedule
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.utils.PracticeClock

private val now = Instant.parse("2026-11-05T04:12:00Z")

// Practice in Tashkent, the phone in Berlin: the practice-time note shows.
private val clock = PracticeClock("Asia/Tashkent", { now }, { TimeZone.of("Europe/Berlin") })

private fun visit(
    id: Long,
    start: String,
) = CalendarEntry.Appointment(
    Instant.parse(start),
    Instant.parse(start).plus(kotlin.time.Duration.parse("30m")),
    id,
    id,
    "Patient $id",
    null,
    "CONFIRMED",
    null,
    "",
    "Shifa Demo Clinic",
)

private val shell = HomeShell("Dr. med. Aziza Karimova", null, unread = 3, followUps = 2, showFollowUps = true)

// HOME-02: the first read: the metrics and the next patient as skeletons.
@Composable
internal fun HomeLoadingPreview() =
    PreviewTheme { HomeContent(HomeUiState(), shell.copy(unread = null, followUps = null), HomeActions(), clock) }

// HOME-02: loaded: 6 visits today, 3 unread, 2 follow-up tasks.
@Composable
internal fun HomeLoadedPreview() =
    PreviewTheme {
        HomeContent(
            HomeUiState(
                loading = false,
                schedule = HomeSchedule((1L..6L).map { visit(it, "2026-11-05T0${it + 3}:00:00Z") }),
            ),
            shell,
            HomeActions(),
            clock,
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
                HomeUiState(
                    loading = false,
                    schedule = HomeSchedule((1L..2L).map { visit(it, "2026-11-05T0${it + 3}:00:00Z") }),
                ),
                shell,
                HomeActions(),
                clock,
            )
        }
    }

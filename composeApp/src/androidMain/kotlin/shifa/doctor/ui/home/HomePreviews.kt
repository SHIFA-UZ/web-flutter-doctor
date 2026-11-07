@file:Suppress("ktlint:standard:max-line-length")

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

// WEB-07b (DaHome web): the date and greeting, the main column and the attention column side by side.
@Composable
internal fun HomeWidePreview() =
    PreviewTheme {
        androidx.compose.runtime.CompositionLocalProvider(shifa.doctor.ui.widgets.LocalWideLayout provides true) {
            HomeContent(
                HomeUiState(
                    loading = false,
                    schedule = busySchedule,
                    heroBirth = day.minus(kotlinx.datetime.DatePeriod(years = 42)),
                ),
                shell,
                HomeActions(),
                clock,
                attention = {
                    val s = HomeAttentionUiState(inboxNotes, listOf(inboxTask))
                    AttentionCard(s, inboxClock, HomeAttentionActions())
                    ActivityCard(s, inboxClock, HomeAttentionActions())
                },
            )
        }
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

private val searchPeople =
    listOf(
        shifa.doctor.data.model.Patient(
            12,
            "Dilnoza Rahimova",
            phones = listOf("+998 90 123 45 67"),
            birthDate = "1990-05-15",
        ),
        shifa.doctor.data.model.Patient(
            7,
            "Jasur Karimov",
            phones = listOf("+998 93 555 12 34"),
            birthDate = "1978-02-01",
        ),
        shifa.doctor.data.model.Patient(18, "Malika Karimova", birthDate = "2001-09-30", clinicalStatus = "FOLLOW_UP"),
    )

// HOME-05: "kar" typed: two matches, counted.
@Composable
internal fun PatientSearchPreview() =
    PreviewTheme {
        PatientSearchContent(PatientSearchUiState(searchPeople, query = "kar", today = day), {}, {}, {}, {})
    }

// HOME-05: nothing matches.
@Composable
internal fun PatientSearchEmptyPreview() =
    PreviewTheme {
        PatientSearchContent(PatientSearchUiState(searchPeople, query = "zz", today = day), {}, {}, {}, {})
    }

private val inboxClock =
    PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-05T09:12:00Z") }, { TimeZone.of("Asia/Tashkent") })

private fun note(
    id: Long,
    type: String,
    at: String,
    read: Boolean = false,
    name: String? = null,
    message: String = "",
) = shifa.doctor.data.model.AppNotification(
    id,
    type,
    shifa.doctor.data.notifications.NotificationKind.of(type),
    "",
    message,
    Instant.parse(at),
    read,
    patientName = name,
)

private val inboxNotes =
    listOf(
        note(1, "APPOINTMENT_BOOKED_BY_PATIENT", "2026-11-05T09:08:00Z", name = "Malika Tursunova"),
        note(2, "CHAT_MESSAGE", "2026-11-05T08:30:00Z"),
        note(3, "APPOINTMENT_CANCELLED_BY_PATIENT", "2026-11-05T05:10:00Z", name = "Jasur Karimov"),
        note(
            4,
            "TASK_COMPLETED",
            "2026-11-04T07:00:00Z",
            read = true,
            message = "Bekzod Aliyev completed \"Blood pressure diary\".",
        ),
        note(
            5,
            "AI_SCRIBE_READY",
            "2026-11-01T06:00:00Z",
            read = true,
            message = "AI scribe notes are ready for review.",
        ),
    )

private val inboxTask =
    shifa.doctor.data.model.RemoteCareTask(
        501, 14, "Bekzod Aliyev", "Asia/Tashkent", "Blood pressure diary", null, shifa.doctor.data.model.TaskCategory.Vital,
        shifa.doctor.data.model.TaskStatus.Active,
        shifa.doctor.data.model.TaskSchedule.of(
            3,
            "08:00",
            null,
            null,
            emptyList(),
        ),
        kotlinx.datetime.LocalDate(
            2026,
            11,
            4,
        ),
        null, 14, shifa.doctor.data.model.TaskInputType.Numeric, null, false, null, null,
        shifa.doctor.data.model.TaskProgress(6, 3, 2, 1), null, null,
    )

// HOME-04: three unread (with Mark as read) and a follow-up, then the activity feed.
@Composable
internal fun HomeAttentionPreview() =
    PreviewTheme {
        Column(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
        ) {
            val s = HomeAttentionUiState(inboxNotes, listOf(inboxTask))
            AttentionCard(s, inboxClock, HomeAttentionActions())
            ActivityCard(s, inboxClock, HomeAttentionActions())
        }
    }

// HOME-04: nothing waiting; the activity read failed elsewhere.
@Composable
internal fun HomeAttentionEmptyPreview() =
    PreviewTheme {
        Column(
            Modifier.background(ShifaColors.Bg.Canvas).padding(ShifaSize.S16),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
        ) {
            AttentionCard(
                HomeAttentionUiState(
                    inboxNotes.map {
                        it.copy(read = true)
                    },
                    emptyList(),
                ),
                inboxClock,
                HomeAttentionActions(),
            )
            ActivityCard(HomeAttentionUiState(failed = true), inboxClock, HomeAttentionActions())
        }
    }

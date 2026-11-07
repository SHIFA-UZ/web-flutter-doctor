package shifa.doctor.ui.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import shifa.doctor.data.model.DayOccupancy
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaSpacing

// Tuesday 2026-11-03 is today; the 11th is selected; past and future days with every shade.
private val occupancy =
    mapOf(
        2 to DayOccupancy(14, 2, 0),
        3 to DayOccupancy(8, 8, 0),
        5 to DayOccupancy(12, 2, 1),
        6 to DayOccupancy(0, 0, 1, fullDayBlock = true),
        9 to DayOccupancy(6, 10, 0),
        12 to DayOccupancy(16, 0, 0),
    ).mapKeys { LocalDate(2026, 11, it.key) }

@Composable
internal fun MonthPickerPreview() =
    PreviewTheme {
        MonthPicker(
            LocalDate(2026, 11, 1),
            LocalDate(2026, 11, 11),
            LocalDate(2026, 11, 3),
            { occupancy[it] },
            {},
            {},
            Modifier.padding(ShifaSpacing.Inset.Md),
        )
    }

private val clock =
    shifa.doctor.utils.PracticeClock("Asia/Tashkent", {
        kotlinx.datetime.Instant.parse("2026-11-04T05:15:00Z")
    }, { kotlinx.datetime.TimeZone.of("Asia/Tashkent") })

private fun at(
    h: Int,
    m: Int,
) = kotlinx.datetime.Instant.parse(
    "2026-11-04T${(h - 5).toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:00Z",
)

private val day =
    listOf(
        shifa.doctor.data.model.CalendarEntry.FreeSlot(at(9, 0), at(9, 30), 1, "Shifa Clinic"),
        shifa.doctor.data.model.CalendarEntry.Appointment(
            at(
                10,
                0,
            ),
            at(10, 30), 101, 11, "Dilnoza Rahimova", null, "CONFIRMED", "PAID", "Follow-up", "Video Consultation",
        ),
        shifa.doctor.data.model.CalendarEntry.Appointment(
            at(
                11,
                0,
            ),
            at(
                11,
                30,
            ),
            102, 12, "Jasur Karimov", null, "CANCELLED", null, "First visit", "Shifa Clinic", 1, "Shifa Clinic",
        ),
        shifa.doctor.data.model.CalendarEntry.Blocked(at(13, 0), at(14, 0), 1, "Lunch"),
        shifa.doctor.data.model.CalendarEntry.Appointment(
            at(
                15,
                0,
            ),
            at(
                16,
                0,
            ),
            103, 13, "Malika Tursunova", null, "REQUESTED", "PENDING",
            "Braces check", "Shifa Clinic", 1, "Shifa Clinic",
        ),
    )

private fun state(details: Boolean) =
    CalendarUiState(
        clock = clock,
        cache =
            shifa.doctor.data.calendar.CalendarCache(
                days = mapOf(shifa.doctor.data.calendar.DayKey(LocalDate(2026, 11, 4)) to day),
            ),
        details = details,
    )

@Composable
internal fun CalendarDayPreview() = PreviewTheme { CalendarContent(state(false), CalendarActions()) }

// WEB-07c (DaCalendar web): the month card on the left, the chosen day's agenda beside it.
@androidx.compose.runtime.Composable
internal fun CalendarWidePreview() =
    PreviewTheme {
        androidx.compose.runtime.CompositionLocalProvider(shifa.doctor.ui.widgets.LocalWideLayout provides true) {
            CalendarContent(state(false), CalendarActions())
        }
    }

// CAL-10 A (flow 04/14 §3 A, board DaCalendar "Staff calendars"): the doctor's own day under "My schedule", a
// colleague's under their name and colour; the third doctor's chip off.
@Composable
internal fun CalendarStaffPreview() =
    PreviewTheme {
        val clinic = shifa.doctor.data.model.MyClinic(2, "Shifa Dev Clinic", "Asia/Tashkent", address = "Amir Temur 1")
        val roster =
            listOf(
                shifa.doctor.data.model.ClinicMember(22, 102, "Bobur Aliyev", "DOCTOR"),
                shifa.doctor.data.model.ClinicMember(23, 103, "Madina Rasulova", "DOCTOR"),
                shifa.doctor.data.model.ClinicMember(9001, 101, "Ulugbek Karimov", "OWNER"),
            )
        val colleague =
            listOf(
                shifa.doctor.data.model.CalendarEntry.FreeSlot(at(9, 0), at(9, 30), 1, "Shifa Clinic"),
                shifa.doctor.data.model.CalendarEntry.Blocked(at(12, 0), at(12, 30), 2, "Team meeting"),
            )
        CalendarContent(
            state(false).let { s ->
                s.copy(
                    cache =
                        s.cache.copy(
                            days =
                                mapOf(
                                    shifa.doctor.data.calendar.DayKey(LocalDate(2026, 11, 4)) to day.take(2),
                                    shifa.doctor.data.calendar.DayKey(LocalDate(2026, 11, 4), 22) to colleague,
                                ),
                        ),
                    staff = StaffCalendars(clinic, roster, 9001, setOf(22)),
                )
            },
            CalendarActions(),
        )
    }

// CAL-10 / CLINIC-09 (flow 04/14 B, board DaCalendar calDoctor): a colleague's calendar, titled with their name, the
// clinic hint, no Block button, a back button.
@Composable
internal fun ClinicDoctorSchedulePreview() =
    PreviewTheme {
        val scope = CalendarScope(22, "Bobur Aliyev", "Asia/Tashkent", "Amir Temur 1")
        CalendarContent(
            state(false).let { s ->
                s.copy(
                    scope = scope,
                    cache =
                        s.cache.copy(
                            days = mapOf(shifa.doctor.data.calendar.DayKey(LocalDate(2026, 11, 4), 22) to day),
                        ),
                )
            },
            CalendarActions(back = {}),
        )
    }

@Composable
internal fun CalendarDetailsPreview() = PreviewTheme { CalendarContent(state(true), CalendarActions()) }

// CAL-6: an unpaid video visit at 11:00 (now 10:15): too early to start, the reminder offered, 30 or 60 minutes.
private val video =
    shifa.doctor.data.model.CalendarEntry.Appointment(
        at(11, 0),
        at(11, 30),
        104,
        14,
        "Dilnoza Rahimova",
        null,
        "CONFIRMED",
        "PENDING",
        "Follow-up",
        "Video Consultation",
        1,
    )

@Composable
internal fun AppointmentPanelPreview() =
    PreviewTheme {
        AppointmentPanel(
            PanelUiState(
                video,
                clock,
                day =
                    listOf(
                        video,
                        shifa.doctor.data.model.CalendarEntry.FreeSlot(at(11, 30), at(12, 0), 1, "Shifa Clinic"),
                    ),
            ),
            PanelActions(durations = { listOf(30, 60) }),
        )
    }

// A visit that is over: the summary, no changes, no cancel.
@Composable
internal fun AppointmentPanelCompletedPreview() =
    PreviewTheme {
        AppointmentPanel(
            PanelUiState(
                video.copy(start = at(9, 0), end = at(9, 30), status = "COMPLETED", paymentStatus = "PAID"),
                clock,
            ),
            PanelActions(),
        )
    }

// CAL-7: 4 November (now 10:15 Tashkent), 11:30 at Shifa Clinic chained with 12:00, a patient picked.
private val freeDay =
    listOf(
        shifa.doctor.data.model.CalendarEntry.FreeSlot(at(11, 30), at(12, 0), 1, "Shifa Clinic"),
        shifa.doctor.data.model.CalendarEntry.FreeSlot(at(12, 0), at(12, 30), 1, "Shifa Clinic"),
        shifa.doctor.data.model.CalendarEntry.FreeSlot(at(12, 30), at(13, 0), 1, "Shifa Clinic"),
    )

@Composable
internal fun BookingPreview() =
    PreviewTheme {
        BookingContent(
            BookingUiState(
                freeDay.first(),
                clock,
                "Check-up",
                day = freeDay,
                end = at(12, 30),
                patient = shifa.doctor.data.model.PatientOption(3, "Anna Karimova"),
            ),
            BookingActions(),
        )
    }

// A slot on a day before today: only the guard.
@Composable
internal fun BookingPastPreview() =
    PreviewTheme {
        val past =
            shifa.doctor.data.model.CalendarEntry.FreeSlot(
                at(9, 0) - kotlin.time.Duration.parse("1d"),
                at(9, 30) - kotlin.time.Duration.parse("1d"),
                1,
                "Shifa Clinic",
            )
        BookingContent(BookingUiState(past, clock, "Check-up"), BookingActions())
    }

// CAL-8: the block tray over 4 November, some hours with a reason, two appointments to be cancelled.
@Composable
internal fun BlockTimePreview() =
    PreviewTheme {
        BlockTimeTray(
            BlockUiState(
                clock,
                LocalDate(2026, 11, 4),
                mode = BlockMode.Hours,
                startMinute = 13 * 60,
                endMinute = 17 * 60,
                reason = "Break",
                count = 2,
            ),
            BlockActions(),
        )
    }

// The blocked row's panel.
@Composable
internal fun BlockPanelPreview() =
    PreviewTheme {
        BlockPanelContent(
            BlockPanelUiState(shifa.doctor.data.model.CalendarEntry.Blocked(at(13, 0), at(14, 0), 1, "Lunch"), clock),
            onClose = {},
        )
    }

// PAT-07: "New appointment" from a patient's record: the month, 4 November's free slots at two places, 10:00 picked.
@androidx.compose.runtime.Composable
internal fun SlotPickPreview() =
    shifa.doctor.ui.theme.PreviewTheme {
        val slots =
            listOf(
                shifa.doctor.data.model.CalendarEntry.FreeSlot(at(9, 0), at(9, 30), 1, "Shifa Clinic"),
                shifa.doctor.data.model.CalendarEntry.FreeSlot(at(10, 0), at(10, 30), 1, "Shifa Clinic"),
                shifa.doctor.data.model.CalendarEntry.FreeSlot(at(10, 0), at(10, 30), 2, "Chilonzor branch"),
            )
        SlotPickTray(
            SlotPickUiState(clock, slots = slots, target = slots[1]),
            {},
            {},
            {},
            {},
            {},
        )
    }

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
            103, 13, "Malika Tursunova", null, "REQUESTED", "PENDING", "Braces check", "Shifa Clinic", 1, "Shifa Clinic",
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

@Composable
internal fun CalendarDetailsPreview() = PreviewTheme { CalendarContent(state(true), CalendarActions()) }

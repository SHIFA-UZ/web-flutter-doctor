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

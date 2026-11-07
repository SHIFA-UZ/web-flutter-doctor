package shifa.doctor.ui.widgets

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.done
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.utils.tr

/**
 * The platform-neutral Material date picker in the app's colours (the design has no date picker): opens on [initial],
 * offers [years] and only the dates [selectable] accepts; null when dismissed. It works in UTC midnights, so a date never
 * shifts with the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShifaDatePickerDialog(
    initial: LocalDate,
    years: IntRange,
    onPick: (LocalDate?) -> Unit,
    selectable: (LocalDate) -> Boolean = { true },
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            yearRange = years,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = selectable(utcTimeMillis.toDate())
                },
        )
    val colors =
        DatePickerDefaults.colors(
            containerColor = ShifaColors.Bg.Surface,
            selectedDayContainerColor = ShifaColors.Interactive.Primary,
            selectedYearContainerColor = ShifaColors.Interactive.Primary,
            todayDateBorderColor = ShifaColors.Border.Brand,
        )
    DatePickerDialog(
        onDismissRequest = { onPick(null) },
        confirmButton = { ShifaLink(tr(Res.string.done), { onPick(state.selectedDateMillis?.toDate()) }) },
        dismissButton = { ShifaLink(tr(Res.string.cancel), { onPick(null) }) },
        colors = colors,
    ) { DatePicker(state, colors = colors, showModeToggle = false) }
}

private fun Long.toDate() = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

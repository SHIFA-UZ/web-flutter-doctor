package shifa.doctor.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.profile.Gender
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.address
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.chooseAClinic
import shifa.doctor.generated.resources.chooseOneOrMore
import shifa.doctor.generated.resources.clearAll
import shifa.doctor.generated.resources.clinic
import shifa.doctor.generated.resources.createAccount
import shifa.doctor.generated.resources.dateOfBirth
import shifa.doctor.generated.resources.done
import shifa.doctor.generated.resources.female
import shifa.doctor.generated.resources.gender
import shifa.doctor.generated.resources.male
import shifa.doctor.generated.resources.noClinic
import shifa.doctor.generated.resources.notSet
import shifa.doctor.generated.resources.optional
import shifa.doctor.generated.resources.other
import shifa.doctor.generated.resources.profession
import shifa.doctor.generated.resources.search
import shifa.doctor.generated.resources.searchProfession
import shifa.doctor.generated.resources.searching
import shifa.doctor.ui.auth.LocalLanguageSwitch
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPickField
import shifa.doctor.ui.widgets.ShifaPickerSheet
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.utils.UzbekCyrillic
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.tr

/**
 * AUTH-07 · step 2 (DaAuth `reg2`, flow 01/08): date of birth, gender, address, clinic and professions, all optional,
 * then the practice timezone. "Create account" registers and PATCHes the rest once.
 */
@Composable
internal fun ColumnScope.DetailsStep(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val enabled = !state.loading
    ShifaPickField(
        tr(Res.string.dateOfBirth),
        state.dob?.let(::displayDate).orEmpty(),
        "${tr(Res.string.dateOfBirth)} (${tr(Res.string.optional)})",
        actions.openDob,
        Modifier.fillMaxWidth(),
        enabled = enabled,
    )
    val genders = listOf(null) + Gender.entries
    val genderLabels = genders.map { tr(it.label()) }
    ShifaDropdown(
        tr(Res.string.gender),
        genderLabels,
        if (state.gender == null) null else tr(state.gender.label()),
        onSelect = { actions.pickGender(genders[it]) },
        expanded = state.genderOpen,
        onExpandedChange = { if (enabled) actions.toggleGender() },
        modifier = Modifier.fillMaxWidth(),
    )
    TextField(state.address, actions.onAddress, tr(Res.string.address), null, enabled)
    ShifaPickField(
        tr(Res.string.clinic),
        state.clinic?.name.orEmpty(),
        tr(Res.string.chooseAClinic),
        { actions.openPicker(RegisterPicker.Clinic) },
        Modifier.fillMaxWidth(),
        enabled = enabled,
    )
    ShifaPickField(
        tr(Res.string.profession),
        "",
        tr(Res.string.chooseOneOrMore),
        { actions.openPicker(RegisterPicker.Profession) },
        Modifier.fillMaxWidth(),
        enabled = enabled,
        chips = state.professions.map { it.label() },
    )
    TimeZoneField(state, actions, hint = true)
    StepDots(1)
    ShifaPrimaryButton(tr(Res.string.createAccount), actions.register, Modifier.fillMaxWidth(), loading = state.loading)
}

/** The clinic sheet: the backend's list or search, "Searching…", "Couldn't load. Retry", and "No clinic" to clear. */
@Composable
internal fun ClinicPicker(
    state: RegisterUiState,
    actions: RegisterActions,
) = ShifaPickerSheet(
    tr(Res.string.clinic),
    state.query,
    actions.onQuery,
    tr(Res.string.search),
    state.clinics.map { OptionItem(it.name, selected = it.id == state.clinic?.id) },
    onPick = { actions.pickClinic(state.clinics[it]) },
    onClose = actions.closePicker,
    loadingText = if (state.pickerLoading) tr(Res.string.searching) else null,
    onRetry = actions.retryPicker.takeIf { state.pickerFailed },
    footer =
        if (state.clinic != null) {
            { ShifaSecondaryButton(tr(Res.string.noClinic), { actions.pickClinic(null) }, Modifier.fillMaxWidth()) }
        } else {
            null
        },
)

/** The profession sheet: several can be picked; "Clear all" and "Done" below (DaAuth picker, `multi`). */
@Composable
internal fun ProfessionPicker(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val options = state.professionOptions
    ShifaPickerSheet(
        tr(Res.string.profession),
        state.query,
        actions.onQuery,
        tr(Res.string.searchProfession),
        options.map { p -> OptionItem(p.label(), selected = state.professions.any { it.english == p.english }) },
        onPick = { actions.toggleProfession(options[it]) },
        onClose = actions.closePicker,
        loadingText = if (state.pickerLoading) tr(Res.string.searching) else null,
        onRetry = actions.retryPicker.takeIf { state.pickerFailed },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                if (state.professions.isNotEmpty()) {
                    ShifaSecondaryButton(tr(Res.string.clearAll), actions.clearProfessions, Modifier.weight(1f))
                }
                ShifaPrimaryButton(tr(Res.string.done), actions.closePicker, Modifier.weight(1f))
            }
        },
    )
}

/**
 * The platform-neutral Material date picker for the birth date: 1900 to today, opening 25 years back (flow 01/08 §3).
 * The picker works in UTC midnights, so the date never shifts with the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DobPicker(
    current: LocalDate?,
    onPick: (LocalDate?) -> Unit,
) {
    val today = remember { currentInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val initial = current ?: today.minus(DatePeriod(years = DEFAULT_AGE))
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            yearRange = FIRST_YEAR..today.year,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toDate() <= today
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

/** dd.MM.yyyy, the usual form in all three languages (flow 01/08 §12: never the ISO string the backend gets). */
internal fun displayDate(date: LocalDate) =
    "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"

private fun Gender?.label() =
    when (this) {
        null -> Res.string.notSet
        Gender.Male -> Res.string.male
        Gender.Female -> Res.string.female
        Gender.Other -> Res.string.other
    }

/** The catalogue label in the app language: Uzbek (Latin or Cyrillic) for uz, English otherwise (no Russian labels). */
@Composable
internal fun Profession.label(): String {
    val language = LocalLanguageSwitch.current.current
    val uz = uzbek?.takeIf { language.resourceCode == "uz" && it.isNotBlank() } ?: return english
    return if (language.cyrillic) UzbekCyrillic.fromLatin(uz) else uz
}

private const val FIRST_YEAR = 1900
private const val DEFAULT_AGE = 25

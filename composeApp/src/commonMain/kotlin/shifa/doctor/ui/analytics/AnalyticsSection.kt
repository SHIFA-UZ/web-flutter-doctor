package shifa.doctor.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.AnalyticsOverview
import shifa.doctor.data.model.AnalyticsRange
import shifa.doctor.data.model.Engagement
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.activePatients
import shifa.doctor.generated.resources.analyticsDateRange
import shifa.doctor.generated.resources.analyticsRangeHint
import shifa.doctor.generated.resources.analyticsTeaserBody
import shifa.doctor.generated.resources.analyticsTeaserTitle
import shifa.doctor.generated.resources.appointmentsToday
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.cancelledToday
import shifa.doctor.generated.resources.clinicPerformance
import shifa.doctor.generated.resources.clinicPerformanceSubtitle
import shifa.doctor.generated.resources.completedToday
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.generated.resources.documentsReceived
import shifa.doctor.generated.resources.engagementSummary
import shifa.doctor.generated.resources.export
import shifa.doctor.generated.resources.ic_block_20
import shifa.doctor.generated.resources.ic_calendar_18
import shifa.doctor.generated.resources.ic_calendar_20
import shifa.doctor.generated.resources.ic_check_circle_20
import shifa.doctor.generated.resources.ic_download_18
import shifa.doctor.generated.resources.ic_file_text_20
import shifa.doctor.generated.resources.ic_sparkles_20
import shifa.doctor.generated.resources.ic_user_plus_20
import shifa.doctor.generated.resources.ic_users_20
import shifa.doctor.generated.resources.lastDaysCount
import shifa.doctor.generated.resources.monthApr
import shifa.doctor.generated.resources.monthAug
import shifa.doctor.generated.resources.monthDec
import shifa.doctor.generated.resources.monthFeb
import shifa.doctor.generated.resources.monthJan
import shifa.doctor.generated.resources.monthJul
import shifa.doctor.generated.resources.monthJun
import shifa.doctor.generated.resources.monthMar
import shifa.doctor.generated.resources.monthMay
import shifa.doctor.generated.resources.monthNov
import shifa.doctor.generated.resources.monthOct
import shifa.doctor.generated.resources.monthSep
import shifa.doctor.generated.resources.newPatientsToday
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.seePlans
import shifa.doctor.generated.resources.selectDateRange
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.rememberFileShare
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

/** Where the card's taps lead (flow 02/05 §7 targets). */
class AnalyticsActions(
    val calendar: () -> Unit = {},
    val patients: () -> Unit = {},
    val seePlans: () -> Unit = {},
)

/**
 * "Clinic performance" (ANALYTICS-02, flow 02/05 and 02/06, board `DaHome`): the range and Export, today's four KPIs,
 * then (PREMIUM) engagement, or a teaser for the other plans. [advanced] null: the plan isn't known yet, so neither
 * shows (no flash, flow §6). Reused by Reports (REPORT-01).
 */
@Composable
fun AnalyticsSection(
    today: LocalDate,
    advanced: Boolean?,
    actions: AnalyticsActions,
    viewModel: AnalyticsViewModel = viewModel { AnalyticsViewModel(today) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val share = rememberFileShare()
    // The CSV is shared once; the share sheet is the confirmation (Flutter's "check your downloads" was wrong).
    LaunchedEffect(state.export) {
        state.export?.let { viewModel.exported(share(it.fileName, "text/csv", it.csv.encodeToByteArray())) }
    }
    AnalyticsContent(state, advanced, viewModel::refresh, viewModel::setRange, viewModel::export, actions)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalyticsContent(
    state: AnalyticsUiState,
    advanced: Boolean?,
    onRetry: () -> Unit,
    onRange: (AnalyticsRange) -> Unit,
    onExport: () -> Unit,
    actions: AnalyticsActions,
) = Column(
    Modifier
        .fillMaxWidth()
        .dropShadow(ShifaRadius.ControlLg, ShifaShadow.Card)
        .clip(ShifaRadius.ControlLg)
        .background(ShifaColors.Bg.Surface)
        .padding(ShifaSize.S16),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            tr(Res.string.clinicPerformance),
            Modifier.semantics { heading() },
            style = ShifaTextStyles.sectionHeading(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            tr(Res.string.clinicPerformanceSubtitle),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    // The toolbar, stacked full width on phones (board `DaHome`).
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        val label = rangeLabel(state.range)
        val described = tr(Res.string.analyticsDateRange, label)
        ShifaSecondaryButton(
            label,
            { picking = true },
            Modifier.fillMaxWidth().semantics { contentDescription = described },
            icon = Res.drawable.ic_calendar_18,
        )
        ShifaPrimaryButton(
            tr(Res.string.export),
            onExport,
            Modifier.fillMaxWidth(),
            icon = Res.drawable.ic_download_18,
            loading = state.exporting,
        )
    }
    Kpis(state.overview, onRetry, actions)
    when (advanced) {
        true -> EngagementBlock(state.engagement, onRetry, actions)
        false -> Teaser(actions.seePlans)
        null -> Unit
    }
    if (picking) {
        RangeDialog(state.range, state.today, { onRange(it).also { picking = false } }, { picking = false })
    }
}

/**
 * Today's four counts, one per row on phones (flow 02/05 §3, board `DaHome` KPI tiles); the server's day, so they ignore
 * the range. Appointments opens Calendar, New patients opens Patients.
 */
@Composable
private fun Kpis(
    overview: Result<AnalyticsOverview>?,
    onRetry: () -> Unit,
    actions: AnalyticsActions,
) {
    if (overview?.isFailure == true) return LoadFailed(onRetry)
    val o = overview?.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
        KpiTile(
            Res.drawable.ic_calendar_20,
            KpiTone.Brand,
            o?.appointmentsToday,
            Res.string.appointmentsToday,
            actions.calendar,
        )
        KpiTile(Res.drawable.ic_check_circle_20, KpiTone.Brand, o?.completedToday, Res.string.completedToday, null)
        KpiTile(Res.drawable.ic_block_20, KpiTone.Warn, o?.cancelledToday, Res.string.cancelledToday, null)
        KpiTile(
            Res.drawable.ic_user_plus_20,
            KpiTone.Info,
            o?.newPatientsToday,
            Res.string.newPatientsToday,
            actions.patients,
        )
    }
}

/** A KPI tile: tinted icon, the value (a skeleton while loading), its label up to 2 lines. */
@Composable
private fun KpiTile(
    icon: DrawableResource,
    tone: KpiTone,
    value: Int?,
    label: StringResource,
    onClick: (() -> Unit)?,
) = Row(
    Modifier
        .fillMaxWidth()
        .clip(ShifaRadius.ControlLg)
        .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlLg)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(ShifaSize.S12),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(ShifaSize.S40).background(tone.tint, ShifaRadius.ControlMd), Alignment.Center) {
        Icon(painterResource(icon), null, tint = tone.ink)
    }
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        if (value == null) {
            ShifaSkeleton(Modifier.size(ShifaSize.S36, ShifaSize.S24))
        } else {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = ShifaColors.Text.Primary)
        }
        Text(tr(label), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary, maxLines = 2)
    }
}

/** The board's KPI tints: brand (teal), warning (amber), info (blue). */
private enum class KpiTone(
    val tint: androidx.compose.ui.graphics.Color,
    val ink: androidx.compose.ui.graphics.Color,
) {
    Brand(ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.BrandStrong),
    Warn(ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning),
    Info(ShifaColors.Bg.InfoSubtle, ShifaColors.Text.Info),
}

/** "Patient Engagement" (PREMIUM, board `DaHome`): active patients (opens Patients) and documents received. */
@Composable
private fun EngagementBlock(
    engagement: Result<Engagement>?,
    onRetry: () -> Unit,
    actions: AnalyticsActions,
) = Block(tr(Res.string.engagementSummary)) {
    if (engagement?.isFailure == true) return@Block LoadFailed(onRetry)
    val e = engagement?.getOrNull()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        Tile(
            Res.drawable.ic_users_20,
            e?.activePatients,
            Res.string.activePatients,
            actions.patients,
            Modifier.weight(1f),
        )
        Tile(
            Res.drawable.ic_file_text_20,
            e?.documentsReceived,
            Res.string.documentsReceived,
            null,
            Modifier.weight(1f),
        )
    }
}

/** A titled, outlined block inside the card (the charts and engagement, board `DaHome`). */
@Composable
internal fun Block(
    title: String,
    content: @Composable () -> Unit,
) = Column(
    Modifier
        .fillMaxWidth()
        .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.ControlLg)
        .padding(ShifaSize.S16),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    Text(
        title,
        Modifier.semantics {
            heading()
        },
        style = MaterialTheme.typography.titleSmall,
        color = ShifaColors.Text.Primary,
    )
    content()
}

@Composable
private fun Tile(
    icon: DrawableResource,
    value: Int?,
    label: StringResource,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) = Column(
    modifier
        .fillMaxHeight()
        .clip(ShifaRadius.ControlMd)
        .background(ShifaColors.Bg.Canvas)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(ShifaSize.S12),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
) {
    Icon(painterResource(icon), null, tint = ShifaColors.Text.BrandStrong)
    if (value == null) {
        ShifaSkeleton(Modifier.size(ShifaSize.S36, ShifaSize.S22))
    } else {
        Text(value.toString(), style = ShifaTextStyles.sectionHeading(), color = ShifaColors.Text.Primary)
    }
    Text(tr(label), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary, maxLines = 2)
}

/** BASIC and PRO (board `DaHome`): what the charts would show, and the way to them. */
@Composable
private fun Teaser(seePlans: () -> Unit) =
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.SuccessSubtle)
            .border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.ControlLg)
            .padding(ShifaSize.S16),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd), Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_sparkles_20), null, tint = ShifaColors.Text.BrandStrong)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                tr(Res.string.analyticsTeaserTitle),
                style = ShifaTextStyles.contextName(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(Res.string.analyticsTeaserBody),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
        ShifaSecondaryButton(tr(Res.string.seePlans), seePlans, size = ShifaButtonSize.Compact)
    }

/** A block that couldn't be read: says so with Retry (Flutter printed "Error"). */
@Composable
internal fun LoadFailed(onRetry: () -> Unit) =
    Row(
        Modifier.fillMaxWidth().padding(vertical = ShifaSize.S8),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(Res.string.couldNotLoad),
            Modifier.weight(1f),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
        )
        ShifaLink(tr(Res.string.retry), onRetry)
    }

private val SHORT_MONTHS =
    listOf(
        Res.string.monthJan,
        Res.string.monthFeb,
        Res.string.monthMar,
        Res.string.monthApr,
        Res.string.monthMay,
        Res.string.monthJun,
        Res.string.monthJul,
        Res.string.monthAug,
        Res.string.monthSep,
        Res.string.monthOct,
        Res.string.monthNov,
        Res.string.monthDec,
    )

/**
 * "17 Oct – 23 Oct, 2026" in the app's language (flow 02/06 §4; Flutter kept English months); the year on both ends
 * across a new year, one date for a one-day range.
 */
@Composable
internal fun rangeLabel(range: AnalyticsRange): String {
    @Composable
    fun day(
        d: LocalDate,
        year: Boolean,
    ) = "${d.dayOfMonth} ${tr(SHORT_MONTHS[d.monthNumber - 1])}" + if (year) " ${d.year}" else ""
    return when {
        range.start == range.end -> day(range.start, true)
        range.start.year == range.end.year -> "${day(range.start, false)} – ${day(range.end, false)}, ${range.end.year}"
        else -> "${day(range.start, true)} – ${day(range.end, true)}"
    }
}

/**
 * "Select date range" (flow 02/06 §3, board `DaHome`): presets for the last 7, 30 and 90 days, the system range picker
 * from a year back to 30 days ahead of the practice date, Cancel and Save (Save only with both ends).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RangeDialog(
    range: AnalyticsRange,
    today: LocalDate,
    onSave: (AnalyticsRange) -> Unit,
    onClose: () -> Unit,
) {
    val first = today.minus(DatePeriod(days = 365))
    val last = today.plus(DatePeriod(days = 30))
    val picker =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = range.start.millis(),
            initialSelectedEndDateMillis = range.end.millis(),
            yearRange = first.year..last.year,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toDate() in first..last
                },
        )
    val colors =
        DatePickerDefaults.colors(
            containerColor = ShifaColors.Bg.Surface,
            selectedDayContainerColor = ShifaColors.Interactive.Primary,
            dayInSelectionRangeContainerColor = ShifaColors.Bg.SuccessSubtle,
            todayDateBorderColor = ShifaColors.Border.Brand,
        )
    val start = picker.selectedStartDateMillis?.toDate()
    val end = picker.selectedEndDateMillis?.toDate()
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            ShifaLink(tr(Res.string.save), { if (start != null && end != null) onSave(AnalyticsRange(start, end)) })
        },
        dismissButton = { ShifaLink(tr(Res.string.cancel), onClose) },
        colors = colors,
    ) {
        DateRangePicker(
            picker,
            Modifier.weight(1f),
            // The title slot carries the board's presets and its bounds hint (the dialog lays out only the picker).
            title = {
                Column(
                    Modifier.padding(start = ShifaSize.S24, end = ShifaSize.S16, top = ShifaSize.S16),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        tr(Res.string.selectDateRange),
                        style = ShifaTextStyles.contextName(),
                        color = ShifaColors.Text.Primary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                        verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    ) {
                        listOf(7, 30, 90).forEach { n ->
                            val preset = AnalyticsRange.lastDays(today, n)
                            ShifaChip(
                                trPlural(
                                    Res.plurals.lastDaysCount,
                                    n,
                                    n,
                                ),
                                start == preset.start && end == preset.end,
                                {
                                    picker.setSelection(preset.start.millis(), preset.end.millis())
                                },
                            )
                        }
                    }
                    Text(
                        tr(Res.string.analyticsRangeHint),
                        style = ShifaTextStyles.metaSmall(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
            },
            showModeToggle = false,
            colors = colors,
        )
    }
}

private fun LocalDate.millis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toDate() = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

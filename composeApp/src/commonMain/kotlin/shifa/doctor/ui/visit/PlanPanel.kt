package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.amountExceedsOutstanding
import shifa.doctor.generated.resources.amountGreaterThanZero
import shifa.doctor.generated.resources.appointmentLinkedPlanBanner
import shifa.doctor.generated.resources.appointmentPlanAllDone
import shifa.doctor.generated.resources.appointmentPlanChartIntro
import shifa.doctor.generated.resources.appointmentPlanFinanceAmount
import shifa.doctor.generated.resources.appointmentPlanFinanceLoadFailed
import shifa.doctor.generated.resources.appointmentPlanFinanceMethod
import shifa.doctor.generated.resources.appointmentPlanFinanceOutstanding
import shifa.doctor.generated.resources.appointmentPlanFinancePaid
import shifa.doctor.generated.resources.appointmentPlanFinanceRecorded
import shifa.doctor.generated.resources.appointmentPlanFinanceSessionPayment
import shifa.doctor.generated.resources.appointmentPlanFinanceTitle
import shifa.doctor.generated.resources.appointmentPlanFinanceTotal
import shifa.doctor.generated.resources.appointmentPlanFulfillSheetHint
import shifa.doctor.generated.resources.appointmentPlanNoOpenLines
import shifa.doctor.generated.resources.appointmentTreatmentPlanChartHint
import shifa.doctor.generated.resources.appointmentTreatmentPlanNone
import shifa.doctor.generated.resources.appointmentTreatmentPlanPick
import shifa.doctor.generated.resources.appointmentTreatmentPlanTitle
import shifa.doctor.generated.resources.clinicTreatmentPlanLineStatusPlanned
import shifa.doctor.generated.resources.clinicTreatmentPlansUntitled
import shifa.doctor.generated.resources.dentalGeneralServices
import shifa.doctor.generated.resources.dentalGeneralServicesHint
import shifa.doctor.generated.resources.dentalPlanLegendCompleted
import shifa.doctor.generated.resources.dentalPlanLegendNone
import shifa.doctor.generated.resources.dentalPlanLegendPartial
import shifa.doctor.generated.resources.dentalPlanLegendPlanned
import shifa.doctor.generated.resources.dentalPlanProgress
import shifa.doctor.generated.resources.dentalToothServices
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.openDentalToTick
import shifa.doctor.generated.resources.recordedAtEnd
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.toothA11y
import shifa.doctor.generated.resources.toothMap
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaCheckbox
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.Money
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

class PlanActions(
    val select: (Long?) -> Unit = {},
    val retry: () -> Unit = {},
    // A tooth's (or the general) fulfil sheet; null closes it.
    val open: (String?) -> Unit = {},
    val toggle: (Long) -> Unit = {},
    val amount: (String) -> Unit = {},
    val method: (PayMethod) -> Unit = {},
    val toDental: () -> Unit = {},
)

private fun planTitle(
    p: shifa.doctor.data.model.TreatmentPlanSummary,
    untitled: String,
) = p.title?.takeIf { it.isNotBlank() } ?: "$untitled #${p.id}"

/**
 * "Treatment plan for this visit" (board `DaVisit` treatment plan panel, flow 05/08 §3): the linked banner, the plan
 * picker ("None / bill separately" and the patient's active plans), its progress and where to tick. Shown while the
 * visit is open and the patient has plans; a failed read says so with Retry.
 */
@Composable
internal fun PlanPanel(
    plan: VisitPlanState,
    dentalMode: Boolean,
    dentalDoctor: Boolean,
    actions: PlanActions,
) {
    plan.loadError?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        ) {
            Text(
                tr(it.text),
                Modifier.weight(1f),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            ShifaLink(tr(Res.string.retry), actions.retry)
        }
        return
    }
    val plans = plan.plans?.takeIf { it.isNotEmpty() } ?: return
    val untitled = tr(Res.string.clinicTreatmentPlansUntitled)
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        plans.firstOrNull { it.id == plan.linkedId }?.let {
            Text(
                tr(Res.string.appointmentLinkedPlanBanner, it.id, planTitle(it, untitled)),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Primary,
            )
        }
        Text(
            tr(Res.string.appointmentTreatmentPlanTitle),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
        )
        val options = listOf(tr(Res.string.appointmentTreatmentPlanNone)) + plans.map { planTitle(it, untitled) }
        val index = plans.indexOfFirst { it.id == plan.selectedId } + 1
        // Once ticks were applied or a payment tried for it, the plan stays (they belong to it).
        if (plan.locked) {
            Text(options[index], style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
        } else {
            ShifaDropdown(
                tr(Res.string.appointmentTreatmentPlanPick),
                options,
                options[index],
                { i -> actions.select(if (i == 0) null else plans[i - 1].id) },
                open,
                { open = it },
                Modifier.fillMaxWidth(),
            )
        }
        plan.progress?.let { (done, total) ->
            Text(
                tr(Res.string.dentalPlanProgress, done, total),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
            if (dentalMode) {
                Text(
                    tr(Res.string.appointmentTreatmentPlanChartHint),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            } else if (dentalDoctor) {
                ShifaSecondaryButton(tr(Res.string.openDentalToTick), actions.toDental)
            }
        }
    }
}

/**
 * The dental record in plan mode (board `DaVisit` dental mode with a plan, flow 05/08 §3.5): the chart coloured by the
 * plan's lines (ticks count as done), its legend, the general lines, the banners; a tap opens the tooth's lines.
 */
@Composable
internal fun ColumnScope.PlanChart(
    plan: VisitPlanState,
    actions: PlanActions,
) {
    Text(
        tr(Res.string.appointmentPlanChartIntro),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    plan.linesError?.let {
        codeBanner(it) {}?.invoke()
        ShifaLink(tr(Res.string.retry), actions.retry)
    }
    if (plan.detail == null) {
        if (plan.linesError == null) ShifaSkeleton(Modifier.fillMaxWidth().defaultMinSize(minHeight = ShifaSize.S120))
        return
    }
    when {
        plan.allDone ->
            Text(
                tr(Res.string.appointmentPlanAllDone),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Brand,
            )
        plan.candidates?.isEmpty() == true && plan.ticks.isEmpty() ->
            Text(
                tr(Res.string.appointmentPlanNoOpenLines),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
    }
    Text(tr(Res.string.toothMap).uppercase(), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
    val words =
        mapOf(
            ToothPlanState.None to tr(Res.string.dentalPlanLegendNone),
            ToothPlanState.Planned to tr(Res.string.dentalPlanLegendPlanned),
            ToothPlanState.Partial to tr(Res.string.dentalPlanLegendPartial),
            ToothPlanState.Done to tr(Res.string.dentalPlanLegendCompleted),
        )
    // States only, no count badges (the board's plan mode); the count is in the screen-reader text.
    ToothMap(
        plan.dentition,
        emptyMap(),
        states = plan.toothStates,
        selected = plan.sheet,
        describe = { tooth, _, state ->
            val count = plan.toothCounts[tooth] ?: 0
            "${trPlural(Res.plurals.toothA11y, count, tooth, count)}, ${words[state ?: ToothPlanState.None]}"
        },
        onTap = { actions.open(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
        listOf(
            Triple(ShifaColors.Bg.Surface, ShifaColors.Border.Default, ToothPlanState.None),
            Triple(ShifaColors.Bg.InfoSubtle, ShifaColors.Icon.Info, ToothPlanState.Planned),
            Triple(ShifaColors.Bg.Surface, ShifaColors.Border.Brand, ToothPlanState.Partial),
            Triple(ShifaColors.Interactive.Primary, ShifaColors.Interactive.Primary, ToothPlanState.Done),
        ).forEach { (fill, edge, state) -> Legend(fill, edge, words.getValue(state)) }
    }
    val general = plan.lines(DentalChartCodec.GENERAL_KEY)
    if (general.isNotEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd)
                .clickable(role = Role.Button) { actions.open(DentalChartCodec.GENERAL_KEY) }
                .padding(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                Text(
                    "${tr(Res.string.dentalGeneralServices)} (${general.count { plan.done(it) }}/${general.size})",
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Primary,
                )
                Text(
                    tr(Res.string.dentalGeneralServicesHint),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Secondary)
        }
    }
}

/** "Plan finances" (board `DaVisit`, flow 05/09 §3): total, paid, outstanding; this visit's payment, recorded at End. */
@Composable
internal fun PlanFinance(
    plan: VisitPlanState,
    actions: PlanActions,
) {
    val summary = plan.detail?.summary?.takeIf { it.id == plan.selectedId }
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            tr(Res.string.appointmentPlanFinanceTitle),
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
        )
        if (summary == null) {
            Text(
                tr(Res.string.appointmentPlanFinanceLoadFailed),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            listOf(
                Res.string.appointmentPlanFinanceTotal to summary.totalMinor,
                Res.string.appointmentPlanFinancePaid to summary.paidMinor,
                Res.string.appointmentPlanFinanceOutstanding to summary.owedMinor,
            ).forEach { (label, minor) ->
                Text(
                    "${tr(label)}: ${Money.format(minor, summary.currency)}",
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Primary,
                )
            }
        }
        Text(
            tr(Res.string.appointmentPlanFinanceSessionPayment),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
        plan.recorded?.let {
            Text(
                tr(Res.string.appointmentPlanFinanceRecorded, Money.format(it, summary.currency)),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            return@Column
        }
        ShifaInputField(
            plan.amount,
            actions.amount,
            "${tr(Res.string.appointmentPlanFinanceAmount)} (${summary.currency})",
            Modifier.fillMaxWidth(),
            supportingText =
                when {
                    plan.amountInvalid -> tr(Res.string.amountGreaterThanZero)
                    plan.overOutstanding -> tr(Res.string.amountExceedsOutstanding)
                    else -> null
                },
            isError = plan.amountInvalid,
            // A payment whose answer was lost can't be changed before End checks whether it was recorded.
            enabled = plan.attempt == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        val methods = PayMethod.entries
        val labels = methods.map { tr(methodLabel(it)) }
        // The method sent with a payment whose answer was lost stays as it was (End looks for that payment).
        if (plan.attempt != null) {
            Text(
                labels[methods.indexOf(plan.method)],
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Primary,
            )
        } else {
            ShifaDropdown(
                tr(Res.string.appointmentPlanFinanceMethod),
                labels,
                labels[methods.indexOf(plan.method)],
                { actions.method(methods[it]) },
                open,
                { open = it },
                Modifier.fillMaxWidth(),
            )
        }
        Text(tr(Res.string.recordedAtEnd), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
    }
}

/**
 * The planned lines on a tooth (or the general ones), board `DaVisit` "plan fulfil": tick the ones done in this visit;
 * lines already completed show checked and can't change. Save closes; End applies the ticks.
 */
@Composable
internal fun PlanFulfilSheet(
    key: String,
    plan: VisitPlanState,
    actions: PlanActions,
) = ShifaDialogSheet(
    if (key == DentalChartCodec.GENERAL_KEY) {
        tr(
            Res.string.dentalGeneralServices,
        )
    } else {
        "${tr(Res.string.dentalToothServices)} $key"
    },
    tr(Res.string.save),
    { actions.open(null) },
) {
    Text(
        tr(Res.string.appointmentPlanFulfillSheetHint),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    val planned = tr(Res.string.clinicTreatmentPlanLineStatusPlanned)
    val completed = tr(Res.string.dentalPlanLegendCompleted)
    plan.lines(key).forEach { line ->
        val on = plan.done(line)
        val enabled = plan.tickable(line)
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ShifaSize.S48)
                .alpha(if (enabled || line.id in plan.ticks) 1f else DISABLED_ALPHA)
                .toggleable(on, enabled = enabled, role = Role.Checkbox) { actions.toggle(line.id) }
                .padding(horizontal = ShifaSize.S4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            ShifaCheckbox(if (on) ToggleableState.On else ToggleableState.Off)
            Column(Modifier.weight(1f)) {
                Text(line.title, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
                Text(
                    "${line.quantity} × ${Money.format(
                        line.unitPriceMinor,
                        line.currency,
                    )} · ${if (on && !enabled) completed else planned}",
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.6f

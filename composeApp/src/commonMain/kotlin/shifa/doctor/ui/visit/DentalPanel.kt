package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.model.DentalRecord
import shifa.doctor.data.model.DoctorService
import shifa.doctor.data.model.percentText
import shifa.doctor.data.model.toothLine
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.all
import shifa.doctor.generated.resources.dentalClinicalNotes
import shifa.doctor.generated.resources.dentalDentitionPermanent
import shifa.doctor.generated.resources.dentalDentitionPrimary
import shifa.doctor.generated.resources.dentalDiscount
import shifa.doctor.generated.resources.dentalDiscountPercent
import shifa.doctor.generated.resources.dentalDocIntroFree
import shifa.doctor.generated.resources.dentalGeneralServices
import shifa.doctor.generated.resources.dentalGeneralServicesHint
import shifa.doctor.generated.resources.dentalLegendAdded
import shifa.doctor.generated.resources.dentalLegendNone
import shifa.doctor.generated.resources.dentalLineItems
import shifa.doctor.generated.resources.dentalNoServices
import shifa.doctor.generated.resources.dentalSelectedServices
import shifa.doctor.generated.resources.dentalSubtotal
import shifa.doctor.generated.resources.dentalToothServices
import shifa.doctor.generated.resources.dentalTotal
import shifa.doctor.generated.resources.discountOutOfRange
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_plus_18
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.removeService
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.toothA11y
import shifa.doctor.generated.resources.toothMap
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.speech.SpeechOrPlainField
import shifa.doctor.ui.speech.appendTranscript
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.utils.Money
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

class DentalActions(
    val retry: () -> Unit = {},
    // A tooth's (or the general) services sheet opened; null closes it.
    val open: (String?) -> Unit = {},
    val edit: ((DentalRecord) -> DentalRecord) -> Unit = {},
    val dentition: (Dentition) -> Unit = {},
    val discount: (String) -> Unit = {},
    val add: (String, DoctorService) -> Unit = { _, _ -> },
    val remove: (String, Int) -> Unit = { _, _ -> },
    val retryServices: () -> Unit = {},
)

/** Each currency's sum: "1 250 000 UZS + 25.00 USD" (never one currency's lines dropped, 05 §10). */
internal fun sums(byCurrency: Map<String, Long>): String =
    byCurrency.entries.joinToString(" + ") { (c, v) -> Money.format(v, c) }.ifEmpty { Money.format(0, "UZS") }

/**
 * The dental visit record in free mode (DENTAL-02, 05 §4.3, board `DaVisit` dental mode): the intro, the dentition
 * (Flutter's toggle; not on the board), the tooth map with its legend (a tap opens the tooth's services), the general
 * services, the discount, the clinical notes and the totals per currency. Nothing is editable until the saved record
 * was read.
 */
@Composable
internal fun ColumnScope.DentalPanel(
    state: DentalNotesState,
    selected: String?,
    actions: DentalActions,
) {
    Text(tr(Res.string.dentalDocIntroFree), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
    state.loadError?.let {
        codeBanner(it) {}?.invoke()
        ShifaLink(tr(Res.string.retry), actions.retry)
        return
    }
    if (!state.loaded) {
        ShifaSkeleton(Modifier.fillMaxWidth().heightIn(min = ShifaSize.S160))
        return
    }
    val record = state.record
    val kind = record.dentitionKind
    SegmentedTabs(
        listOf(TabItem(tr(Res.string.dentalDentitionPermanent)), TabItem(tr(Res.string.dentalDentitionPrimary))),
        if (kind == Dentition.Primary) 1 else 0,
        { actions.dentition(if (it == 1) Dentition.Primary else Dentition.Permanent) },
        block = true,
    )
    Text(
        tr(Res.string.toothMap).uppercase(),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    val counts = record.counts - DentalChartCodec.GENERAL_KEY
    ToothMap(
        kind,
        counts,
        selected = selected,
        describe = { tooth, count, _ -> trPlural(Res.plurals.toothA11y, count, tooth, count) },
        onTap = { actions.open(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
        Legend(ShifaColors.Bg.Surface, ShifaColors.Border.Default, tr(Res.string.dentalLegendNone))
        Legend(ShifaColors.Interactive.Primary, ShifaColors.Interactive.Primary, tr(Res.string.dentalLegendAdded))
    }
    val general = record.lines(DentalChartCodec.GENERAL_KEY).size
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
                "${tr(Res.string.dentalGeneralServices)} ($general)",
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
    ShifaInputField(
        state.discount,
        actions.discount,
        "${tr(Res.string.dentalDiscountPercent)} (%)",
        Modifier.widthIn(max = ShifaSize.S160),
        supportingText = if (state.discountInvalid) tr(Res.string.discountOutOfRange) else null,
        isError = state.discountInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    SpeechOrPlainField(
        record.notes,
        { v -> actions.edit { it.copy(notes = v) } },
        tr(Res.string.dentalClinicalNotes),
        "dental-notes",
        { t -> actions.edit { it.copy(notes = appendTranscript(it.notes, t)) } },
        Modifier.fillMaxWidth(),
        multiline = true,
        areaMinHeight = ShifaSize.S56,
    )
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S12),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        Text(
            "${tr(
                Res.string.dentalSubtotal,
            )}: ${sums(record.subtotals)} · ${record.lineCount} ${tr(Res.string.dentalLineItems)}",
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            "${tr(Res.string.dentalDiscount)}: ${percentText(record.discountPercent.coerceIn(0.0, 100.0))}%",
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            "${tr(Res.string.dentalTotal)}: ${sums(record.totals)}",
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Primary,
        )
    }
    state.saveError?.let { codeBanner(it) {}?.invoke() }
}

@Composable
private fun Legend(
    fill: androidx.compose.ui.graphics.Color,
    edge: androidx.compose.ui.graphics.Color,
    label: String,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
    Box(
        Modifier
            .size(ShifaSize.S10)
            .background(fill, ShifaRadius.Sm)
            .border(ShifaSize.S1, edge, ShifaRadius.Sm),
    )
    Text(label, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
}

/**
 * A tooth's (or the general) services (board `DaVisit` "dental services picker"): the lines added, each removable,
 * the group chips and the doctor's services, a tap adding one. Changes apply at once (and autosave); Save closes.
 */
@Composable
internal fun DentalServicesSheet(
    tooth: String,
    state: DentalNotesState,
    actions: DentalActions,
) {
    val general = tooth == DentalChartCodec.GENERAL_KEY
    val lines = state.record.lines(tooth)
    var group by remember { mutableStateOf<String?>(null) }
    ShifaDialogSheet(
        if (general) tr(Res.string.dentalGeneralServices) else "${tr(Res.string.dentalToothServices)} $tooth",
        tr(Res.string.save),
        { actions.open(null) },
    ) {
        if (lines.isNotEmpty()) {
            Text(
                tr(Res.string.dentalSelectedServices),
                style = ShifaTextStyles.caption(),
                color = ShifaColors.Text.Secondary,
            )
            lines.forEachIndexed { i, line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.ControlMd)
                        .padding(start = ShifaSize.S10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        line.title,
                        Modifier.weight(1f),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Primary,
                    )
                    Text(
                        Money.format(line.amountMinor, line.currency),
                        style = ShifaTextStyles.caption(),
                        color = ShifaColors.Text.Primary,
                    )
                    ShifaIconButton(
                        Res.drawable.ic_x_16,
                        tr(Res.string.removeService),
                        { actions.remove(tooth, i) },
                        variant = ShifaIconButtonVariant.Ghost,
                    )
                }
            }
        }
        val services = state.services
        when {
            services == null && state.servicesError != null -> {
                codeBanner(state.servicesError) {}?.invoke()
                ShifaLink(tr(Res.string.retry), actions.retryServices)
            }
            services == null -> ShifaSkeleton(Modifier.fillMaxWidth().heightIn(min = ShifaSize.S48))
            services.isEmpty() ->
                Text(
                    tr(Res.string.dentalNoServices),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            else -> {
                val groups = services.mapNotNull { it.groupName?.takeIf(String::isNotBlank) }.distinct()
                if (groups.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    ) {
                        ShifaChip(tr(Res.string.all), group == null, { group = null })
                        groups.forEach { g -> ShifaChip(g, group == g, { group = g }) }
                    }
                }
                Column(
                    // A long catalogue scrolls inside the sheet.
                    Modifier.heightIn(max = ShifaSize.S240).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    services.filter { group == null || it.groupName == group }.forEach { s ->
                        val line = s.toothLine()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ShifaSize.S48)
                                .clickable(role = Role.Button) { actions.add(tooth, s) }
                                .padding(horizontal = ShifaSize.S10),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
                                Text(
                                    Money.format(line.amountMinor, line.currency),
                                    style = ShifaTextStyles.caption(),
                                    color = ShifaColors.Text.Secondary,
                                )
                            }
                            Icon(painterResource(Res.drawable.ic_plus_18), null, tint = ShifaColors.Text.Brand)
                        }
                    }
                }
            }
        }
    }
}

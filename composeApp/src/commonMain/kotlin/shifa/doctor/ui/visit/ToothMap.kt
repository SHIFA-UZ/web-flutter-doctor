package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.Dentition
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles

/** A tooth in a treatment plan (05 §4.2): nothing planned, planned, partly done, completed. */
enum class ToothPlanState { None, Planned, Partial, Done }

/**
 * The FDI tooth map (DENTAL-01, 05 §4.2; board `DaVisit` "TOOTH MAP", `.dr-teeth`): the upper jaw 18→11 | 21→28 and the
 * lower 48→41 | 31→38 (primary 55→51 | 61→65, 85→81 | 71→75), a gap between the quadrants; each tooth a tile with its
 * number (at the bottom above, at the top below) and a badge with its service count. Free mode marks teeth with
 * services; plan mode colours them by [states]. Small tiles on a phone (all 16 fit), larger ones from 560 dp; it
 * scrolls sideways if even that doesn't fit. [describe] gives a tooth's screen-reader text (its count and plan state:
 * the fill alone says nothing to a screen reader); the selected tooth says so. Null [onTap]: read-only. No dentition
 * toggle or anatomy captions here: the board has neither; the panel that needs the toggle has it (DENTAL-02).
 */
@Composable
fun ToothMap(
    dentition: Dentition,
    counts: Map<String, Int>,
    modifier: Modifier = Modifier,
    states: Map<String, ToothPlanState>? = null,
    selected: String? = null,
    describe: @Composable (
        tooth: String,
        count: Int,
        state: ToothPlanState?,
    ) -> String = { t, c, _ -> if (c > 0) "$t, $c" else t },
    onTap: ((String) -> Unit)? = null,
) = BoxWithConstraints(modifier.fillMaxWidth()) {
    val wide = maxWidth >= ShifaDimens.ToothMapWide
    val w = if (wide) ShifaDimens.ToothWideW else ShifaDimens.ToothW
    val h = if (wide) ShifaDimens.ToothWideH else ShifaDimens.ToothH
    val gap = if (wide) ShifaSize.S4 else ShifaSize.S2
    val quadrantGap = if (wide) ShifaSize.S12 else ShifaSize.S6
    val perQuad = DentalChartCodec.teethPerQuadrant(dentition)
    Column(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = ShifaSize.S8),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            DentalChartCodec.upperTeeth(dentition) to true,
            DentalChartCodec.lowerTeeth(dentition) to false,
        ).forEach {
                (teeth, upper) ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                teeth.forEachIndexed { i, tooth ->
                    if (i == perQuad) Box(Modifier.width(quadrantGap))
                    val count = counts[tooth] ?: 0
                    val state = states?.get(tooth)
                    Tooth(
                        tooth, count, upper, w, h, state, states == null, tooth == selected,
                        describe(
                            tooth,
                            count,
                            state,
                        ),
                        onTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tooth(
    number: String,
    count: Int,
    upper: Boolean,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    state: ToothPlanState?,
    free: Boolean,
    selected: Boolean,
    description: String,
    onTap: ((String) -> Unit)?,
) {
    // Rounder at the root end (`.dr-tooth`'s radii, flipped for the lower jaw).
    val crown = ShifaDimens.ToothCrownRadius
    val root = ShifaDimens.ToothRootRadius
    val shape =
        if (upper) {
            RoundedCornerShape(
                crown,
                crown,
                root,
                root,
            )
        } else {
            RoundedCornerShape(root, root, crown, crown)
        }
    // Free mode: a tooth with services looks done (the board's "Services added").
    val look =
        when {
            !free -> state ?: ToothPlanState.None
            count > 0 -> ToothPlanState.Done
            else -> ToothPlanState.None
        }
    val (fill, edge, ink) =
        when (look) {
            ToothPlanState.None ->
                Triple(
                    Brush.verticalGradient(listOf(ShifaColors.Bg.Surface, ShifaColors.Bg.Surface)),
                    ShifaColors.Border.Default,
                    ShifaColors.Text.Secondary,
                )
            ToothPlanState.Planned ->
                Triple(
                    Brush.verticalGradient(listOf(ShifaColors.Bg.InfoSubtle, ShifaColors.Bg.InfoSubtle)),
                    ShifaColors.Icon.Info,
                    ShifaColors.Text.Primary,
                )
            ToothPlanState.Done ->
                Triple(
                    Brush.verticalGradient(listOf(ShifaColors.Interactive.Primary, ShifaColors.Interactive.Primary)),
                    Color.Transparent,
                    ShifaColors.Interactive.OnPrimary,
                )
            // Half filled from the bottom (`.is-partial`).
            ToothPlanState.Partial ->
                Triple(
                    Brush.verticalGradient(0.5f to ShifaColors.Bg.Surface, 0.5f to ShifaColors.Interactive.Primary),
                    ShifaColors.Border.Brand,
                    ShifaColors.Text.Primary,
                )
        }
    Box(Modifier.size(w, h)) {
        Box(
            Modifier
                .size(w, h)
                .clip(shape)
                .background(fill)
                .border(ShifaSize.S2, if (selected) ShifaColors.Border.Focus else edge, shape)
                .then(if (onTap != null) Modifier.clickable(role = Role.Button) { onTap(number) } else Modifier)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }
                .padding(vertical = ShifaSize.S2),
            contentAlignment = if (upper) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            Text(number, style = ShifaTextStyles.caption(), color = ink, textAlign = TextAlign.Center, maxLines = 1)
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(ShifaSize.S6, -ShifaSize.S6)
                    .widthIn(min = ShifaSize.S16)
                    .height(ShifaSize.S16)
                    .background(ShifaColors.Text.Primary, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = ShifaSize.S4),
                contentAlignment = Alignment.Center,
            ) {
                Text("$count", style = ShifaTextStyles.badgeCount(), color = ShifaColors.Text.Inverse, maxLines = 1)
            }
        }
    }
}

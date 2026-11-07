package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

// `.dr-tooth` states: untouched, planned (blue), partly done (half teal), done (teal).
enum class ToothState { None, Planned, Partial, Done }

/** What [DentalChart] shows on a tooth: its state, procedures count badge, and the selected ring. */
data class ToothMark(
    val state: ToothState = ToothState.None,
    val count: Int = 0,
)

// FDI order as the chart reads it: upper right 18→11 | upper left 21→28, lower right 48→41 | lower left 31→38.
private val UPPER = listOf((18 downTo 11).toList(), (21..28).toList())
private val LOWER = listOf((48 downTo 41).toList(), (31..38).toList())

/**
 * DentalChart (`.dr-teeth`, scroll mode on phones): the 32 permanent teeth in FDI numbering, one chart for the
 * visit, the plan and Shifa AI. Teeth are 36×40, the arches scroll sideways; [legend] names the states below.
 * [describe] gives each tooth's screen-reader text ("Tooth 16, planned, 2 procedures").
 */
@Composable
fun DentalChart(
    marks: Map<Int, ToothMark>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onTooth: ((Int) -> Unit)? = null,
    legend: List<Pair<ToothState, String>> = emptyList(),
    describe: (Int) -> String = { "$it" },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
        Column(
            Modifier.horizontalScroll(
                rememberScrollState(),
            ).padding(horizontal = ShifaSize.S4, vertical = ShifaSize.S8),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        ) {
            Arch(UPPER, upper = true, marks, selected, onTooth, describe)
            Arch(LOWER, upper = false, marks, selected, onTooth, describe)
        }
        if (legend.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md, Alignment.CenterHorizontally),
            ) {
                legend.forEach { (state, label) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(ShifaSize.S12, ShifaSize.S14).toothFill(state, ShifaRadius.Xs))
                        Text(label, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Arch(
    halves: List<List<Int>>,
    upper: Boolean,
    marks: Map<Int, ToothMark>,
    selected: Int?,
    onTooth: ((Int) -> Unit)?,
    describe: (Int) -> String,
) {
    // Upper crowns round at the bottom (roots up), lower ones at the top.
    val shape =
        if (upper) {
            RoundedCornerShape(ShifaSize.S10, ShifaSize.S10, ShifaSize.S16, ShifaSize.S16)
        } else {
            RoundedCornerShape(ShifaSize.S16, ShifaSize.S16, ShifaSize.S10, ShifaSize.S10)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4)) {
        halves.forEachIndexed { h, teeth ->
            if (h == 1) Spacer(Modifier.width(ShifaSpacing.Gap.Md - ShifaSize.S4))
            teeth.forEach { n -> Tooth(n, marks[n] ?: ToothMark(), n == selected, upper, shape, onTooth, describe(n)) }
        }
    }
}

@Composable
private fun Tooth(
    number: Int,
    mark: ToothMark,
    selected: Boolean,
    upper: Boolean,
    shape: Shape,
    onTooth: ((Int) -> Unit)?,
    description: String,
) {
    Box(Modifier.size(ShifaSize.S36, ShifaSize.S40)) {
        Box(
            Modifier
                .matchParent()
                .then(
                    if (selected) {
                        Modifier.background(
                            ShifaColors.Alpha.Teal25,
                            shape,
                        ).padding(ShifaSize.S1)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .toothFill(mark.state, shape)
                .then(if (selected) Modifier.border(ShifaSize.S2, ShifaColors.Border.Focus, shape) else Modifier)
                .then(
                    if (onTooth != null) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button) {
                            onTooth(number)
                        }
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = description }
                .padding(vertical = ShifaSize.S4),
            contentAlignment = if (upper) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            val ink =
                if (mark.state == ToothState.Done) {
                    ShifaColors.Interactive.OnPrimary
                } else if (mark.state == ToothState.None) {
                    ShifaColors.Text.Secondary
                } else {
                    ShifaColors.Text.Primary
                }
            Text("$number", style = ShifaTextStyles.toothNumber(), color = ink)
        }
        if (mark.count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = ShifaSize.S6, y = -ShifaSize.S6)
                    .defaultMinSize(minWidth = ShifaSize.S16)
                    .height(ShifaSize.S16)
                    .background(ShifaColors.Text.Primary, ShifaRadius.Full)
                    .padding(horizontal = ShifaSize.S4),
                contentAlignment = Alignment.Center,
            ) {
                Text("${mark.count}", style = ShifaTextStyles.badgeCount(), color = ShifaColors.Text.Inverse)
            }
        }
    }
}

private fun Modifier.matchParent() = fillMaxWidth().height(ShifaSize.S40)

// Fill and border per state; partial is half teal from the bottom.
private fun Modifier.toothFill(
    state: ToothState,
    shape: Shape,
): Modifier =
    when (state) {
        ToothState.None ->
            background(
                ShifaColors.Bg.Surface,
                shape,
            ).border(ShifaSize.S2, ShifaColors.Border.Default, shape)
        ToothState.Planned ->
            background(
                ShifaColors.Bg.InfoSubtle,
                shape,
            ).border(ShifaSize.S2, ShifaColors.Icon.Info, shape)
        ToothState.Done -> background(ShifaColors.Interactive.Primary, shape)
        ToothState.Partial ->
            background(
                Brush.verticalGradient(
                    0f to ShifaColors.Bg.Surface,
                    0.5f to ShifaColors.Bg.Surface,
                    0.5f to ShifaColors.Interactive.Primary,
                    1f to ShifaColors.Interactive.Primary,
                ),
                shape,
            ).border(ShifaSize.S2, ShifaColors.Border.Brand, shape)
    }

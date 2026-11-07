package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox

enum class ShifaChipType { Filter, Slot }

/** Figma interactive-chip. Content is centred, so a chip given a width (e.g. in [ShifaChipGrid]) fills it. */
@Composable
fun ShifaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ShifaChipType = ShifaChipType.Filter,
) {
    val slot = type == ShifaChipType.Slot
    val style =
        (if (slot) ShifaTextStyles.chipSlot() else ShifaTextStyles.chipFilter())
            .let { if (selected) it.copy(fontWeight = FontWeight.Bold) else it }
    val (bg, content) =
        when {
            !selected -> ShifaColors.Bg.Canvas to ShifaColors.Text.Primary
            slot -> ShifaColors.Interactive.Primary to ShifaColors.Text.Inverse
            else -> ShifaColors.Bg.SuccessSubtle to ShifaColors.Text.Brand
        }
    Row(
        modifier
            .clip(ShifaRadius.Full)
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(ShifaDimens.InteractiveChipBorder, ShifaColors.Border.Focus, ShifaRadius.Full)
                } else {
                    Modifier
                },
            ).pointerHoverIcon(PointerIcon.Hand).selectable(selected, onClick = onClick)
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Stack.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaDimens.InteractiveChipIconGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.lineBox(style), style = style, color = content, maxLines = 1)
    }
}

/** Figma chip-grid: 4 equal columns, each cell stretches its content to the column width. */
@Composable
fun <T> ShifaChipGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit,
) {
    val columns = 4
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Sm)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
                row.forEach { Box(Modifier.weight(1f), propagateMinConstraints = true) { cell(it) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Figma carousel: horizontally scrolling row of slots. Figma uses 4dp for chips, 8dp for cards. */
@Composable
fun ShifaCarousel(
    modifier: Modifier = Modifier,
    spacing: Dp = ShifaSpacing.Gap.Xs,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(spacing),
    content = content,
)

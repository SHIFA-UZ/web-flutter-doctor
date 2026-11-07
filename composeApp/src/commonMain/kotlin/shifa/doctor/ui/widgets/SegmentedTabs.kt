package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/** A tab of [SegmentedTabs] / [UnderlineTabs]; [count] shows the small count pill. */
data class TabItem(
    val label: String,
    val count: Int? = null,
)

/**
 * `.dr-tabs`: pill segmented tabs (patient directory, notification filters, Day/Week/Month). Scrolls sideways when
 * it overflows. [onSurface] uses the canvas track inside white panels. [block] is the full-width form control:
 * equal cells whose labels wrap instead of spilling.
 */
@Composable
fun SegmentedTabs(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSurface: Boolean = false,
    block: Boolean = false,
) {
    val track = if (onSurface || block) ShifaColors.Bg.Canvas else ShifaColors.Bg.Disabled
    val shape = if (block) ShifaRadius.ControlMd else ShifaRadius.Full
    Row(
        modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()))
            .clip(shape)
            .background(track)
            .padding(ShifaSize.S4),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSelected = i == selected
            val cellShape = if (block) ShifaRadius.Sm else ShifaRadius.Full
            Row(
                (if (block) Modifier.weight(1f) else Modifier)
                    .defaultMinSize(minHeight = if (block) ShifaSize.S40 else ShifaSize.S32)
                    .then(if (isSelected) Modifier.dropShadow(cellShape, ShifaShadow.Dropdown) else Modifier)
                    .clip(cellShape)
                    .background(if (isSelected) ShifaColors.Bg.Surface else Color.Transparent)
                    .pointerHoverIcon(
                        PointerIcon.Hand,
                    ).selectable(isSelected, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(
                        horizontal = if (block) ShifaSize.S6 else ShifaSpacing.Inset.Md,
                        vertical = if (block) ShifaSize.S4 else ShifaSpacing.Inset.Xs,
                    ),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = if (block) ShifaTextStyles.kpiLabel() else ShifaTextStyles.tabLabel()
                Text(
                    tab.label,
                    style = style.copy(fontWeight = if (isSelected) FontWeight.Bold else style.fontWeight),
                    color = if (isSelected) ShifaColors.Text.Primary else ShifaColors.Text.Secondary,
                    textAlign = TextAlign.Center,
                    maxLines = if (block) 2 else 1,
                )
                tab.count?.let { Count(it, isSelected) }
            }
        }
    }
}

/** `.dr-underline`: underline tabs (patient record sections). Scrolls sideways; no more than six sections. */
@Composable
fun UnderlineTabs(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rail = ShifaColors.Border.Default
    val focus = ShifaColors.Border.Focus
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val h = ShifaSize.S2.toPx()
                drawRect(rail, Offset(0f, size.height - h), Size(size.width, h))
            }.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSelected = i == selected
            Row(
                Modifier
                    .defaultMinSize(minWidth = ShifaSize.Touch, minHeight = ShifaSize.Touch)
                    .drawBehind {
                        if (isSelected) {
                            val h = ShifaSize.S2.toPx()
                            drawRect(focus, Offset(0f, size.height - h), Size(size.width, h))
                        }
                    }.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).selectable(isSelected, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(top = ShifaSpacing.Stack.Sm, bottom = ShifaSpacing.Inset.Sm),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = ShifaTextStyles.tabLabel()
                Text(
                    tab.label,
                    style = style.copy(fontWeight = if (isSelected) FontWeight.Bold else style.fontWeight),
                    color = if (isSelected) ShifaColors.Text.Primary else ShifaColors.Text.Secondary,
                    maxLines = 1,
                )
                tab.count?.let { Count(it, false) }
            }
        }
    }
}

@Composable
private fun Count(
    n: Int,
    selected: Boolean,
) = Box(
    Modifier
        .clip(ShifaRadius.Full)
        .background(if (selected) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Disabled)
        .padding(horizontal = ShifaSize.S6, vertical = ShifaSize.S2),
) {
    Text(
        n.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) ShifaColors.Text.Primary else ShifaColors.Text.Secondary,
    )
}

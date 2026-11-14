package shifa.doctor.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_12
import shifa.doctor.generated.resources.ic_chev_d_20
import shifa.doctor.generated.resources.ic_chev_u_20
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.lineBox

/**
 * Figma dropdown. The menu floats over the page with `shadow-dropdown` (design review D13): opening it never changes
 * the form's height. As in Figma, the header shows [label] while expanded and the [selected] option once collapsed.
 * Picking an option collapses it. [onSelect] gets the option's index, so two options may share a label.
 */
@Composable
fun ShifaDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (index: Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val style = MaterialTheme.typography.bodyLarge
    // The menu is as wide as its field.
    var widthPx by remember { mutableStateOf(0) }
    val width = with(LocalDensity.current) { widthPx.toDp() }
    Box(modifier.onSizeChanged { widthPx = it.width }) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ShifaRadius.ControlMd)
                .background(ShifaColors.Bg.Surface)
                .border(
                    ShifaSize.S2,
                    when {
                        isError -> ShifaColors.Border.Error
                        expanded -> ShifaColors.Border.Focus
                        else -> ShifaColors.Border.Default
                    },
                    ShifaRadius.ControlMd,
                ).pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.DropdownList) { onExpandedChange(!expanded) }
                .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val value = selected.takeUnless { expanded }
            Box(Modifier.weight(1f).height(ShifaSize.S30), contentAlignment = Alignment.CenterStart) {
                Text(
                    value ?: label,
                    Modifier.lineBox(style),
                    style = style,
                    color = if (value == null) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(if (expanded) Res.drawable.ic_chev_u_20 else Res.drawable.ic_chev_d_20),
                contentDescription = null,
                tint = ShifaColors.Icon.Secondary,
            )
        }
        // Material's menu places itself under the field, or above it where there is no room below. A long list (times
        // of day) scrolls inside a capped height and opens at the chosen value. (No lazy list: the menu measures its
        // content's intrinsic size, which lazy lists don't have.)
        val scroll = rememberScrollState()
        val rowPx = with(LocalDensity.current) { ShifaSize.S40.roundToPx() }
        LaunchedEffect(expanded) {
            if (expanded) scroll.scrollTo(rowPx * (options.indexOf(selected) - 1).coerceAtLeast(0))
        }
        DropdownMenu(
            expanded,
            { onExpandedChange(false) },
            Modifier.width(width).heightIn(max = ShifaDimens.DropdownMenuMaxHeight),
            offset = DpOffset.Zero.copy(y = ShifaSpacing.Gap.Xs),
            scrollState = scroll,
            shape = ShifaRadius.ControlMd,
            containerColor = ShifaColors.Bg.Surface,
            tonalElevation = Dp.Hairline,
            shadowElevation = ShifaSize.S6,
            border = BorderStroke(ShifaSize.S2, ShifaColors.Border.Default),
        ) {
            options.forEachIndexed { i, option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .selectable(option == selected, role = Role.RadioButton) {
                            onSelect(i)
                            onExpandedChange(false)
                        }.background(if (option == selected) ShifaColors.Bg.Selected else ShifaColors.Bg.Surface)
                        .defaultMinSize(minHeight = ShifaSize.S40)
                        .padding(horizontal = ShifaSpacing.Inset.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        Modifier.weight(1f).lineBox(style),
                        style = style,
                        color = ShifaColors.Text.Primary,
                    )
                    if (option == selected) {
                        Icon(painterResource(Res.drawable.ic_check_12), null, tint = ShifaColors.Icon.Brand)
                    }
                }
            }
        }
    }
}

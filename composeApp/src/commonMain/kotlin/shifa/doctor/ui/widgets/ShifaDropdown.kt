package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_12
import shifa.doctor.generated.resources.ic_chev_d_20
import shifa.doctor.generated.resources.ic_chev_u_20
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.lineBox

/**
 * Figma dropdown. Expands inline (the menu pushes content below). As in Figma, the header shows
 * [label] while expanded and the [selected] option once collapsed. Picking an option collapses it.
 * [onSelect] gets the option's index, so two options may share a label.
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
    Column(
        if (expanded) {
            modifier
                .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Dropdown)
                .clip(ShifaRadius.ControlMd)
                .background(ShifaColors.Bg.Surface)
                .border(ShifaSize.S2, ShifaColors.Border.Default, ShifaRadius.ControlMd)
                .padding(bottom = ShifaDimens.DropdownMenuPaddingBottom)
        } else {
            modifier
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ShifaRadius.ControlMd)
                .background(ShifaColors.Bg.Surface)
                .border(
                    ShifaSize.S2,
                    if (isError) ShifaColors.Border.Error else ShifaColors.Border.Default,
                    ShifaRadius.ControlMd,
                ).clickable(role = Role.DropdownList) { onExpandedChange(!expanded) }
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
        if (expanded) {
            // A long list (times of day) scrolls inside a capped height and opens at the chosen value.
            val list = rememberLazyListState((options.indexOf(selected) - 1).coerceAtLeast(0))
            LazyColumn(Modifier.heightIn(max = ShifaDimens.DropdownMenuMaxHeight), state = list) {
                itemsIndexed(options) { i, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(option == selected, role = Role.RadioButton) {
                                onSelect(i)
                                onExpandedChange(false)
                            }
                            // Gap above each option is part of its touch target.
                            .padding(
                                start = ShifaSpacing.Inset.Sm,
                                end = ShifaSpacing.Inset.Sm,
                                top = ShifaSpacing.Gap.Md,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (option == selected) {
                            Icon(painterResource(Res.drawable.ic_check_12), null, tint = ShifaColors.Icon.Brand)
                        }
                        Text(option, Modifier.lineBox(style), style = style, color = ShifaColors.Text.Primary)
                    }
                }
            }
        }
    }
}

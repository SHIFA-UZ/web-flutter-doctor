package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_14
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaLetterSpacing
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/** `.dr-box`: the 20 checkbox mark (off, on, mixed). Pair it with [ShifaCheckboxRow] for a tappable row. */
@Composable
fun ShifaCheckbox(
    state: ToggleableState,
    modifier: Modifier = Modifier,
) {
    val on = state != ToggleableState.Off
    Box(
        modifier
            .size(ShifaSize.S20)
            .clip(ShifaRadius.Sm)
            .background(if (on) ShifaColors.Interactive.Primary else ShifaColors.Bg.Surface)
            .then(if (on) Modifier else Modifier.border(ShifaSize.S2, ShifaColors.Border.Disabled, ShifaRadius.Sm)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> Icon(painterResource(Res.drawable.ic_check_14), null, tint = ShifaColors.Icon.Inverse)
            ToggleableState.Indeterminate ->
                Box(Modifier.size(ShifaSize.S10, ShifaSize.S2).background(ShifaColors.Icon.Inverse, ShifaRadius.Full))
            ToggleableState.Off -> Unit
        }
    }
}

/** `.dr-check` as a row: the checkbox carries the state, the row never gets a selected tint. */
@Composable
fun ShifaCheckboxRow(
    label: String,
    state: ToggleableState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .defaultMinSize(minHeight = ShifaSize.Touch)
            .clip(ShifaRadius.ControlMd)
            .triStateToggleable(state, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = ShifaSpacing.Inset.Xs),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaCheckbox(state)
        Text(label, style = ShifaTextStyles.tabLabel(), color = ShifaColors.Text.Primary)
    }
}

/** `.dr-radio`: the 20 radio mark; the "on" ring is 6 thick. */
@Composable
fun ShifaRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .size(ShifaSize.S20)
        .clip(ShifaRadius.Full)
        .background(ShifaColors.Bg.Surface)
        .border(
            if (selected) ShifaSize.S6 else ShifaSize.S2,
            if (selected) ShifaColors.Interactive.Primary else ShifaColors.Border.Disabled,
            ShifaRadius.Full,
        ),
)

/**
 * `.dr-choice`: a bordered option (place, gender, input type, template) with an optional [description] and
 * leading mark. Selected: focus ring on teal-subtle.
 */
@Composable
fun ShifaChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    radio: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.ControlLg)
            .clip(ShifaRadius.ControlMd)
            .background(if (selected) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Surface)
            .border(
                ShifaSize.S2,
                if (selected) ShifaColors.Border.Focus else ShifaColors.Border.Default,
                ShifaRadius.ControlMd,
            )
            .selectable(selected, role = if (radio) Role.RadioButton else Role.Button, onClick = onClick)
            .padding(ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (radio) ShifaRadio(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ShifaColors.Text.Primary)
            description?.let { Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary) }
        }
    }
}

/** A row of [ShifaOptionList]: `.sh-option` / `.dr-menu__item`. */
data class OptionItem(
    val label: String,
    val icon: DrawableResource? = null,
    val selected: Boolean = false,
    val danger: Boolean = false,
    // A small colour dot before the label (staff calendars).
    val dot: Color? = null,
    // Draws a separator above this row.
    val separatorBefore: Boolean = false,
)

/**
 * OptionList (`.sh-option`): every picker and menu row. On phones a menu is this list inside a `ShifaTray`.
 * [heading] is the menu's uppercase caption. Selected rows are bold on `bg-selected` with a teal check.
 */
@Composable
fun ShifaOptionList(
    items: List<OptionItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    heading: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        heading?.let {
            Text(
                it.uppercase(),
                Modifier.padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
                style =
                    ShifaTextStyles.caption().copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = ShifaLetterSpacing.Caps,
                    ),
                color = ShifaColors.Text.Tertiary,
            )
        }
        items.forEachIndexed { i, item ->
            if (item.separatorBefore) {
                HorizontalDivider(Modifier.padding(vertical = ShifaSize.S4), ShifaSize.S1, ShifaColors.Border.Default)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ShifaSize.Touch)
                    .clip(ShifaRadius.ControlMd)
                    .background(if (item.selected) ShifaColors.Bg.Selected else Color.Transparent)
                    .clickable(role = Role.Button, onClick = { onSelect(i) })
                    .semantics { selected = item.selected }
                    .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.dot?.let { Box(Modifier.size(ShifaSize.S8).background(it, ShifaRadius.Full)) }
                item.icon?.let {
                    Icon(
                        painterResource(it),
                        null,
                        tint = if (item.danger) ShifaColors.Icon.Error else ShifaColors.Icon.Secondary,
                    )
                }
                Text(
                    item.label,
                    Modifier.weight(1f),
                    style =
                        ShifaTextStyles.bodyText().copy(
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (item.danger) ShifaColors.Text.Error else ShifaColors.Text.Primary,
                )
                if (item.selected) Icon(painterResource(Res.drawable.ic_check_16), null, tint = ShifaColors.Icon.Brand)
            }
        }
    }
}

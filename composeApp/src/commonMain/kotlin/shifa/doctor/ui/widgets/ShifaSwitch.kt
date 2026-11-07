package shifa.doctor.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles

/** Figma Switch: Property 1=Default is off, Variant2 is on. [onCheckedChange] null draws it only (a row toggles it). */
@Composable
fun ShifaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(if (checked) ShifaColors.Icon.Brand else ShifaColors.Icon.Disabled)
    val bias by animateFloatAsState(if (checked) 1f else -1f)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(ShifaDimens.SwitchWidth, ShifaSize.S22)
            .clip(ShifaRadius.Full)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).toggleable(
                        checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = ShifaSize.S4),
        contentAlignment = BiasAlignment(bias, 0f),
    ) {
        Box(Modifier.size(ShifaDimens.SwitchKnob).background(ShifaColors.Icon.Inverse, ShifaRadius.Full))
    }
}

/**
 * A labelled switch: the whole row toggles it and is read out as the switch with its label (a bare switch had no
 * name for screen readers).
 */
@Composable
fun ShifaSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier.fillMaxWidth().pointerHoverIcon(
        PointerIcon.Hand,
    ).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
    ShifaSwitch(checked, null)
}

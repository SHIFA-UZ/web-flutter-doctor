package shifa.doctor.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_star_24
import shifa.doctor.generated.resources.ic_star_filled_24
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing

/**
 * Figma's five stars: [rating] filled amber, the rest outlined. With [onRate], each star is a 44 dp radio
 * (role, selected state, [describe] as its label) and a tap sets 1 to 5.
 */
@Composable
fun ShifaRating(
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = ShifaSize.S16,
    onRate: ((Int) -> Unit)? = null,
    describe: (Int) -> String = { "$it" },
) = Row(
    // Read-only: one label for the row ("4 out of 5" from [describe]); interactive: a radio group.
    if (onRate != null) {
        modifier.selectableGroup()
    } else {
        modifier.clearAndSetSemantics {
            contentDescription = describe(rating)
        }
    },
    horizontalArrangement = if (onRate != null) Arrangement.Start else Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
) {
    (1..5).forEach { star ->
        val filled = star <= rating
        val icon = if (filled) Res.drawable.ic_star_filled_24 else Res.drawable.ic_star_24
        val tint = if (filled) ShifaColors.Icon.Warning else ShifaColors.Icon.Primary
        if (onRate == null) {
            Icon(painterResource(icon), null, Modifier.size(size), tint = tint)
        } else {
            Box(
                Modifier
                    .size(ShifaSize.Touch)
                    .clip(ShifaRadius.Full)
                    .pointerHoverIcon(
                        PointerIcon.Hand,
                    ).selectable(star == rating, role = Role.RadioButton, onClick = { onRate(star) })
                    .semantics { contentDescription = describe(star) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, Modifier.size(size), tint = tint)
            }
        }
    }
}

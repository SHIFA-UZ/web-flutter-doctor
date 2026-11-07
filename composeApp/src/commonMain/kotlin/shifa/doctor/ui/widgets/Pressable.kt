package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize

// `.sh-press` variants: Row (no fill), Card (white), Tile (canvas), Tint (teal-subtle), Brand (teal-subtle, teal ring).
enum class PressVariant { Row, Card, Tile, Tint, Brand }

/**
 * Pressable (`.sh-press`): the shared base for anything tappable that is not a button — rows, cards, tiles. At least
 * 44 tall on phones; [selected] gives the choice look (focus ring on teal-subtle); disabled is 55 % opacity.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    variant: PressVariant = PressVariant.Row,
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
): Modifier {
    val card = variant == PressVariant.Card || variant == PressVariant.Brand
    val shape = if (card) ShifaRadius.ControlLg else ShifaRadius.ControlMd
    val fill =
        when {
            selected -> ShifaColors.Bg.SuccessSubtle
            variant == PressVariant.Card -> ShifaColors.Bg.Surface
            variant == PressVariant.Tile -> ShifaColors.Bg.Canvas
            variant == PressVariant.Tint || variant == PressVariant.Brand -> ShifaColors.Bg.SuccessSubtle
            else -> null
        }
    return this
        .defaultMinSize(minHeight = ShifaSize.Touch)
        .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
        .clip(shape)
        .then(fill?.let { Modifier.background(it) } ?: Modifier)
        .then(
            if (selected || variant == PressVariant.Brand) {
                Modifier.border(
                    ShifaSize.S2,
                    if (selected) ShifaColors.Border.Focus else ShifaColors.Border.Brand,
                    shape,
                )
            } else {
                Modifier
            },
        ).pointerHoverIcon(PointerIcon.Hand).clickable(enabled = enabled, role = role, onClick = onClick)
}

private const val DISABLED_ALPHA = 0.55f // `.sh-press:disabled { opacity: .55 }`

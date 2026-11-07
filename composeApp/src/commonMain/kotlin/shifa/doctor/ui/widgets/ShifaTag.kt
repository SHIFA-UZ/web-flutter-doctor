package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

enum class ShifaTagType(internal val bg: Color, internal val text: Color) {
    Primary(ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.Brand),
    Secondary(ShifaColors.Bg.InfoSubtle, ShifaColors.Text.Info),
    Tertiary(ShifaColors.Bg.WarningSubtle, ShifaColors.Text.Warning),
}

/** `.sh-tag`: a 28 tall label pill (location, "Video", a duration). [icon] is a 12 icon before the text. */
@Composable
fun ShifaTag(
    text: String,
    modifier: Modifier = Modifier,
    type: ShifaTagType = ShifaTagType.Secondary,
    icon: DrawableResource? = null,
) {
    val style = ShifaTextStyles.tag()
    Row(
        modifier
            .height(ShifaSize.S28)
            .background(type.bg, ShifaRadius.Full)
            .padding(horizontal = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), null, tint = type.text) }
        Text(text, style = style, color = type.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

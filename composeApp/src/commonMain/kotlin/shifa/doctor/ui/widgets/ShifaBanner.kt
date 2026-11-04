package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dismiss
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.utils.tr

enum class ShifaBannerType(
    internal val bg: Color,
    internal val border: Color,
    internal val tint: Color,
) {
    Error(ShifaColors.Bg.ErrorSubtle, ShifaColors.Border.Error, ShifaColors.Icon.Error),
    Warning(ShifaColors.Bg.WarningSubtle, ShifaColors.Border.Warning, ShifaColors.Icon.Warning),
    Success(ShifaColors.Bg.SuccessSubtle, ShifaColors.Border.Success, ShifaColors.Icon.Brand),
}

/**
 * Inline form feedback (errors, lockout, notices). No Figma component: styled like the feedback
 * notification. The Flutter app uses SnackBars; inline stays until the next edit.
 */
@Composable
fun ShifaBanner(
    text: String,
    icon: DrawableResource,
    type: ShifaBannerType,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(type.bg)
            .border(ShifaSize.S2, type.border, ShifaRadius.ControlMd)
            .padding(ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = type.tint)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ShifaColors.Text.Primary)
        onClose?.let {
            ShifaIconButton(Res.drawable.ic_x_16, tr(Res.string.dismiss), it, variant = ShifaIconButtonVariant.Ghost)
        }
    }
}

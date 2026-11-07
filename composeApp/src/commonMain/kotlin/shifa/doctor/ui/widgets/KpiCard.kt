package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_trend_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

// `.dr-kpi__icon` tones.
enum class KpiTone(
    internal val bg: Color,
    internal val icon: Color,
) {
    Brand(ShifaColors.Bg.SuccessSubtle, ShifaColors.Icon.Brand),
    Info(ShifaColors.Bg.InfoSubtle, ShifaColors.Icon.Info),
    Warn(ShifaColors.Bg.WarningSubtle, ShifaColors.Icon.Warning),
    Err(ShifaColors.Bg.ErrorSubtle, ShifaColors.Icon.Error),
}

/**
 * `.dr-kpi`: a KPI tile (Home, Clinic, Finance, Tasks). Value in tabular numerals, 24/28 at phone width.
 * [delta] with [deltaUp] draws the trend arrow (teal up, red down) and [deltaNote] the muted "vs last week".
 */
@Composable
fun KpiCard(
    label: String,
    value: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    tone: KpiTone = KpiTone.Brand,
    delta: String? = null,
    deltaUp: Boolean = true,
    deltaNote: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(ShifaRadius.ControlLg)
            .background(ShifaColors.Bg.Surface)
            .then(
                if (selected) {
                    Modifier.border(
                        ShifaSize.S2,
                        ShifaColors.Border.Focus,
                        ShifaRadius.ControlLg,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(ShifaSize.S32).background(tone.bg, ShifaRadius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, tint = tone.icon)
            }
            Text(label, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary, maxLines = 2)
        }
        // Phone width: 24/28 (`.dr-kpi__value` below 560).
        Text(
            value,
            style = ShifaTextStyles.kpiValuePhone(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        delta?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_trend_16),
                    null,
                    if (deltaUp) Modifier else Modifier.graphicsLayer { scaleY = -1f },
                    tint = if (deltaUp) ShifaColors.Icon.Brand else ShifaColors.Icon.Error,
                )
                Text(it, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary)
                deltaNote?.let {
                        note ->
                    Text(note, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Tertiary)
                }
            }
        }
    }
}

enum class ProgressTone { Brand, Warn, Err }

/** `.dr-progress`: a 6 tall bar on the canvas; [fraction] is clamped to 0..1. */
@Composable
fun ShifaProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: ProgressTone = ProgressTone.Brand,
) {
    val fill =
        when (tone) {
            ProgressTone.Brand -> ShifaColors.Interactive.Primary
            ProgressTone.Warn -> ShifaColors.Icon.Warning
            ProgressTone.Err -> ShifaColors.Interactive.Destructive
        }
    Box(modifier.height(ShifaSize.S6).clip(ShifaRadius.Full).background(ShifaColors.Bg.Canvas)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(fill, ShifaRadius.Full))
    }
}

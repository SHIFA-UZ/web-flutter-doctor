package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

// `.dr-status` tones: the pill fill, the word colour, the dot (or icon) colour.
enum class StatusTone(
    internal val bg: Color,
    internal val text: Color,
    internal val dot: Color,
) {
    Neutral(ShifaColors.Bg.Canvas, ShifaColors.Text.Primary, ShifaColors.Text.Primary),
    Brand(ShifaColors.Bg.SuccessSubtle, ShifaColors.Text.BrandStrong, ShifaColors.Icon.Brand),
    Solid(ShifaColors.Interactive.Primary, ShifaColors.Interactive.OnPrimary, ShifaColors.Icon.Inverse),
    Info(ShifaColors.Bg.InfoSubtle, ShifaColors.Text.Info, ShifaColors.Icon.Info),
    Warn(ShifaColors.Bg.WarningSubtle, ShifaColors.Text.WarningStrong, ShifaColors.Icon.Warning),
    Err(ShifaColors.Bg.ErrorSubtle, ShifaColors.Text.Error, ShifaColors.Text.Error),
    Muted(ShifaColors.Bg.Canvas, ShifaColors.Text.Secondary, ShifaColors.Icon.Disabled),
}

/**
 * `.dr-status`: a dot and a word, so status never relies on colour alone. [onClick] makes it a status menu (caret).
 * Give [icon] for the icon-only 28 disc used in tight phone rows; [text] then becomes its content description.
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    icon: DrawableResource? = null,
    onClick: (() -> Unit)? = null,
) {
    if (icon != null) {
        Box(
            modifier
                .size(ShifaSize.S28)
                .clip(ShifaRadius.Full)
                .background(tone.bg)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics { contentDescription = text },
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (tone == StatusTone.Neutral) ShifaColors.Icon.Primary else tone.dot
            Icon(painterResource(icon), null, tint = tint)
        }
        return
    }
    Row(
        modifier
            .height(ShifaSize.S24)
            .clip(ShifaRadius.Full)
            .background(tone.bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(
                start = ShifaSize.S10,
                end = if (onClick != null) ShifaSpacing.Inset.Xs else ShifaSpacing.Inset.Sm,
            ),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ShifaSize.S6).background(tone.dot, ShifaRadius.Full))
        Text(
            text,
            style = ShifaTextStyles.tableHead(),
            color = tone.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) Icon(painterResource(Res.drawable.ic_chev_d_16), null, tint = tone.text)
    }
}

// `.dr-alert` tones: allergies and risks (Err), chronic conditions (Warn), information (Info), AI (Ai), context (Neutral).
enum class AlertTone(
    internal val bg: Color,
    internal val border: Color?,
    internal val icon: Color,
) {
    Err(ShifaColors.Bg.ErrorSubtle, ShifaColors.Border.Error, ShifaColors.Icon.Error),
    Warn(ShifaColors.Bg.WarningSubtle, ShifaColors.Border.Warning, ShifaColors.Icon.Warning),
    Info(ShifaColors.Bg.InfoSubtle, null, ShifaColors.Icon.Info),
    Ai(ShifaColors.Alpha.Teal15, ShifaColors.Border.Brand, ShifaColors.Icon.Brand),
    Neutral(ShifaColors.Bg.Disabled, null, ShifaColors.Icon.Secondary),
}

/** `.dr-alert`: a clinical flag (allergy, chronic disease, risk) on the patient header, Home and the visit. */
@Composable
fun AlertChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: AlertTone = AlertTone.Err,
    icon: DrawableResource? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .height(ShifaSize.S28)
            .clip(ShifaRadius.ControlMd)
            .background(tone.bg)
            .then(tone.border?.let { Modifier.border(ShifaSize.S1, it, ShifaRadius.ControlMd) } ?: Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ShifaSize.S10),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), null, tint = tone.icon) }
        Text(text, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary, maxLines = 1)
    }
}

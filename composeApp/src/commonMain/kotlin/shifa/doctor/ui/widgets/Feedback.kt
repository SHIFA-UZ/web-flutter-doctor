package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.ic_alert_20
import shifa.doctor.generated.resources.ic_alert_circle_20
import shifa.doctor.generated.resources.ic_check_circle_20
import shifa.doctor.generated.resources.ic_chev_d_16
import shifa.doctor.generated.resources.ic_cloud_off_16
import shifa.doctor.generated.resources.ic_info_20
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

enum class ToastTone { Ok, Err, Warn, Info }

/**
 * `.dr-toast`: the one feedback pattern, at the top under the status bar (replaces SnackBars). Charcoal pill,
 * tone icon, the message, an optional [action] and a close button. The host stacks them newest on top.
 */
@Composable
fun ShifaToast(
    message: String,
    modifier: Modifier = Modifier,
    tone: ToastTone = ToastTone.Ok,
    action: String? = null,
    onAction: () -> Unit = {},
    onClose: (() -> Unit)? = null,
) {
    val (icon, tint) =
        when (tone) {
            ToastTone.Ok -> Res.drawable.ic_check_circle_20 to ShifaColors.Raw.SplashLogo
            ToastTone.Err -> Res.drawable.ic_alert_circle_20 to ShifaColors.Bg.ErrorSubtle
            ToastTone.Warn -> Res.drawable.ic_alert_20 to ShifaColors.Icon.Warning
            ToastTone.Info -> Res.drawable.ic_info_20 to ShifaColors.Icon.Inverse
        }
    Row(
        modifier
            .widthIn(max = ShifaDimens.Layout.DialogMax)
            .fillMaxWidth()
            .dropShadow(ShifaRadius.ControlMd, ShifaShadow.Default)
            .background(ShifaColors.Text.Primary, ShifaRadius.ControlMd)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = ShifaSpacing.Inset.Md, end = ShifaSpacing.Inset.Sm)
            .padding(vertical = ShifaSpacing.Inset.Sm),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint)
        Text(message, Modifier.weight(1f), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Inverse)
        action?.let {
            Box(
                Modifier
                    .clip(ShifaRadius.Full)
                    .border(ShifaSize.S1, ShifaColors.Alpha.White70, ShifaRadius.Full)
                    .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
            ) {
                Text(it, style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Inverse, maxLines = 1)
            }
        }
        onClose?.let {
            Box(
                Modifier.size(
                    ShifaSize.Touch,
                ).clip(ShifaRadius.Full).pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_x_16),
                    tr(Res.string.close),
                    tint = ShifaColors.Alpha.White70,
                )
            }
        }
    }
}

/**
 * `.dr-empty`: an empty list or a filtered-empty result. Icon disc, title, body and an optional [action]
 * ("Clear filters", "Add patient"). Not a skeleton: skeletons are for loading.
 */
@Composable
fun EmptyState(
    title: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    ) {
        Box(
            Modifier.size(ShifaSize.S64).background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = ShifaColors.Icon.Brand)
        }
        Text(
            title,
            Modifier.padding(top = ShifaSpacing.Stack.Xs),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            textAlign = TextAlign.Center,
        )
        body?.let {
            Text(
                it,
                Modifier.widthIn(max = ShifaSize.S320),
                style = ShifaTextStyles.dialogBody(),
                color = ShifaColors.Text.Secondary,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

// The disc above a dialog's title: red for a destructive question, teal with a halo for an AI sheet.
enum class DialogTone { None, Destructive, Ai }

/**
 * ResponsiveDialog (`.sh-sheet`, Dialog board) on phones: it docks as a tray, the handle on top, a tone disc, the
 * title and body centred for a question, a [content] slot (a form, a briefing) and the actions stacked with the
 * primary on top. Host it in a `ModalBottomSheet`; it only draws.
 */
@Composable
fun ShifaDialogSheet(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    tone: DialogTone = DialogTone.None,
    icon: DrawableResource? = null,
    cancelText: String? = null,
    onCancel: () -> Unit = {},
    confirmLoading: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val sheetShape = RoundedCornerShape(topStart = ShifaSize.S24, topEnd = ShifaSize.S24)
    val centred = tone == DialogTone.Destructive || content == null
    Column(
        modifier
            .trackActivity()
            .fillMaxWidth()
            .dropShadow(sheetShape, ShifaShadow.Tray)
            .background(ShifaColors.Bg.Surface, sheetShape)
            .navigationBarsPadding()
            .padding(start = ShifaSpacing.Inset.Md, end = ShifaSpacing.Inset.Md, bottom = ShifaSpacing.Inset.Lg),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S20),
    ) {
        Box(
            Modifier
                .padding(top = ShifaSpacing.Inset.Sm)
                .align(Alignment.CenterHorizontally)
                .size(ShifaSize.S36, ShifaSize.S4)
                .background(ShifaColors.Border.Default, ShifaRadius.Full),
        )
        // DialogStates: the body (heading, copy, form) scrolls inside the sheet; the actions below stay put.
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S20),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            ) {
                if (tone != DialogTone.None && icon != null) {
                    val (bg, tint) =
                        if (tone == DialogTone.Destructive) {
                            ShifaColors.Bg.ErrorSubtle to ShifaColors.Icon.Error
                        } else {
                            ShifaColors.Interactive.Primary to ShifaColors.Icon.Inverse
                        }
                    // The AI disc wears a teal halo.
                    Box(
                        if (tone == DialogTone.Ai) {
                            Modifier.background(ShifaColors.Alpha.Teal25, ShifaRadius.Full).padding(ShifaSize.S4)
                        } else {
                            Modifier
                        },
                    ) {
                        Box(
                            Modifier.size(ShifaSize.S48).background(bg, ShifaRadius.Full),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(icon), null, tint = tint)
                        }
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ShifaColors.Text.Primary,
                    textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                )
                body?.let {
                    Text(
                        it,
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Secondary,
                        textAlign = if (centred) TextAlign.Center else TextAlign.Start,
                    )
                }
            }
            content?.invoke(this)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaPrimaryButton(
                confirmText,
                onConfirm,
                Modifier.fillMaxWidth(),
                destructive = tone == DialogTone.Destructive,
                loading = confirmLoading,
                enabled = confirmEnabled,
            )
            cancelText?.let { ShifaSecondaryButton(it, onCancel, Modifier.fillMaxWidth()) }
        }
    }
}

/** Flow 01/19: the slim persistent strip under the app bar while offline ("You're offline. Showing saved data."). */
@Composable
fun OfflineStrip(
    text: String,
    modifier: Modifier = Modifier,
) = Row(
    modifier
        .fillMaxWidth()
        .background(ShifaColors.Bg.WarningSubtle)
        .semantics { liveRegion = LiveRegionMode.Polite }
        .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Xs),
    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(painterResource(Res.drawable.ic_cloud_off_16), null, tint = ShifaColors.Icon.Warning)
    Text(text, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Primary)
}

/**
 * Flow 01/15 "mini toggle": the language pill on the auth screens (EN / UZ / ЎЗ / RU and a chevron). Tapping opens
 * a menu (a `ShifaTray` with a `ShifaOptionList` on phones). [description] is the screen-reader label.
 */
@Composable
fun LanguageToggle(
    code: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier
        .defaultMinSize(minHeight = ShifaSize.Touch)
        .dropShadow(ShifaRadius.Full, ShifaShadow.Dropdown)
        .clip(ShifaRadius.Full)
        .background(ShifaColors.Bg.Surface)
        .border(ShifaSize.S1, ShifaColors.Border.Default, ShifaRadius.Full)
        .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
        .padding(horizontal = ShifaSpacing.Inset.Sm),
    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(code, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary)
    Icon(painterResource(Res.drawable.ic_chev_d_16), null, tint = ShifaColors.Icon.Brand)
}

package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attach
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.ic_plus_18
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.send
import shifa.doctor.ui.theme.ShifaBreakpoint
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

// Trays are visual only: host them in a [ShifaSheetHost].
private val DockedShape = ShifaRadius.Xl.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

/** False inside a [ShifaSheetHost] that shows its content as a centred dialog: all four corners, no grab handle. */
val LocalSheetDocked = staticCompositionLocalOf { true }

// A tray's shape where it is hosted: docked to the bottom edge, or a dialog's four round corners.
internal val TrayShape: Shape
    @Composable get() = if (LocalSheetDocked.current) DockedShape else ShifaRadius.Xl

/**
 * ResponsiveDialog's host (`.sh-sheet`, Dialog board): a bottom tray on phones, and from 560 wide a centred dialog at
 * most `layout-dialog-max` (480) wide. Trays are a phone pattern; a form or a menu on a computer opens as a dialog.
 * [confirmHide] is asked before a drag, a scrim tap or Escape hides it: false keeps it (a form saving, or asking to
 * discard first). The content only draws (`ShifaTray`, `ShifaDialogSheet`, `ShifaPickerSheet`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShifaSheetHost(
    onDismiss: () -> Unit,
    confirmHide: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    // The sheet state keeps its first lambda: read the caller's latest check (busy / changed now, not at first show).
    val confirm by rememberUpdatedState(confirmHide)
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (windowWidth < ShifaBreakpoint.Compact) {
        ModalBottomSheet(
            onDismiss,
            // Full height at once, so a tall tray shows its button instead of stopping half way.
            sheetState =
                rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
                    it != SheetValue.Hidden || confirm()
                }),
            shape = RectangleShape,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) { content() }
    } else {
        Dialog({ if (confirm()) onDismiss() }, DialogProperties(usePlatformDefaultWidth = false)) {
            CompositionLocalProvider(LocalSheetDocked provides false) {
                Box(Modifier.padding(ShifaSpacing.Inset.Lg).widthIn(max = ShifaDimens.Layout.DialogMax)) { content() }
            }
        }
    }
}

/** Figma tray, type = template (and upload: [content] = secondary buttons). */
@Composable
fun ShifaTray(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = TraySheet(TrayShape, modifier) {
    TrayHeader(title, onClose)
    content()
}

/** Figma tray, type = shifa-chat. [chatBar] is usually a [ShifaChatBar]; it fills the space between the buttons. */
@Composable
fun ShifaChatTray(
    onAttach: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    sendEnabled: Boolean = true,
    chatBar: @Composable () -> Unit,
) = TraySheet(TrayShape, modifier) {
    if (suggestions.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
        ) {
            suggestions.forEach {
                ShifaActionButton(
                    { onSuggestion(it) },
                    text = it,
                    variant = ShifaActionVariant.Secondary,
                )
            }
        }
    }
    // The tray sits on the screen's bottom edge, so it pads for the system bar itself. No shadows in the row (1772:17215).
    Row(
        Modifier.navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShifaIconButton(
            Res.drawable.ic_plus_18,
            tr(Res.string.attach),
            onAttach,
            variant = ShifaIconButtonVariant.Secondary,
        )
        Box(Modifier.weight(1f)) { chatBar() }
        ShifaIconButton(
            Res.drawable.ic_send_18,
            tr(Res.string.send),
            onSend,
            enabled = sendEnabled,
        )
    }
}

// Shared by tray and pop-up.
@Composable
internal fun TraySheet(
    shape: Shape,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier
        .fillMaxWidth()
        .dropShadow(shape, ShifaShadow.Tray)
        .background(ShifaColors.Bg.Surface, shape)
        .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Stack.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    content = content,
)

@Composable
internal fun TrayHeader(
    title: String,
    onClose: () -> Unit,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        title,
        Modifier.weight(1f),
        style = ShifaTextStyles.trayTitle(),
        color = ShifaColors.Text.Primary,
        textAlign = TextAlign.Center,
    )
    ShifaActionButton(
        onClose,
        icon = Res.drawable.ic_x_16,
        contentDescription = tr(Res.string.close),
        variant = ShifaActionVariant.Secondary,
    )
}

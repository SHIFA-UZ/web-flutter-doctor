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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attach
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.ic_plus_18
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.send
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

// Trays are visual only. In a ModalBottomSheet pass shape = RectangleShape,
// containerColor = Color.Transparent and dragHandle = null.
private val TrayShape = ShifaRadius.Xl.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

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

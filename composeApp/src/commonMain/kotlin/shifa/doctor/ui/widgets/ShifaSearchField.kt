package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clear
import shifa.doctor.generated.resources.ic_search_16
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.theme.lineBox
import shifa.doctor.utils.tr

/** Figma search-field. active = focused with text, error = isError, disabled = !enabled. */
@Composable
fun ShifaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val style = ShifaTextStyles.chatInput()
    val border =
        when {
            isError -> ShifaColors.Border.Error
            focused -> ShifaColors.Border.Focus
            else -> ShifaColors.Border.Default
        }
    val text =
        when {
            !enabled -> ShifaColors.Text.Disabled
            isError -> ShifaColors.Text.Error
            else -> ShifaColors.Text.Primary
        }
    val filled = enabled && value.isNotEmpty()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = style.copy(color = text),
        cursorBrush = SolidColor(ShifaColors.Border.Focus),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .clip(ShifaRadius.Full)
                    .background(if (enabled) ShifaColors.Bg.Surface else ShifaColors.Bg.Disabled)
                    .border(ShifaSize.S2, border, ShifaRadius.Full)
                    .padding(ShifaSpacing.Inset.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_search_16),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ShifaSpacing.Inset.Xs),
                    tint = if (filled) ShifaColors.Icon.Secondary else ShifaColors.Icon.Disabled,
                )
                Box(Modifier.weight(1f).lineBox(style)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = style,
                            color = if (enabled) ShifaColors.Text.Tertiary else ShifaColors.Text.Disabled,
                        )
                    }
                    innerTextField()
                }
                if (filled) {
                    Icon(
                        painterResource(Res.drawable.ic_x_16),
                        contentDescription = tr(Res.string.clear),
                        modifier = Modifier.clip(ShifaRadius.Full).clickable { onValueChange("") },
                        tint = ShifaColors.Icon.Secondary,
                    )
                }
            }
        },
    )
}

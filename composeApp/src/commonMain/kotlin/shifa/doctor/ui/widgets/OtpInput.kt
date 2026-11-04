package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_backspace_22
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/**
 * `.dr-otp`: the sign-in code, [length] cells of 48×56. One hidden text field takes the digits (paste and SMS
 * autofill work); the cell after the last digit shows the focus ring, every cell turns red on [error].
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: Boolean = false,
    contentDescription: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter(Char::isDigit).take(length)) },
        modifier = modifier.semantics { contentDescription?.let { this.contentDescription = it } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        decorationBox = { inner ->
            // The editor is composed once, invisible over the cells, so typing, paste and SMS autofill reach it.
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
                    repeat(length) { i ->
                        val border =
                            when {
                                error -> ShifaColors.Border.Error
                                i == value.length -> ShifaColors.Border.Focus
                                else -> ShifaColors.Border.Default
                            }
                        Box(
                            Modifier
                                .size(ShifaSize.S48, ShifaSize.S56)
                                .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
                                .border(ShifaSize.S2, border, ShifaRadius.ControlMd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                value.getOrNull(i)?.toString().orEmpty(),
                                style = ShifaTextStyles.otpDigit(),
                                color = ShifaColors.Text.Primary,
                            )
                        }
                    }
                }
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        },
    )
}

/** `.dr-pindots`: one dot per allowed digit, filled for the ones entered. */
@Composable
fun PinDots(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    repeat(total) { i ->
        Box(
            Modifier
                .size(ShifaSize.S14)
                .clip(ShifaRadius.Full)
                .then(
                    if (i < filled) {
                        Modifier.background(ShifaColors.Interactive.Primary)
                    } else {
                        Modifier.border(ShifaSize.S2, ShifaColors.Border.Disabled, ShifaRadius.Full)
                    },
                ),
        )
    }
}

/**
 * `.dr-pinpad`: the app-lock keypad, 64 round keys. Bottom row: [leftKey] (biometrics, or nothing), 0, backspace.
 * The PIN is submitted explicitly (LOCK-01), never on the fourth digit.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    backspaceDescription: String,
    modifier: Modifier = Modifier,
    leftKey: DrawableResource? = null,
    leftKeyDescription: String? = null,
    onLeftKey: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Lg)) {
                row.forEach {
                        d ->
                    Key(
                        onClick = { onDigit(d) },
                    ) { Text(d.toString(), style = ShifaTextStyles.pinKey(), color = ShifaColors.Text.Primary) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Lg)) {
            if (leftKey != null) {
                Key(onLeftKey, ghost = true, description = leftKeyDescription) {
                    Icon(painterResource(leftKey), null, tint = ShifaColors.Icon.Primary)
                }
            } else {
                Spacer(Modifier.size(ShifaSize.S64))
            }
            Key(
                onClick = { onDigit('0') },
            ) { Text("0", style = ShifaTextStyles.pinKey(), color = ShifaColors.Text.Primary) }
            Key(onBackspace, ghost = true, description = backspaceDescription) {
                Icon(painterResource(Res.drawable.ic_backspace_22), null, tint = ShifaColors.Icon.Primary)
            }
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    ghost: Boolean = false,
    description: String? = null,
    content: @Composable () -> Unit,
) = Box(
    Modifier
        .size(ShifaSize.S64)
        .clip(ShifaRadius.Full)
        .background(if (ghost) Color.Transparent else ShifaColors.Bg.Canvas)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { description?.let { contentDescription = it } },
    contentAlignment = Alignment.Center,
) { content() }

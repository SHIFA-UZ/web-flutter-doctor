package shifa.doctor.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.hidePassword
import shifa.doctor.generated.resources.ic_eye_20
import shifa.doctor.generated.resources.ic_eye_off_20
import shifa.doctor.generated.resources.showPassword
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.utils.tr

/** A password field with its own eye toggle (shared by the reset and registration forms). */
@Composable
internal fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    error: StringResource?,
    actions: KeyboardActions,
    ime: ImeAction,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShifaInputField(
    value,
    onValue,
    label,
    Modifier.fillMaxWidth(),
    supportingText = error?.let { tr(it) },
    isError = error != null,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
    keyboardActions = actions,
    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    trailingIcon = {
        ShifaIconButton(
            if (visible) Res.drawable.ic_eye_off_20 else Res.drawable.ic_eye_20,
            tr(if (visible) Res.string.hidePassword else Res.string.showPassword),
            onToggle,
            variant = ShifaIconButtonVariant.Ghost,
        )
    },
    fieldModifier = fieldModifier,
)

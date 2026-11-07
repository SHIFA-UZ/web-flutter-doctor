package shifa.doctor.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.createAccount
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.firstName
import shifa.doctor.generated.resources.lastName
import shifa.doctor.generated.resources.staffTimezoneHint
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.tr

/**
 * AUTH-08 · the receptionist's one-step form (DaAuth `staffReg`, flow 01/10): the clinic and invited address on a card,
 * the address read-only, names, the timezone (their own notifications), password and confirmation, "Create account".
 * When the invitation carries no address (C-4) the field is editable instead of a dead end.
 */
@Composable
internal fun ColumnScope.StaffStep(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val editable = !state.loading
    val clinic = state.invite.clinicName
    if (clinic != null || state.invite.email != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd)
                .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S2),
        ) {
            clinic?.let { Text(it, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary) }
            state.invite.email?.let { Text(it, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary) }
        }
    }
    TextField(
        state.email,
        actions.onEmail,
        tr(Res.string.email),
        state.emailError,
        editable && !state.emailLocked,
        KeyboardType.Email,
    )
    TextField(
        state.firstName,
        actions.onFirstName,
        tr(Res.string.firstName),
        state.firstNameError,
        editable,
        words = true,
    )
    TextField(state.lastName, actions.onLastName, tr(Res.string.lastName), state.lastNameError, editable, words = true)
    TimeZoneField(state, actions, hint = Res.string.staffTimezoneHint)
    Passwords(state, actions, actions.register, editable)
    ShifaPrimaryButton(
        tr(Res.string.createAccount),
        actions.register,
        Modifier.fillMaxWidth(),
        loading = state.loading,
        enabled = !state.completed,
    )
}

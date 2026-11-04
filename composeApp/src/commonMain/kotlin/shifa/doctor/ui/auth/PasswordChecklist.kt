package shifa.doctor.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.passwordRequired
import shifa.doctor.generated.resources.passwordRequirementDigit
import shifa.doctor.generated.resources.passwordRequirementLowercase
import shifa.doctor.generated.resources.passwordRequirementMaxLength
import shifa.doctor.generated.resources.passwordRequirementMinLength
import shifa.doctor.generated.resources.passwordRequirementSpecialChar
import shifa.doctor.generated.resources.passwordRequirementUppercase
import shifa.doctor.generated.resources.passwordTooLong
import shifa.doctor.generated.resources.passwordTooShort
import shifa.doctor.generated.resources.passwordsDoNotMatch
import shifa.doctor.generated.resources.pleaseConfirmPassword
import shifa.doctor.generated.resources.requirementMet
import shifa.doctor.generated.resources.requirementNotMet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.PasswordPolicy
import shifa.doctor.utils.tr

/** Each backend rule and its line; the length line says "At most 128" once [password] is past the maximum. */
fun PasswordPolicy.Rule.label(password: String): StringResource =
    when (this) {
        PasswordPolicy.Rule.Length ->
            if (password.length > PasswordPolicy.MAX) {
                Res.string.passwordRequirementMaxLength
            } else {
                Res.string.passwordRequirementMinLength
            }
        PasswordPolicy.Rule.Upper -> Res.string.passwordRequirementUppercase
        PasswordPolicy.Rule.Lower -> Res.string.passwordRequirementLowercase
        PasswordPolicy.Rule.Digit -> Res.string.passwordRequirementDigit
        PasswordPolicy.Rule.Special -> Res.string.passwordRequirementSpecialChar
    }

/**
 * The inline errors under a new password and its confirmation (flows 01/05, 01/07 and 01/10 §9): the password's first
 * unmet rule ("Password must be at least 8 characters", "One uppercase letter"…), then "Please confirm your password" /
 * "Passwords do not match". Both are compared exactly as typed: nothing is trimmed.
 */
fun newPasswordErrors(
    password: String,
    confirm: String,
): Pair<StringResource?, StringResource?> {
    val first = PasswordPolicy.missing(password).firstOrNull()
    val passwordError =
        when {
            password.isEmpty() -> Res.string.passwordRequired
            first == PasswordPolicy.Rule.Length && password.length > PasswordPolicy.MAX -> Res.string.passwordTooLong
            first == PasswordPolicy.Rule.Length -> Res.string.passwordTooShort
            else -> first?.label(password)
        }
    val confirmError =
        when {
            confirm.isEmpty() -> Res.string.pleaseConfirmPassword
            confirm != password -> Res.string.passwordsDoNotMatch
            else -> null
        }
    return passwordError to confirmError
}

/**
 * The live checklist under a new password (DaAuth `checklist`, flows 01/05 §3 and 01/07): every rule of the backend's
 * policy, ticked as it is met. Shown once something is typed; a screen reader hears changes politely. Over 128
 * characters the length line reads "At most 128 characters".
 */
@Composable
fun PasswordChecklist(
    password: String,
    modifier: Modifier = Modifier,
) {
    if (password.isEmpty()) return
    val missing = PasswordPolicy.missing(password)
    Column(
        modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
    ) {
        PasswordPolicy.Rule.entries.forEach { rule ->
            val ok = rule !in missing
            val text = rule.label(password)
            // Met or not is said, not only shown by the icon and colour.
            val status = tr(if (ok) Res.string.requirementMet else Res.string.requirementNotMet)
            Row(
                Modifier.semantics(mergeDescendants = true) { stateDescription = status },
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (ok) Res.drawable.ic_check_16 else Res.drawable.ic_x_16),
                    null,
                    tint = if (ok) ShifaColors.Text.BrandStrong else ShifaColors.Text.Tertiary,
                )
                Text(
                    tr(text),
                    style =
                        ShifaTextStyles.metaSmall().copy(
                            fontWeight = if (ok) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (ok) ShifaColors.Text.BrandStrong else ShifaColors.Text.Tertiary,
                )
            }
        }
    }
}

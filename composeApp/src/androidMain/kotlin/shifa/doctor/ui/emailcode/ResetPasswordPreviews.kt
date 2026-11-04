package shifa.doctor.ui.emailcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.passwordMismatch
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode
import shifa.doctor.ui.theme.PreviewTheme

@Composable
private fun Reset(state: ResetPasswordUiState) =
    PreviewTheme { ResetPasswordScreenContent(state, {}, {}, {}, {}, {}, {}, {}) }

// DaAuth "reset" (flow 01/05): the live checklist half met, a mismatch, and a wrong code.
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ResetChecklistPreview() = Reset(ResetPasswordUiState("doctor@shifa.dev", password = "Abcdef1"))

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ResetMismatchPreview() =
    Reset(
        ResetPasswordUiState("doctor@shifa.dev", "Abcdefg1!", "Abcdefg1?", confirmError = Res.string.passwordMismatch),
    )

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ResetWrongCodePreview() =
    Reset(
        ResetPasswordUiState(
            "doctor@shifa.dev",
            "Abcdefg1!",
            "Abcdefg1!",
            banner = CodeBanner(Res.string.wrongOrExpiredCodeBackToCode),
        ),
    )

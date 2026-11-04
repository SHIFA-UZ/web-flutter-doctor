package shifa.doctor.ui.register

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.invalidInviteKey
import shifa.doctor.generated.resources.requestFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.theme.PreviewTheme

// DaAuth "verify" (flow 01/06): empty, and an invalid key.
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun VerifyKeyEmptyPreview() = PreviewTheme { VerifyKeyScreenContent(VerifyKeyUiState(), {}, {}, {}, {}, {}) }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun VerifyKeyInvalidPreview() =
    PreviewTheme {
        VerifyKeyScreenContent(VerifyKeyUiState("SHIFA-DOC-2025", banner = CodeBanner(Res.string.invalidInviteKey)), {
        }, {}, {}, {}, {})
    }

// A server failure while checking: the key is read-only, the banner names the request id (17 §1).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun VerifyKeyFailedPreview() =
    PreviewTheme {
        VerifyKeyScreenContent(
            VerifyKeyUiState("SHIFA-DOC-2025", banner = CodeBanner(Res.string.requestFailed, requestId = "6f1c2a9e")),
            {},
            {},
            {},
            {},
            {},
        )
    }

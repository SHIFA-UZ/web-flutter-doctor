package shifa.doctor.ui.emailcode

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidOtp
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.ui.theme.PreviewTheme

// DaAuth "email" and "otp" branches (flows 01/03, 01/04).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun EmailEntryPreview() =
    PreviewTheme {
        EmailEntryScreenContent(EmailCodeMode.SignIn, EmailEntryUiState("seed.doctor1@shifa.dev"), {}, {}, {}, {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun EmailEntryResetErrorPreview() =
    PreviewTheme {
        EmailEntryScreenContent(EmailCodeMode.Reset, EmailEntryUiState("doctor@shifa", Res.string.invalidEmail), {
        }, {}, {}, {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun CodeCountdownPreview() =
    PreviewTheme { CodeScreenContent(CodeUiState("seed.doctor1@shifa.dev", "482", resendIn = 42), {}, {}, {}, {}, {}) }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun CodeWrongPreview() =
    PreviewTheme {
        CodeScreenContent(CodeUiState("seed.doctor1@shifa.dev", banner = CodeBanner(Res.string.invalidOtp)), {
        }, {}, {}, {}, {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun CodeResentPreview() =
    PreviewTheme {
        CodeScreenContent(
            CodeUiState(
                "seed.doctor1@shifa.dev",
                banner = CodeBanner(Res.string.otpResent, error = false),
            ),
            {
            },
            {},
            {},
            {},
            {},
        )
    }

// AUTH-10: sign in by SMS, the e-mail code screens with a phone number.
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun PhoneEntryPreview() =
    PreviewTheme {
        EmailEntryScreenContent(EmailCodeMode.SmsSignIn, EmailEntryUiState("+998 90 123 45 67"), {}, {}, {}, {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun PhoneCodePreview() =
    PreviewTheme {
        CodeScreenContent(CodeUiState("+998901234567", bySms = true, resendIn = 42), {}, {}, {}, {}, {})
    }

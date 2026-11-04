package shifa.doctor.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.enterEmailOrPhone
import shifa.doctor.generated.resources.invalidCredentials
import shifa.doctor.generated.resources.minimum6Characters
import shifa.doctor.generated.resources.sessionExpiredBanner
import shifa.doctor.ui.theme.PreviewTheme

@Composable
private fun Login(state: LoginUiState) = PreviewTheme { LoginScreenContent(state, {}, {}, {}, {}, {}, {}, {}, {}) }

// DaAuth default branch: empty, validation errors, signing in, the error banner, the forced-logout banner.
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LoginEmptyPreview() = Login(LoginUiState())

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LoginErrorsPreview() =
    Login(
        LoginUiState(
            password = "123",
            identifierError = Res.string.enterEmailOrPhone,
            passwordError = Res.string.minimum6Characters,
        ),
    )

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LoginLoadingPreview() = Login(LoginUiState("seed.doctor1@shifa.dev", "Dev12345!", loading = true))

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LoginFailedPreview() =
    Login(
        LoginUiState(
            "seed.doctor1@shifa.dev",
            "wrong123",
            banner = LoginBanner(Res.string.invalidCredentials, error = true),
        ),
    )

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LoginSessionExpiredPreview() =
    Login(
        LoginUiState(banner = LoginBanner(Res.string.sessionExpiredBanner, error = false)),
    )

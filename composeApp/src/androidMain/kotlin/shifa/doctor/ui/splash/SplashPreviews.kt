package shifa.doctor.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.ui.theme.PreviewTheme

// DaApp splash and boot frames (flow 01/01 §3, §6).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun SplashLoadingPreview() = PreviewTheme { SplashScreenContent(SplashUiState.Loading, {}, {}) }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun SplashLoadingEndPreview() =
    PreviewTheme { SplashScreenContent(SplashUiState.Loading, {}, {}, progress = 1f) }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun SplashUpdatePreview() =
    PreviewTheme {
        SplashScreenContent(SplashUiState.UpdateRequired("market://x"), {}, {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun SplashMaintenancePreview() = PreviewTheme { SplashScreenContent(SplashUiState.Maintenance(null), {}, {}) }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun SplashMaintenanceRetryingPreview() =
    PreviewTheme {
        SplashScreenContent(
            SplashUiState.Maintenance("Back at 10:00 Tashkent time.", retrying = true),
            {},
            {},
        )
    }

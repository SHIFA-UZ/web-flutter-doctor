package shifa.doctor.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.pinsDontMatch
import shifa.doctor.ui.theme.PreviewTheme
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing

// DaApp lock screen (flow 01/12) and DaProfile Security → App lock with its PIN sheet (flow 01/11).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LockScreenPreview() =
    PreviewTheme {
        LockScreenContent(LockUiState(entry = "1234", biometric = true), {}, {}, {}, onBiometric = {}, onSignOut = {})
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun LockScreenWrongPreview() =
    PreviewTheme {
        LockScreenContent(LockUiState(wrong = true, attemptsLeft = 2), {}, {}, {}, onBiometric = null, onSignOut = {})
    }

@Preview(widthDp = 402, heightDp = 420)
@Composable
internal fun AppLockSectionPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).padding(ShifaSpacing.Inset.Md)) {
            AppLockSectionContent(AppLockSettingsUiState(enabled = true, biometric = true), biometricsAvailable = true)
        }
    }

@Preview(widthDp = 402, heightDp = 420)
@Composable
internal fun AppLockSectionNoBiometricsPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).padding(ShifaSpacing.Inset.Md)) {
            AppLockSectionContent(AppLockSettingsUiState(enabled = true), biometricsAvailable = false)
        }
    }

@Preview(widthDp = 402, heightDp = 480)
@Composable
internal fun PinSheetPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            PinSheetContent(PinSheet(PinSheetMode.Set, value = "1234", error = Res.string.pinsDontMatch), {}, {}, {})
        }
    }

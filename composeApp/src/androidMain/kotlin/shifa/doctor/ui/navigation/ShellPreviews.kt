package shifa.doctor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.ui.theme.PreviewTheme

// DaApp phone shell (flow 01/16) and the More sheet (flow 01/17).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellHomePreview() =
    PreviewTheme {
        MainScreenContent(ShellUiState(plan = Plan.Premium, hasClinic = true), ShellActions())
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellMoreTabPreview() =
    PreviewTheme { MainScreenContent(ShellUiState(tab = ShellTab.Reports, plan = Plan.Premium), ShellActions()) }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun MoreSheetPremiumPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MoreSheetContent(
                ShellUiState(tab = ShellTab.Notifications, plan = Plan.Premium, hasClinic = true),
                ShellActions(),
            )
        }
    }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun MoreSheetBasicPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MoreSheetContent(ShellUiState(plan = Plan.Basic, hasClinic = false), ShellActions())
        }
    }

// Flow 01/18: the clinic-staff shell (DaApp `phoneTabs` for staff).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellStaffPreview() = PreviewTheme { MainScreenContent(ShellUiState(staff = true), ShellActions()) }

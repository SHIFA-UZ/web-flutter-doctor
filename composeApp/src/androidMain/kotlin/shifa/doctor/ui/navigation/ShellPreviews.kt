package shifa.doctor.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.ui.theme.PreviewTheme

// DaApp phone shell (flow 01/16) and the More sheet (flow 01/17).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellHomePreview() =
    PreviewTheme {
        MainScreenContent(
            ShellUiState(gate = FeatureGate.forTier(DoctorTier.Premium), hasClinic = true, chatUnread = 3),
            ShellActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellMoreTabPreview() =
    PreviewTheme {
        MainScreenContent(
            ShellUiState(tab = ShellTab.Reports, gate = FeatureGate.forTier(DoctorTier.Premium)),
            ShellActions(),
        )
    }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun MoreSheetPremiumPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MoreSheetContent(
                ShellUiState(
                    tab = ShellTab.Notifications,
                    gate = FeatureGate.forTier(DoctorTier.Premium),
                    hasClinic = true,
                    notificationsUnread = 140,
                    activeTasks = 4,
                ),
                ShellActions(),
            )
        }
    }

@Preview(widthDp = 402, heightDp = 640)
@Composable
internal fun MoreSheetBasicPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MoreSheetContent(
                ShellUiState(gate = FeatureGate.forTier(DoctorTier.Basic), hasClinic = false),
                ShellActions(),
            )
        }
    }

// Flow 01/18: the clinic-staff shell (DaApp `phoneTabs` for staff).
@Preview(widthDp = 402, heightDp = 874)
@Composable
internal fun ShellStaffPreview() =
    PreviewTheme {
        MainScreenContent(ShellUiState(staff = true, chatUnread = 2, notificationsUnread = 7), ShellActions())
    }

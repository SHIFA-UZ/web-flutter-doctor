package shifa.doctor.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import shifa.doctor.ui.theme.PreviewTheme

// DaApp sign-out question (flow 08/15), with the app-lock line.
@Preview(widthDp = 402, heightDp = 480)
@Composable
internal fun SignOutSheetPreview() =
    PreviewTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SignOutSheetContent(SignOutUiState(asking = true, pinSet = true), {}, {})
        }
    }

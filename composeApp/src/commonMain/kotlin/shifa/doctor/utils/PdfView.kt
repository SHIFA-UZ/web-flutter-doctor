package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The pages of a PDF, scrollable; [onTap] on a single tap, [fallback] when it doesn't open. */
@Composable
expect fun PdfView(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    fallback: @Composable () -> Unit,
)

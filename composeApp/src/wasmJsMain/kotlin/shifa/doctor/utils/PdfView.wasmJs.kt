package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ponytail: the fallback (open or download) until WEB-05 shows PDFs in the browser's viewer.
@Composable
actual fun PdfView(
    bytes: ByteArray,
    modifier: Modifier,
    onTap: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    fallback()
}

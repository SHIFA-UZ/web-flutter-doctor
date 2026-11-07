package shifa.doctor.ui.widgets

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import shifa.doctor.ui.theme.ShifaRadius

/** Figma pop-up. Visual only: host it in a Dialog. */
@Composable
fun ShifaPopUp(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = TraySheet(ShifaRadius.ControlLg, modifier) {
    TrayHeader(title, onClose)
    content()
}

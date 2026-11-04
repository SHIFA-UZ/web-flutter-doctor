package shifa.doctor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_info_16
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.utils.tr

/**
 * A one-off message for the shell to show once it opens, from a screen that has already left (registration: "Some
 * profile details could not be saved…", flow 01/08 §8). [ShellNoticeBanner] shows it until it is closed.
 */
object ShellNotice {
    val pending = MutableStateFlow<Notice?>(null)
}

/** A note for the shell; [success] shows it as the calm teal banner ("Profile information saved") instead of a warning. */
data class Notice(
    val text: StringResource,
    val success: Boolean = false,
)

/** The pending [ShellNotice] as a closable warning banner at the top of the shell (SHELL-01 moves it under its bar). */
@Composable
fun ShellNoticeBanner(modifier: Modifier = Modifier) {
    val notice by ShellNotice.pending.collectAsState()
    notice?.let {
        ShifaBanner(
            tr(it.text),
            if (it.success) Res.drawable.ic_check_circle_16 else Res.drawable.ic_info_16,
            if (it.success) ShifaBannerType.Success else ShifaBannerType.Warning,
            modifier.statusBarsPadding().padding(ShifaSpacing.Inset.Sm),
            onClose = { ShellNotice.pending.value = null },
        )
    }
}

package shifa.doctor.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.markNoShow
import shifa.doctor.generated.resources.noShowConfirmBody
import shifa.doctor.generated.resources.notifyPatient
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.utils.tr

/**
 * Board DaVisit "noshow" (BE-10), from the appointment panel and from a video call the patient never joined: what it
 * means, "Notify patient" (on by default), Cancel and the destructive Mark as no-show. The board's Undo is left out: the
 * backend has no way back from NO_SHOW.
 */
@Composable
internal fun NoShowQuestion(
    notify: Boolean,
    busy: Boolean,
    onNotify: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) = ProfileSheet({ if (!busy) onClose() }) {
    ShifaDialogSheet(
        tr(Res.string.markNoShow),
        tr(Res.string.markNoShow),
        onConfirm,
        body = tr(Res.string.noShowConfirmBody),
        tone = DialogTone.Destructive,
        confirmLoading = busy,
        cancelText = tr(Res.string.cancel),
        onCancel = { if (!busy) onClose() },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaSwitch(notify, { if (!busy) onNotify(it) })
            Text(
                tr(Res.string.notifyPatient),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
        }
    }
}

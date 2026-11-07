package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cameraAccessNeeded
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardChangesBody
import shifa.doctor.generated.resources.discardChangesTitle
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.keepEditing
import shifa.doctor.generated.resources.openSettings
import shifa.doctor.generated.resources.save
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.utils.openAppSettings
import shifa.doctor.utils.tr

// What the profile edit forms (Personal information, Contact, Invoicing) share: the Back rules, the close after Save
// or Discard, the header with its Save check, and the "Discard changes?" question (flow 08/02 §7).

/** What Back does on an edit form. */
internal enum class BackStep { Close, Ask, CloseQuestion, Stay }

/** Busy (saving): nothing; the question open: close it; changes: ask; otherwise close. */
internal fun backStep(
    busy: Boolean,
    asking: Boolean,
    dirty: Boolean,
): BackStep =
    when {
        busy -> BackStep.Stay
        asking -> BackStep.CloseQuestion
        dirty -> BackStep.Ask
        else -> BackStep.Close
    }

/**
 * The system Back goes through [back]; once the ViewModel says [done], it is told ([onClosed], so a later open of the
 * same shell-scoped ViewModel doesn't close at once) and the screen closes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FormExitEffects(
    done: Boolean,
    onClosed: () -> Unit,
    onClose: () -> Unit,
    back: () -> Unit,
) {
    val close by rememberUpdatedState(onClose)
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(done) {
        if (done) {
            closed()
            close()
        }
    }
    BackHandler(onBack = back)
}

/** A pushed edit form: header with Back and (when [onSave] is set) the Save check, then a scrolling column. */
@Composable
internal fun ProfileForm(
    title: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)?,
    askDiscard: Boolean,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(
            title,
            Modifier.statusBarsPadding(),
            onBack = onBack,
            onCheck = onSave,
            checkDescription = Res.string.save,
        )
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
            content = content,
        )
    }
    if (askDiscard) DiscardChangesSheet(onDiscard, onKeep)
}

/** "Discard changes?" with Discard (destructive) and Keep editing. */
@Composable
internal fun DiscardChangesSheet(
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) = ProfileSheet(onKeep) {
    ShifaDialogSheet(
        tr(Res.string.discardChangesTitle),
        tr(Res.string.discard),
        onDiscard,
        body = tr(Res.string.discardChangesBody),
        tone = DialogTone.Destructive,
        icon = Res.drawable.ic_trash_22,
        cancelText = tr(Res.string.keepEditing),
        onCancel = onKeep,
    )
}

/**
 * A bottom sheet that only hosts a drawn tray (`ShifaDialogSheet`, `ShifaPickerSheet`). It opens at full height, so a
 * tall tray (the reschedule month) shows its button instead of stopping half way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSheet(
    onDismiss: () -> Unit,
    // Asked before a drag or a scrim tap hides the sheet: false keeps it (a form saving, or asking to discard first).
    confirmHide: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    // The sheet state keeps its first lambda: read the caller's latest check (busy / changed now, not at first show).
    val confirm by rememberUpdatedState(confirmHide)
    ProfileSheetHost(onDismiss, { confirm() }, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheetHost(
    onDismiss: () -> Unit,
    confirmHide: () -> Boolean,
    content: @Composable () -> Unit,
) = ModalBottomSheet(
    onDismiss,
    sheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {
            it != SheetValue.Hidden || confirmHide()
        }),
    shape = RectangleShape,
    containerColor = Color.Transparent,
    dragHandle = null,
) {
    content()
}

/** "Camera access is needed to take a photo" with Open settings, after the camera permission was refused. */
@Composable
internal fun CameraDeniedBanner(onClose: () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        ShifaBanner(
            tr(Res.string.cameraAccessNeeded),
            Res.drawable.ic_alert_circle_16,
            ShifaBannerType.Warning,
            onClose = onClose,
        )
        ShifaLink(tr(Res.string.openSettings), ::openAppSettings)
    }

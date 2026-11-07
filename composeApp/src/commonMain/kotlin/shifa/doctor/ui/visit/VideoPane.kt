package shifa.doctor.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cameraLabel
import shifa.doctor.generated.resources.cameraOff
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.flipCamera
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_mic_22
import shifa.doctor.generated.resources.ic_mic_off_22
import shifa.doctor.generated.resources.ic_phone_off_22
import shifa.doctor.generated.resources.ic_refresh_22
import shifa.doctor.generated.resources.ic_video_22
import shifa.doctor.generated.resources.ic_video_off_22
import shifa.doctor.generated.resources.joinCall
import shifa.doctor.generated.resources.leaveCall
import shifa.doctor.generated.resources.leaveCallBody
import shifa.doctor.generated.resources.micDeniedPatientCantHear
import shifa.doctor.generated.resources.microphoneLabel
import shifa.doctor.generated.resources.off
import shifa.doctor.generated.resources.on
import shifa.doctor.generated.resources.openSettings
import shifa.doctor.generated.resources.patientInRoom
import shifa.doctor.generated.resources.patientNotInRoomYet
import shifa.doctor.generated.resources.rejoinCall
import shifa.doctor.generated.resources.videoCall
import shifa.doctor.generated.resources.videoCallConnecting
import shifa.doctor.generated.resources.videoCallReady
import shifa.doctor.generated.resources.waitingForPatient
import shifa.doctor.generated.resources.youLeftTheCall
import shifa.doctor.generated.resources.yourCameraPreview
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.ui.widgets.ShifaTag
import shifa.doctor.ui.widgets.ShifaTagType
import shifa.doctor.utils.VideoCall
import shifa.doctor.utils.VideoTile
import shifa.doctor.utils.openAppSettings
import shifa.doctor.utils.tr

class VideoActions(
    val join: () -> Unit = {},
    val camera: () -> Unit = {},
    val mic: () -> Unit = {},
    val flip: () -> Unit = {},
    // Leave call?: asked, answered.
    val askLeave: (Boolean) -> Unit = {},
    val leave: () -> Unit = {},
    val dismissError: () -> Unit = {},
    // The pre-join page shown (true) or gone: whether the patient is in the room is asked meanwhile.
    val presence: (Boolean) -> Unit = {},
)

/**
 * Before joining (board `DaVisit` "VIDEO PRE-JOIN", flow 05/10; new UI): the doctor's camera preview with Flip, the
 * camera and microphone switches, the call's status (an explained refusal, VIDEO-01), a refused microphone with Open
 * settings, the patient and the time, Join call and Cancel.
 */
@Composable
internal fun PreJoin(
    state: VisitUiState,
    video: VideoVisitState,
    call: VideoCall,
    schedule: String,
    actions: VisitActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    // Asked only while the page is in front: not in the background or under the lock screen.
    val presence by rememberUpdatedState(actions.video.presence)
    LifecycleResumeEffect(Unit) {
        presence(true)
        onPauseOrDispose { presence(false) }
    }
    ShifaSubHeader(tr(Res.string.videoCall), Modifier.statusBarsPadding(), onBack = actions.back)
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S14),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(ShifaRadius.ControlLg)
                .background(ShifaColors.Bg.Media),
            contentAlignment = Alignment.Center,
        ) {
            if (video.call.cameraOn && video.camera == true) {
                VideoTile(call, remote = false, Modifier.fillMaxSize())
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Icon(painterResource(Res.drawable.ic_video_off_22), null, tint = ShifaColors.Icon.Inverse)
                    Text(
                        tr(Res.string.cameraOff),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Inverse,
                    )
                }
            }
            ShifaIconButton(
                Res.drawable.ic_refresh_22,
                tr(Res.string.flipCamera),
                actions.video.flip,
                Modifier.align(Alignment.TopEnd).padding(ShifaSize.S12),
                variant = ShifaIconButtonVariant.OnDark,
            )
        }
        Text(tr(Res.string.yourCameraPreview), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
        Toggle(Res.drawable.ic_video_22, tr(Res.string.cameraLabel), video.call.cameraOn, actions.video.camera)
        Toggle(Res.drawable.ic_mic_22, tr(Res.string.microphoneLabel), video.call.micOn, actions.video.mic)
        val joining = video.stage == VideoStage.Joining
        when {
            video.error != null ->
                ShifaBanner(
                    tr(video.error),
                    Res.drawable.ic_alert_circle_16,
                    ShifaBannerType.Error,
                    onClose = actions.video.dismissError,
                )
            video.mic == false -> {
                ShifaBanner(
                    tr(Res.string.micDeniedPatientCantHear),
                    Res.drawable.ic_alert_circle_16,
                    ShifaBannerType.Error,
                )
                ShifaLink(tr(Res.string.openSettings), ::openAppSettings)
            }
            else ->
                Text(
                    tr(if (joining) Res.string.videoCallConnecting else Res.string.videoCallReady),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Primary,
                )
        }
        Row(
            Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                Text(
                    state.visit?.patientName.orEmpty(),
                    style = ShifaTextStyles.panelTitle(),
                    color = ShifaColors.Text.Primary,
                )
                Text(schedule, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
            }
            // Not in the design: where the board's patient row has its payment pill, whether the patient is here.
            when (video.patientInRoom) {
                true -> ShifaTag(tr(Res.string.patientInRoom), type = ShifaTagType.Primary)
                false -> ShifaTag(tr(Res.string.patientNotInRoomYet), type = ShifaTagType.Secondary)
                null -> Unit
            }
        }
        ShifaPrimaryButton(
            tr(Res.string.joinCall),
            actions.video.join,
            Modifier.fillMaxWidth(),
            icon = Res.drawable.ic_video_22,
            loading = joining,
            enabled = video.canJoin,
        )
        ShifaLink(tr(Res.string.cancel), actions.back, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Toggle(
    icon: DrawableResource,
    label: String,
    on: Boolean,
    toggle: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .height(ShifaSize.S52)
        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd)
        .clickable(role = Role.Switch, onClick = toggle)
        .padding(horizontal = ShifaSize.S14),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    Icon(painterResource(icon), null, tint = if (on) ShifaColors.Icon.Brand else ShifaColors.Icon.Secondary)
    Text(label, Modifier.weight(1f), style = ShifaTextStyles.buttonCompact(), color = ShifaColors.Text.Primary)
    Text(
        tr(if (on) Res.string.on else Res.string.off),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    ShifaSwitch(on, { toggle() })
}

/**
 * The call in the workspace (board `DaVisit` video pane, flow 05/11): connecting; the doctor alone ("Waiting for … to
 * join"); the patient with the doctor's camera in a corner and the patient's name; left ("You left the call", the visit
 * stays open, Rejoin). Controls: microphone, camera, flip, leave (asked first).
 */
@Composable
internal fun VideoPane(
    patientName: String,
    video: VideoVisitState,
    call: VideoCall,
    actions: VideoActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S8),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S8),
) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(PANE_RATIO).clip(ShifaRadius.ControlMd).background(ShifaColors.Bg.Media),
        contentAlignment = Alignment.Center,
    ) {
        when {
            video.stage == VideoStage.Left ->
                Column(
                    Modifier.padding(ShifaSize.S16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                ) {
                    Text(
                        tr(Res.string.youLeftTheCall),
                        style = ShifaTextStyles.panelTitle(),
                        color = ShifaColors.Text.Inverse,
                    )
                    Text(
                        tr(Res.string.leaveCallBody),
                        style = ShifaTextStyles.caption(),
                        color = ShifaColors.Text.Inverse,
                    )
                    video.error?.let {
                        Text(
                            tr(it),
                            style = ShifaTextStyles.caption(),
                            color = ShifaColors.Text.Inverse,
                        )
                    }
                    ShifaPrimaryButton(tr(Res.string.rejoinCall), actions.join, enabled = video.canJoin)
                }
            video.stage == VideoStage.Joining ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                ) {
                    CircularProgressIndicator(color = ShifaColors.Icon.Inverse)
                    Text(
                        tr(Res.string.videoCallConnecting),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.Inverse,
                    )
                }
            !video.call.patientJoined -> {
                VideoTile(call, remote = false, Modifier.fillMaxSize())
                Text(
                    tr(Res.string.waitingForPatient, patientName),
                    Modifier.background(
                        ShifaColors.Alpha.Black50,
                        ShifaRadius.Full,
                    ).padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S6),
                    style = ShifaTextStyles.buttonCompact(),
                    color = ShifaColors.Text.Inverse,
                )
            }
            else -> {
                VideoTile(call, remote = true, Modifier.fillMaxSize())
                Text(
                    patientName,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(ShifaSize.S12)
                        .background(ShifaColors.Alpha.Black50, ShifaRadius.Full)
                        .padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S4),
                    style = ShifaTextStyles.caption(),
                    color = ShifaColors.Text.Inverse,
                )
                VideoTile(
                    call,
                    remote = false,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ShifaSize.S12)
                        .size(ShifaSize.S88, ShifaSize.S120)
                        .clip(ShifaRadius.ControlMd)
                        .border(ShifaSize.S2, ShifaColors.Bg.Surface, ShifaRadius.ControlMd),
                )
            }
        }
    }
    if (video.stage == VideoStage.InCall) {
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            ShifaIconButton(
                if (video.call.micOn) Res.drawable.ic_mic_22 else Res.drawable.ic_mic_off_22,
                tr(Res.string.microphoneLabel),
                actions.mic,
                variant = ShifaIconButtonVariant.Secondary,
                selected = !video.call.micOn,
            )
            ShifaIconButton(
                if (video.call.cameraOn) Res.drawable.ic_video_22 else Res.drawable.ic_video_off_22,
                tr(Res.string.cameraLabel),
                actions.camera,
                variant = ShifaIconButtonVariant.Secondary,
                selected = !video.call.cameraOn,
            )
            ShifaIconButton(
                Res.drawable.ic_refresh_22,
                tr(Res.string.flipCamera),
                actions.flip,
                variant = ShifaIconButtonVariant.Secondary,
            )
            ShifaIconButton(
                Res.drawable.ic_phone_off_22,
                tr(Res.string.leaveCall),
                { actions.askLeave(true) },
                variant = ShifaIconButtonVariant.Danger,
            )
        }
    }
}

/** "Leave call?": the visit stays open until it is ended (flow 05/11: leaving the call isn't ending the visit). */
@Composable
internal fun LeaveCall(actions: VideoActions) =
    ShifaDialogSheet(
        tr(Res.string.leaveCall),
        tr(Res.string.leaveCall),
        actions.leave,
        body = tr(Res.string.leaveCallBody),
        cancelText = tr(Res.string.cancel),
        onCancel = { actions.askLeave(false) },
    )

private const val PREVIEW_RATIO = 4f / 3f
private const val PANE_RATIO = 4f / 3f

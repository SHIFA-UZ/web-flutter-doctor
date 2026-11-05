package shifa.doctor.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attachFile
import shifa.doctor.generated.resources.chatPhoto
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.failedToLoadImage
import shifa.doctor.generated.resources.ic_camera_20
import shifa.doctor.generated.resources.ic_image_20
import shifa.doctor.generated.resources.ic_image_24
import shifa.doctor.generated.resources.ic_x_22
import shifa.doctor.generated.resources.imageNotAvailable
import shifa.doctor.generated.resources.selectImage
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ReceivedShape
import shifa.doctor.ui.widgets.SentShape
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.ZoomState
import shifa.doctor.ui.widgets.zoomable
import shifa.doctor.utils.tr

/**
 * A photo message (CHAT-04, flow 07/05 §3, board `DaChat`): a square tile cropped to fill, in the bubble's shape, the
 * time and tick on a dark pill in its corner; a skeleton while it loads, "Failed to load image" when it can't, "Image
 * not available" without a URL. [bytes] is a photo still on its way, shown from the phone. A tap opens the viewer.
 */
@Composable
internal fun ImageTile(
    url: String?,
    bytes: ByteArray?,
    mine: Boolean,
    time: String,
    tick: String?,
    onOpen: () -> Unit,
) = Box(
    Modifier
        .size(ShifaSize.S240)
        .clip(if (mine) SentShape else ReceivedShape)
        .background(ShifaColors.Bg.Canvas)
        .then(if (url != null) Modifier.clickable(role = Role.Image, onClick = onOpen) else Modifier),
) {
    val local: ImageBitmap? = remember(bytes) { bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    when {
        local != null -> Image(local, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        url != null ->
            KamelImage(
                resource = asyncPainterResource(url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = { ShifaSkeleton(Modifier.fillMaxSize(), ShifaRadius.Xs) },
                onFailure = { Missing(tr(Res.string.failedToLoadImage)) },
            )
        else -> Missing(tr(Res.string.imageNotAvailable))
    }
    Text(
        listOfNotNull(time, tick).joinToString(" "),
        Modifier
            .align(Alignment.BottomEnd)
            // Said once, in the message's own description.
            .clearAndSetSemantics {}
            .padding(ShifaSize.S8)
            .clip(ShifaRadius.Full)
            .background(ShifaColors.Alpha.Black50)
            .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Inverse,
    )
}

@Composable
private fun Missing(text: String) =
    Column(
        Modifier.fillMaxSize().padding(ShifaSize.S16),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S8, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(Res.drawable.ic_image_24), null, tint = ShifaColors.Text.Secondary)
        Text(
            text,
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }

/** The attach sheet (flow 07/03 §3): Select Image, Take Photo; documents and voice notes join with CHAT-05 and -06. */
@Composable
internal fun AttachSheet(
    actions: ThreadActions,
    onClose: () -> Unit,
) = ProfileSheet(onClose) {
    ShifaTray(tr(Res.string.attachFile), onClose) {
        val rows =
            listOf(
                OptionItem(tr(Res.string.selectImage), Res.drawable.ic_image_20) to actions.pickImage,
                OptionItem(tr(Res.string.takePhoto), Res.drawable.ic_camera_20) to actions.takePhoto,
            )
        ShifaOptionList(rows.map { it.first }, { i ->
            onClose()
            rows[i].second()
        })
    }
}

/**
 * The photo full screen (flow 07/05 §3): black, fit to the screen, pinch zoom 0.5×–4× and pan, double tap for 2× (the
 * documents' `zoomable`), a close button; "Failed to load image" when it can't. Back closes it.
 */
@Composable
internal fun ImageViewer(
    url: String,
    onClose: () -> Unit,
) = Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
    Box(Modifier.fillMaxSize().background(ShifaColors.Bg.Media)) {
        val zoom = remember(url) { ZoomState(min = 0.5f) }
        KamelImage(
            resource = asyncPainterResource(url),
            contentDescription = tr(Res.string.chatPhoto),
            modifier = Modifier.fillMaxSize().zoomable(zoom),
            contentScale = ContentScale.Fit,
            onLoading = { Box(Modifier.fillMaxSize()) },
            onFailure = {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        tr(Res.string.failedToLoadImage),
                        style = ShifaTextStyles.bodyText(),
                        color = ShifaColors.Text.Inverse,
                    )
                }
            },
        )
        ShifaIconButton(
            Res.drawable.ic_x_22,
            tr(Res.string.close),
            onClose,
            Modifier.statusBarsPadding().padding(ShifaSize.S12),
            variant = ShifaIconButtonVariant.OnDark,
        )
    }
}

package shifa.doctor.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.api.fileCacheKey
import shifa.doctor.data.chat.fileSizeLabel
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.attachFile
import shifa.doctor.generated.resources.chatFileUnavailable
import shifa.doctor.generated.resources.chatPhoto
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.couldNotLoad
import shifa.doctor.generated.resources.document
import shifa.doctor.generated.resources.failedToLoadImage
import shifa.doctor.generated.resources.ic_camera_20
import shifa.doctor.generated.resources.ic_file_20
import shifa.doctor.generated.resources.ic_file_text_20
import shifa.doctor.generated.resources.ic_image_20
import shifa.doctor.generated.resources.ic_image_24
import shifa.doctor.generated.resources.ic_mic_20
import shifa.doctor.generated.resources.ic_x_22
import shifa.doctor.generated.resources.imageNotAvailable
import shifa.doctor.generated.resources.openDocument
import shifa.doctor.generated.resources.recordVoice
import shifa.doctor.generated.resources.selectDocument
import shifa.doctor.generated.resources.selectImage
import shifa.doctor.generated.resources.takePhoto
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ReceivedShape
import shifa.doctor.ui.widgets.SentShape
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaLoadError
import shifa.doctor.ui.widgets.ShifaOptionList
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSpinner
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.ZoomState
import shifa.doctor.ui.widgets.decodedImage
import shifa.doctor.ui.widgets.zoomable
import shifa.doctor.utils.PdfView
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
    val decoded = bytes?.let { decodedImage(it) }
    val local = decoded?.getOrNull()
    when {
        bytes != null && decoded == null -> ShifaSkeleton(Modifier.fillMaxSize(), ShifaRadius.Xs)
        local != null -> Image(local, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        url != null ->
            KamelImage(
                resource = asyncPainterResource(url, key = fileCacheKey(url)),
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

/** The attach sheet (flow 07/03 §3): Select Image, Take Photo, Select Document, Record Voice Message. */
@Composable
internal fun AttachSheet(
    actions: ThreadActions,
    // False while an interrupted recording waits for Send or Discard: a new one would drop it.
    canRecord: Boolean,
    onClose: () -> Unit,
) = ProfileSheet(onClose) {
    ShifaTray(tr(Res.string.attachFile), onClose) {
        val rows =
            listOf(
                OptionItem(tr(Res.string.selectImage), Res.drawable.ic_image_20) to actions.pickImage,
                OptionItem(tr(Res.string.takePhoto), Res.drawable.ic_camera_20) to actions.takePhoto,
                OptionItem(tr(Res.string.selectDocument), Res.drawable.ic_file_20) to actions.pickDocument,
                OptionItem(tr(Res.string.recordVoice), Res.drawable.ic_mic_20) to actions.recordVoice,
            ).let { if (canRecord) it else it.dropLast(1) }
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
            resource = asyncPainterResource(url, key = fileCacheKey(url)),
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

/**
 * CHAT-05: a PDF from the thread in the app, as a photo opens: the dark backdrop, a bar with its name and Close, the pages
 * below (pinch zoom on the phones; the browser's own viewer on the web, which is why the bar sits above the document and
 * not over it). While it's read, a spinner; when it can't be, Retry and the old way out (the browser).
 */
@Composable
internal fun PdfViewer(
    pdf: PdfPreview,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenOutside: () -> Unit,
) = Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
    PdfViewerContent(pdf, onClose, onRetry, onOpenOutside)
}

/** [PdfViewer] without its window, for previews. */
@Composable
internal fun PdfViewerContent(
    pdf: PdfPreview,
    onClose: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenOutside: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Media)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(ShifaSize.S12),
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaIconButton(
                Res.drawable.ic_x_22,
                tr(Res.string.close),
                onClose,
                variant = ShifaIconButtonVariant.OnDark,
            )
            Text(
                pdf.name?.takeIf { it.isNotBlank() } ?: tr(Res.string.document),
                Modifier.weight(1f),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Inverse,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            val failed = @Composable {
                // One white card: the browser link under Retry, readable on the dark backdrop.
                Column(
                    Modifier
                        .padding(ShifaSize.S16)
                        .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
                        .padding(bottom = ShifaSpacing.Inset.Lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShifaLoadError(tr(Res.string.couldNotLoad), onRetry)
                    ShifaLink(tr(Res.string.openDocument), onOpenOutside)
                }
            }
            val bytes = pdf.bytes
            when {
                pdf.failed -> failed()
                bytes == null -> ShifaSpinner(ShifaColors.Text.Inverse, large = true)
                else -> {
                    val zoom = remember(pdf.url) { ZoomState(min = 1f) }
                    PdfView(bytes, Modifier.fillMaxSize().zoomable(zoom), fallback = failed)
                }
            }
        }
    }
}

/** A document a message carries is a PDF: by its name, else by its URL's path. */
internal fun isPdf(
    name: String?,
    url: String,
) = listOfNotNull(name, url.substringBefore('?')).any { it.trim().lowercase().endsWith(".pdf") }

/**
 * A document message (CHAT-05, flow 07/06 §3, board `DaChat`): an icon tile by its type (PDF, Word, photo, other), the
 * name (2 lines; "Document" without one), its size, and the time and tick. A tap opens a PDF in the app ([PdfViewer]),
 * any other type outside it.
 */
@Composable
internal fun DocumentTile(
    name: String?,
    size: Long?,
    mine: Boolean,
    time: String,
    tick: String?,
    max: androidx.compose.ui.unit.Dp,
    // Null: no file to open (the card is off, "File unavailable").
    onOpen: (() -> Unit)?,
) = androidx.compose.foundation.layout.Row(
    Modifier
        .widthIn(max = minOf(max, ShifaSize.S280))
        .clip(if (mine) SentShape else ReceivedShape)
        .background(if (mine) ShifaColors.Alpha.Black5 else ShifaColors.Alpha.Teal25)
        .then(
            if (onOpen != null) {
                Modifier.clickable(
                    role = Role.Button,
                    onClick = onOpen,
                )
            } else {
                Modifier.alpha(UNAVAILABLE_ALPHA)
            },
        )
        .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    verticalAlignment = Alignment.CenterVertically,
) {
    val ext = name?.substringAfterLast('.', "")?.lowercase()
    Box(Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd), Alignment.Center) {
        Icon(
            painterResource(
                when (ext) {
                    "pdf", "doc", "docx" -> Res.drawable.ic_file_text_20
                    "jpg", "jpeg", "png", "gif" -> Res.drawable.ic_image_20
                    else -> Res.drawable.ic_file_20
                },
            ),
            null,
            tint = ShifaColors.Text.BrandStrong,
        )
    }
    Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            name?.takeIf { it.isNotBlank() } ?: tr(Res.string.document),
            // Said in the message's own description ("Document, Referral.pdf").
            Modifier.clearAndSetSemantics {},
            style = ShifaTextStyles.chatInput(),
            color = ShifaColors.Text.Primary,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                if (onOpen == null) tr(Res.string.chatFileUnavailable) else size?.let(::fileSizeLabel),
                listOfNotNull(time, tick).joinToString(" "),
            ).joinToString(" · "),
            Modifier.clearAndSetSemantics {},
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
    }
}

// A document card without a file reads as off (the pressables' disabled look).
private const val UNAVAILABLE_ALPHA = 0.55f

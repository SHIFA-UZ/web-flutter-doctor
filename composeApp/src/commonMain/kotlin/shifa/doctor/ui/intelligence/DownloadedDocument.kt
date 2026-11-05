package shifa.doctor.ui.intelligence

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.decodeToImageBitmap
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cantPreview
import shifa.doctor.generated.resources.couldNotLoadDocument
import shifa.doctor.generated.resources.documentGone
import shifa.doctor.generated.resources.intelNoDocumentAccess
import shifa.doctor.generated.resources.retry
import shifa.doctor.ui.documents.DocumentViewerViewModel
import shifa.doctor.ui.documents.Viewed
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.PdfView
import shifa.doctor.utils.tr

/**
 * A patient's document inside an Intelligence card (INTEL-07, flow 03/18 §3): the authenticated download of the
 * documents viewer (DOCS-05, its ViewModel reused), drawn in a grey 360 box: a page-shaped skeleton while it loads
 * (target; Flutter spun), the PDF's pages or the image fitted, else why not (failed + Retry, no access, gone, a type it
 * can't draw). Zoom and saving are in the full-screen viewer. The file goes when the card leaves the screen.
 */
@Composable
internal fun DownloadedDocument(
    patientId: Long,
    documentId: Long,
    title: String,
    // Which card shows it: each card owns its download.
    owner: String,
) {
    val viewModel: DocumentViewerViewModel =
        viewModel(key = "intel-document-$owner-$patientId-$documentId") {
            DocumentViewerViewModel(patientId, documentId, null)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    DisposableEffect(viewModel) { onDispose { viewModel.release() } }
    val box =
        Modifier
            .fillMaxWidth()
            .height(ShifaDimens.IntelDocumentHeight)
            .clip(ShifaRadius.Sm)
            .background(ShifaColors.Bg.Canvas)
            .semantics { contentDescription = title }
    when (val v = state.viewed) {
        Viewed.Loading -> ShifaSkeleton(box)
        Viewed.NoAccess -> Message(box, tr(Res.string.intelNoDocumentAccess))
        Viewed.Gone -> Message(box, tr(Res.string.documentGone))
        is Viewed.Failed -> Message(box, tr(Res.string.couldNotLoadDocument), viewModel::load)
        is Viewed.Ready -> {
            val file = v.file
            when {
                file.isPdf ->
                    PdfView(
                        file.bytes,
                        box,
                    ) { Message(box, tr(Res.string.couldNotLoadDocument), viewModel::load) }
                file.isImage -> {
                    val image = remember(file) { runCatching { file.bytes.decodeToImageBitmap() }.getOrNull() }
                    if (image == null) {
                        Message(box, tr(Res.string.cantPreview))
                    } else {
                        Box(box.padding(ShifaSpacing.Inset.Sm), contentAlignment = Alignment.Center) {
                            Image(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }
                else -> Message(box, tr(Res.string.cantPreview))
            }
        }
    }
}

@Composable
private fun Message(
    modifier: Modifier,
    text: String,
    retry: (() -> Unit)? = null,
) = Column(
    modifier.padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(text, style = ShifaTextStyles.aiText(), color = ShifaColors.Text.Secondary, textAlign = TextAlign.Center)
    retry?.let { ShifaLink(tr(Res.string.retry), it) }
}

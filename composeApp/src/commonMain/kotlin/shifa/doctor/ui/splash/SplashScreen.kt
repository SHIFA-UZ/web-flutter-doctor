package shifa.doctor.ui.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.data.session.Session
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appName
import shifa.doctor.generated.resources.loading
import shifa.doctor.generated.resources.maintenanceBody
import shifa.doctor.generated.resources.maintenanceTitle
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.updateNow
import shifa.doctor.generated.resources.updateRequiredBody
import shifa.doctor.generated.resources.updateRequiredTitle
import shifa.doctor.ui.theme.ShifaBlur
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.tr

/** AUTH-01: plays the splash, shows a force-update or maintenance frame when the backend asks, then [onFinished]. */
@Composable
fun SplashScreen(
    onFinished: (Session?) -> Unit,
    viewModel: SplashViewModel = viewModel { SplashViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by rememberUpdatedState(onFinished)
    val uri = LocalUriHandler.current
    LaunchedEffect(state) { (state as? SplashUiState.Finished)?.let { finished(it.session) } }
    // Back from the store (or from anywhere) while the update frame shows: check the version again (flow §7).
    if (state is SplashUiState.UpdateRequired) LifecycleResumeEffect(Unit) { onPauseOrDispose { viewModel.retry() } }
    SplashScreenContent(
        state,
        onUpdate = { url -> url?.let { runCatching { uri.openUri(it) } } },
        onRetry = viewModel::retry,
        onSkip = viewModel::skip,
    )
}

@Composable
fun SplashScreenContent(
    state: SplashUiState,
    onUpdate: (storeUrl: String?) -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit = {},
) = when (state) {
    SplashUiState.Loading, is SplashUiState.Finished -> Splash(onSkip)
    is SplashUiState.UpdateRequired ->
        Boot(tr(Res.string.updateRequiredTitle), tr(Res.string.updateRequiredBody), tr(Res.string.updateNow), false) {
            onUpdate(state.storeUrl)
        }
    is SplashUiState.Maintenance ->
        Boot(
            tr(Res.string.maintenanceTitle),
            state.message ?: tr(Res.string.maintenanceBody),
            tr(Res.string.retry),
            state.retrying,
            onRetry,
        )
}

// DaApp "splash": teal, two drifting blurred blobs, the glass "S" tile and the name. Nothing is focusable; a screen
// reader hears "Shifa Doctor, Loading…" once.
@Composable
private fun Splash(onSkip: () -> Unit) {
    val label = "${tr(Res.string.appName)}, ${tr(Res.string.loading)}"
    // Previews and screenshots hold the blobs still (an endless animation never lets a capture settle).
    val drift =
        if (LocalInspectionMode.current) {
            0f
        } else {
            rememberInfiniteTransition().animateFloat(
                0f,
                1f,
                infiniteRepeatable(tween(BLOB_MS), RepeatMode.Reverse),
            ).value
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(ShifaColors.Interactive.Primary)
            .pointerInput(Unit) { detectTapGestures { onSkip() } }
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Blob(ShifaSize.S320, Alignment.BottomStart, -ShifaSize.S120, ShifaSize.S80, drift)
        Blob(ShifaSize.S280, Alignment.TopEnd, ShifaSize.S100, -ShifaSize.S60, 1f - drift)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
        ) {
            Mark(ShifaColors.Raw.GlassTile, ShifaColors.Raw.GlassTileBorder)
            Text(
                tr(Res.string.appName),
                style = MaterialTheme.typography.headlineSmall,
                color = ShifaColors.Text.Inverse,
            )
        }
    }
}

@Composable
private fun Blob(
    size: androidx.compose.ui.unit.Dp,
    alignment: Alignment,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    drift: Float,
) = Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
    Box(
        Modifier
            .offset(x, y)
            .size(size)
            .graphicsLayer {
                val scale = 1f + BLOB_GROWTH * drift
                scaleX = scale
                scaleY = scale
                rotationZ = BLOB_TURN * drift
            }.blur(ShifaBlur.Glow)
            .background(ShifaColors.Raw.SplashBlob, ShifaRadius.Full),
    )
}

@Composable
private fun Mark(
    fill: Color,
    border: Color?,
) = Box(
    Modifier
        .size(ShifaSize.S96)
        .clip(ShifaRadius.Xl)
        .background(fill)
        .then(if (border != null) Modifier.border(ShifaSize.S1, border, ShifaRadius.Xl) else Modifier),
    contentAlignment = Alignment.Center,
) {
    Text("S", style = ShifaTextStyles.splashMark(), color = ShifaColors.Text.Inverse)
}

// DaApp "boot": the force-update and maintenance frames. No way past them but the one button. It scrolls, so a long
// backend message or 200 % text never pushes the button off screen (flow §8); centred while it fits.
@Composable
private fun Boot(
    title: String,
    body: String,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize().background(ShifaColors.Bg.Surface)) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight).fillMaxWidth().padding(ShifaSize.S32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S16, Alignment.CenterVertically),
    ) { BootContent(title, body, action, busy, onAction) }
}

@Composable
private fun BootContent(
    title: String,
    body: String,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
) {
    Box(Modifier.clearAndSetSemantics { }) { Mark(ShifaColors.Interactive.Primary, null) } // decorative
    Text(
        title,
        Modifier.padding(top = ShifaSpacing.Gap.Sm),
        style = MaterialTheme.typography.headlineSmall,
        color = ShifaColors.Text.Primary,
        textAlign = TextAlign.Center,
    )
    Text(
        body,
        Modifier.widthIn(max = ShifaDimens.Layout.DialogSm),
        style = ShifaTextStyles.dialogBody(),
        color = ShifaColors.Text.Secondary,
        textAlign = TextAlign.Center,
    )
    ShifaPrimaryButton(
        action,
        onAction,
        Modifier.fillMaxWidth().widthIn(max = ShifaDimens.Layout.DialogSm),
        loading = busy,
    )
}

private const val BLOB_MS = 2_000
private const val BLOB_GROWTH = 0.12f
private const val BLOB_TURN = 18f

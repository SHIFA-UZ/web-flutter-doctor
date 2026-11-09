package shifa.doctor.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.session.Session
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appName
import shifa.doctor.generated.resources.loading
import shifa.doctor.generated.resources.maintenanceBody
import shifa.doctor.generated.resources.maintenanceTitle
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.shifa_mark
import shifa.doctor.generated.resources.updateNow
import shifa.doctor.generated.resources.updateRequiredBody
import shifa.doctor.generated.resources.updateRequiredTitle
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAppIcon
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
    // Plays once the splash can draw its name (on the web, as index.html's frame lifts), so a slow first load doesn't
    // hide it; its end lets the app go on.
    val progress = remember { Animatable(0f) }
    val ready = tr(Res.string.appName).isNotEmpty()
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        progress.animateTo(1f, tween(SPLASH_MS, easing = EaseInOutCubic))
        viewModel.skip()
    }
    SplashScreenContent(
        state,
        onUpdate = { url -> url?.let { runCatching { uri.openUri(it) } } },
        onRetry = viewModel::retry,
        onSkip = viewModel::skip,
        progress = progress.value,
    )
}

@Composable
fun SplashScreenContent(
    state: SplashUiState,
    onUpdate: (storeUrl: String?) -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit = {},
    progress: Float = 0f,
) = when (state) {
    SplashUiState.Loading, is SplashUiState.Finished -> Splash(progress, onSkip)
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

// The patient app's splash (Figma 1614:12212 → 1614:12237): two teal blobs part to show white, the glass tile's border
// fades in and the mark turns from white to teal. No name under it: it would cross the blobs. [progress] 0 is the start, 1 the end. Nothing is focusable;
// a screen reader hears "Shifa Doctor, Loading…" once.
@Composable
private fun Splash(
    progress: Float,
    onSkip: () -> Unit,
) {
    val label = "${tr(Res.string.appName)}, ${tr(Res.string.loading)}"
    val tile = RoundedCornerShape(ShifaDimens.SplashTileRadius)
    Box(
        Modifier
            .fillMaxSize()
            .background(ShifaColors.Bg.Surface)
            .drawBehind {
                blob(lerp(ShifaDimens.SplashBlobAStart, ShifaDimens.SplashBlobAEnd, progress))
                blob(lerp(ShifaDimens.SplashBlobBStart, ShifaDimens.SplashBlobBEnd, progress))
            }.pointerInput(Unit) { detectTapGestures { onSkip() } }
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Figma also blurs the backdrop; skipped, as in the patient app (blur froze low-end Android GPUs).
        Box(
            Modifier
                .size(ShifaDimens.SplashTile)
                .clip(tile)
                .background(ShifaColors.Raw.GlassTile)
                .border(ShifaSize.S2, ShifaColors.Raw.GlassTileBorder.copy(alpha = progress), tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.shifa_mark),
                contentDescription = null,
                tint = lerp(ShifaColors.Icon.Inverse, ShifaColors.Raw.SplashLogo, progress),
            )
        }
    }
}

// Figma's blurred ellipse, rotated 45°. A radial gradient fades the edge over ±2σ instead of a real blur (Figma's blur
// value is 2σ). index.html draws the same blobs at the start while the app downloads.
private fun DrawScope.blob(offset: DpOffset) {
    val color = ShifaColors.Raw.SplashBlob
    val rx = ShifaDimens.SplashBlobWidth.toPx() / 2
    val fade = ShifaDimens.SplashBlobBlur.toPx()
    val radius = rx + fade
    val c = center + Offset(offset.x.toPx(), offset.y.toPx())
    rotate(45f, c) {
        scale(1f, ShifaDimens.SplashBlobHeight / ShifaDimens.SplashBlobWidth, c) {
            drawCircle(
                Brush.radialGradient(
                    (rx - fade) / radius to color,
                    rx / radius to color.copy(alpha = color.alpha / 2),
                    1f to color.copy(alpha = 0f),
                    center = c,
                    radius = radius,
                ),
                radius,
                c,
            )
        }
    }
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
    Box(Modifier.clearAndSetSemantics { }) { ShifaAppIcon(ShifaSize.S96) } // decorative
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

private const val SPLASH_MS = 1_600 // as in the patient app

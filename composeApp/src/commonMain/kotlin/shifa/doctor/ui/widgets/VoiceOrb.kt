package shifa.doctor.ui.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_mic_16
import shifa.doctor.generated.resources.ic_sparkles_40
import shifa.doctor.generated.resources.ic_stop_filled_12
import shifa.doctor.generated.resources.ic_voice_wave_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.rememberReduceMotion

/** The voice session's phases (02 §3.5): idle, listening, thinking, speaking; there is no connecting phase. */
enum class VoicePhase { Idle, Listening, Thinking, Speaking }

/**
 * How much the orb moves (AI-07, Flutter's `shifaOrbTargetIntensity`, ported with its tests): fixed per phase, except
 * listening, which follows the microphone [level] above the recorder's noise floor (0.16; a quiet room stays calm).
 * Speaking and thinking don't follow a level (playback has none). Reduced motion: low fixed values.
 */
fun orbIntensity(
    phase: VoicePhase,
    level: Float,
    reduceMotion: Boolean,
): Float {
    if (reduceMotion) {
        return when (phase) {
            VoicePhase.Listening -> 0.2f
            VoicePhase.Speaking -> 0.28f
            VoicePhase.Thinking -> 0.12f
            VoicePhase.Idle -> 0.05f
        }
    }
    val safe = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
    return when (phase) {
        VoicePhase.Idle -> 0.06f
        VoicePhase.Thinking -> 0.16f
        VoicePhase.Speaking -> 0.46f
        VoicePhase.Listening ->
            if (safe <= NOISE_FLOOR) 0.08f else ((safe - NOISE_FLOOR) / (1 - NOISE_FLOOR)).coerceIn(0f, 1f)
    }
}

private const val NOISE_FLOOR = 0.16f

/**
 * The DaIntel board's voice orb: a 112 teal sphere (radial gradient, the sparkles mark, the default shadow) that
 * pulses, inside a brand ring that ripples out and fades; both faster while Shifa speaks (pulse 0.6 / 1.1 / 1.8 s,
 * ripple 1 / 1.4 / 2.2 s for speaking / listening / the rest). The pulse's size follows [orbIntensity], smoothed, so
 * the orb answers the doctor's voice while listening (Flutter's orb did; the board's pulse is fixed). Still with
 * reduced motion and in previews.
 */
@Composable
fun VoiceOrb(
    phase: VoicePhase,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val still = rememberReduceMotion() || LocalInspectionMode.current
    val energy by animateFloatAsState(orbIntensity(phase, level, still), tween(SMOOTH_MS))
    val (pulseMs, rippleMs) =
        when (phase) {
            VoicePhase.Speaking -> 600 to 1000
            VoicePhase.Listening -> 1100 to 1400
            else -> 1800 to 2200
        }
    val motion = rememberInfiniteTransition()
    val pulse by motion.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(pulseMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    val ripple by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(rippleMs, easing = LinearOutSlowInEasing)))
    Box(modifier.size(ShifaSize.S120), contentAlignment = Alignment.Center) {
        val ring = ShifaColors.Border.Brand
        Canvas(Modifier.fillMaxSize()) {
            // `ai-ripple`: 0.8 → 1.5 of the 120 box, 0.6 → 0 opaque; still: the ring as drawn.
            val grow = if (still) 1f else 0.8f + 0.7f * ripple
            val alpha = if (still) 1f else 0.6f * (1 - ripple)
            drawCircle(
                ring.copy(alpha = alpha),
                radius = size.minDimension / 2 * grow,
                style = Stroke(ShifaSize.S2.toPx()),
            )
        }
        // `ai-orb`: scale 1 → 1.08 at the board's energy (speaking 0.46 ≈ 0.08), more for a loud voice.
        // Read in the layer, not in composition: the pulse changes on every frame.
        Box(
            Modifier
                .size(ShifaSize.S112)
                .graphicsLayer {
                    val scale = if (still) 1f else 1f + pulse * (PULSE_BASE + PULSE_PER_ENERGY * energy)
                    scaleX = scale
                    scaleY = scale
                }.dropShadow(CircleShape, ShifaShadow.Default),
            contentAlignment = Alignment.Center,
        ) {
            // `radial-gradient(circle at 35% 30%, …)`: light teal, the brand teal at half way, the deep teal at the
            // farthest corner (0.955 of the side from that centre).
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(
                        0f to ShifaColors.Raw.OrbHighlight,
                        0.5f to ShifaColors.Interactive.Primary,
                        1f to ShifaColors.Text.BrandStrong,
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.width * 0.955f,
                    ),
                )
            }
            Icon(
                painterResource(Res.drawable.ic_sparkles_40),
                null,
                Modifier.size(ShifaDimens.VoiceOrbMark),
                tint = ShifaColors.Icon.Inverse,
            )
        }
    }
}

private const val SMOOTH_MS = 160
private const val PULSE_BASE = 0.04f
private const val PULSE_PER_ENERGY = 0.08f

/**
 * The DaIntel board's orb panel (flow 03/04 §3): a tinted card with the [VoiceOrb], the phase's icon (a spinner while
 * [phase] is thinking, the bars while speaking, the mic otherwise) and [status], the [subtitle], and Stop while
 * [onStop] is given (listening or dictating: it sends what was captured). A polite live region, so a screen reader hears
 * the phase change (not the level).
 */
@Composable
fun VoiceOrbPanel(
    phase: VoicePhase,
    level: Float,
    status: String,
    subtitle: String,
    stopLabel: String,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
) = Column(
    modifier
        .fillMaxWidth()
        .background(ShifaColors.Alpha.Teal15, ShifaRadius.ControlLg)
        .border(ShifaSize.S1, ShifaColors.Alpha.Teal25, ShifaRadius.ControlLg)
        .padding(ShifaSize.S18)
        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    VoiceOrb(phase, level)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        if (phase == VoicePhase.Thinking) {
            ShifaSpinner(ShifaColors.Text.BrandStrong)
        } else {
            Icon(
                painterResource(
                    if (phase == VoicePhase.Speaking) Res.drawable.ic_voice_wave_16 else Res.drawable.ic_mic_16,
                ),
                null,
                tint = ShifaColors.Text.BrandStrong,
            )
        }
        Text(
            status,
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.BrandStrong,
            textAlign = TextAlign.Center,
        )
    }
    Text(
        subtitle,
        style = ShifaTextStyles.metaSmall(),
        color = ShifaColors.Text.Secondary,
        textAlign = TextAlign.Center,
    )
    onStop?.let {
        ShifaSecondaryButton(stopLabel, it, icon = Res.drawable.ic_stop_filled_12, size = ShifaButtonSize.Compact)
    }
}
